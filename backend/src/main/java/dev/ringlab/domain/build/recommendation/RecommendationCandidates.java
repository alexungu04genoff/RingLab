package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.build.GadgetPlate;
import dev.ringlab.domain.collection.CollectionExclusions;
import dev.ringlab.domain.gamedata.*;
import java.util.*;
import static dev.ringlab.domain.build.recommendation.RecommendationCandidateOrder.IDS;

/** Validates selections and prepares legal catalog choices and passive evaluations. */
final class RecommendationCandidates {
  private static final BaseStatsBreakdown ZERO = new BaseStatsBreakdown(
      PassiveGadgetRules.ZERO, PassiveGadgetRules.ZERO, PassiveGadgetRules.ZERO);
  private final RecommendationCatalog catalog;
  private final RecommendationRequest request;
  private final CollectionExclusions availability;

  RecommendationCandidates(RecommendationCatalog catalog, RecommendationRequest request, CollectionExclusions availability) {
    this.catalog = catalog;
    this.request = request;
    this.availability = availability;
  }

  List<Racer> racers() {
    return catalog.racers().values().stream()
        .filter(r -> availability.racerAvailable(r.id()))
        .filter(r -> request.locked().racerId() == null || r.id().equals(request.locked().racerId()))
        .sorted(Comparator.comparing(Racer::id, IDS)).toList();
  }

  List<MachinePart> parts(MachinePartType slot, UUID locked) {
    return catalog.parts().values().stream().filter(part -> part.type() == slot
        && availability.machineAvailable(part.sourceMachineId())
        && (locked == null || part.id().equals(locked)) && catalog.machines().containsKey(part.sourceMachineId())
        && catalog.machines().get(part.sourceMachineId()).racingType() == request.machineType())
        .sorted(Comparator.comparing(MachinePart::id, IDS)).toList();
  }

  PassiveStatsResult passive(RacingType racerType, List<UUID> ids) {
    return PassiveStatsCalculator.calculate(catalog.rules(), ZERO, catalog.version().version(), racerType,
        request.machineType(), ids.stream().map(catalog.gadgets()::get).toList(), true);
  }

  PassiveStatsResult evaluate(BuildSelection selection, RacingType type) {
    return PassiveStatsCalculator.calculate(catalog.rules(), baseStats(selection), catalog.version().version(),
        catalog.racers().get(selection.racerId()).racingType(), type,
        selection.gadgetIds().stream().map(catalog.gadgets()::get).toList(), true);
  }

  BaseStatsBreakdown baseStats(BuildSelection selection) {
    return BaseStatsBreakdown.calculate(selection.racerId(), selection.frontPartId(), selection.rearPartId(),
        selection.tirePartId(), catalog.racerStats(), catalog.partStats());
  }

  RacingType legalType(BuildSelection selection) {
    if (selection.racerId() == null || selection.frontPartId() == null || selection.rearPartId() == null
        || !GadgetPlate.canFit(selection.gadgetIds().stream().map(id -> catalog.gadgets().get(id).slotCost()).toList())) return null;
    try {
      var type = MachineCompatibility.requireCompatible(id -> Optional.ofNullable(catalog.machines().get(id)),
          catalog.parts().get(selection.frontPartId()), catalog.parts().get(selection.rearPartId()),
          selection.tirePartId() == null ? null : catalog.parts().get(selection.tirePartId()));
      return type != RacingType.BOOST && selection.tirePartId() == null ? null : type;
    } catch (IllegalArgumentException invalid) { return null; }
  }

  void validate() {
    if (!catalog.version().id().equals(request.gameVersionId())) throw new IllegalArgumentException("Patch does not match the catalog snapshot");
    validateSelection(request.current());
    validateSelection(request.locked());
    var unavailableLocks = unavailableNames(request.locked());
    if (!unavailableLocks.isEmpty()) throw new IllegalArgumentException("Not owned — locked: " + String.join(", ", unavailableLocks));
    var locks = request.locked();
    var current = request.current();
    requireKept("Racer", locks.racerId(), current.racerId());
    requireKept("Front", locks.frontPartId(), current.frontPartId());
    requireKept("Rear", locks.rearPartId(), current.rearPartId());
    requireKept("Tire", locks.tirePartId(), current.tirePartId());
    if (!current.gadgetIds().containsAll(locks.gadgetIds())) throw new IllegalArgumentException("Locked gadgets must be currently selected");
    for (var id : Arrays.asList(locks.frontPartId(), locks.rearPartId(), locks.tirePartId())) {
      if (id == null) continue;
      var part = catalog.parts().get(id);
      if (part.type() == MachinePartType.TIRE && request.machineType() == RacingType.BOOST)
        throw new IllegalArgumentException("Unlock the Tire before choosing Boost: Boost has no tire part");
      var source = catalog.machines().get(part.sourceMachineId());
      if (source == null || source.racingType() != request.machineType())
        throw new IllegalArgumentException("Locked " + part.type() + " is incompatible with " + request.machineType() + "; unlock it or choose its source machine type");
    }
    if (!GadgetPlate.canFit(locks.gadgetIds().stream().map(id -> catalog.gadgets().get(id).slotCost()).toList()))
      throw new IllegalArgumentException("Locked gadgets have unknown/invalid costs or do not fit the two-row Gadget Plate");
  }

  private void validateSelection(BuildSelection selection) {
    if (selection.racerId() != null && !catalog.racers().containsKey(selection.racerId())) throw new IllegalArgumentException("Unknown racer ID");
    var ids = Arrays.asList(selection.frontPartId(), selection.rearPartId(), selection.tirePartId());
    var slots = List.of(MachinePartType.FRONT, MachinePartType.REAR, MachinePartType.TIRE);
    for (int i = 0; i < ids.size(); i++) {
      if (ids.get(i) == null) continue;
      var part = catalog.parts().get(ids.get(i));
      if (part == null) throw new IllegalArgumentException("Unknown " + slots.get(i) + " part ID");
      if (part.type() != slots.get(i)) throw new IllegalArgumentException("Part ID does not match the " + slots.get(i) + " slot");
    }
    if (selection.gadgetIds().size() > 6 || new HashSet<>(selection.gadgetIds()).size() != selection.gadgetIds().size())
      throw new IllegalArgumentException("Select distinct gadget IDs (at most six)");
    if (!catalog.gadgets().keySet().containsAll(selection.gadgetIds())) throw new IllegalArgumentException("Unknown gadget ID");
  }

  boolean available(BuildSelection selection) { return unavailableNames(selection).isEmpty(); }

  private Set<String> unavailableNames(BuildSelection selection) {
    var names = new LinkedHashSet<String>();
    if (selection.racerId() != null && !availability.racerAvailable(selection.racerId()))
      names.add("racer " + catalog.racers().get(selection.racerId()).name());
    for (var id : Arrays.asList(selection.frontPartId(), selection.rearPartId(), selection.tirePartId())) {
      if (id == null) continue;
      var source = catalog.parts().get(id).sourceMachineId();
      if (!availability.machineAvailable(source)) names.add("source machine " + catalog.machines().get(source).name());
    }
    for (var id : selection.gadgetIds()) if (!availability.gadgetAvailable(id))
      names.add("gadget " + catalog.gadgets().get(id).name());
    return names;
  }

  private static void requireKept(String slot, UUID locked, UUID current) {
    if (locked != null && !locked.equals(current)) throw new IllegalArgumentException("Locked " + slot + " must be currently selected");
  }

}
