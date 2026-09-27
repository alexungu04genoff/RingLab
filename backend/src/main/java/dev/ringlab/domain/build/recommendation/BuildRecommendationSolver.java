package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.build.GadgetPlate;
import dev.ringlab.domain.gamedata.*;
import java.time.Duration;
import java.util.*;
import java.util.function.LongSupplier;
import static dev.ringlab.domain.build.recommendation.RecommendationResult.Outcome.*;

/** Exact search within reviewed passive rules; see docs/auto-builder.md for the separability proof. */
public final class BuildRecommendationSolver {
  public record Budget(long maxWork, Duration time) {
    public Budget {
      if (maxWork < 1 || time == null || time.isNegative() || time.isZero())
        throw new IllegalArgumentException("Search budget must be positive");
    }
  }
  private record Candidate(BuildSelection selection, BaseStats stats) {}
  private static final Comparator<UUID> IDS = Comparator.comparing(UUID::toString);
  private static final BaseStatsBreakdown ZERO = new BaseStatsBreakdown(
      PassiveGadgetRules.ZERO, PassiveGadgetRules.ZERO, PassiveGadgetRules.ZERO);

  private final RecommendationCatalog catalog;
  private final RecommendationRequest request;
  private final Budget budget;
  private final LongSupplier clock;
  private final long started;
  private final Set<String> restrictions = new LinkedHashSet<>();
  private long work;
  private Candidate best;
  private BaseStats currentStats;

  public BuildRecommendationSolver(RecommendationCatalog catalog, RecommendationRequest request, Budget budget) {
    this(catalog, request, budget, System::nanoTime);
  }

  BuildRecommendationSolver(RecommendationCatalog catalog, RecommendationRequest request, Budget budget, LongSupplier clock) {
    this.catalog = Objects.requireNonNull(catalog);
    this.request = Objects.requireNonNull(request);
    this.budget = Objects.requireNonNull(budget);
    this.clock = clock;
    this.started = clock.getAsLong();
  }

  public RecommendationResult solve() {
    validate();
    if (!PassiveGadgetRules.VERSION.equals(catalog.version().version()))
      return result(UNAVAILABLE, "Recommendation requires the reviewed Ver. " + PassiveGadgetRules.VERSION
          + " ruleset. The selected patch has not been changed.");
    restrictions.add("Only fully known base contributions and reviewed passive combinations compete; unreviewed effects and unresolved stacking are excluded.");
    restrictions.add("Race-time, conditional and utility effects are not the objective. Current gadget costs and the complete two-row plate are used.");
    try {
      step();
      var currentType = legalType(request.current());
      if (currentType != null) {
        var evaluation = evaluate(request.current(), currentType);
        if (evaluation.coverage() == PassiveStatsResult.Coverage.CALCULATED) {
          currentStats = evaluation.adjusted();
          if (currentType == request.machineType()) best = new Candidate(request.current(), currentStats);
        }
      }

      // Check feasibility before data completeness: unknown contributions are not an empty legal catalog.
      var racers = catalog.racers().values().stream()
          .filter(r -> request.locked().racerId() == null || r.id().equals(request.locked().racerId()))
          .sorted(Comparator.comparing(Racer::id, IDS)).toList();
      var fronts = parts(MachinePartType.FRONT, request.locked().frontPartId());
      var rears = parts(MachinePartType.REAR, request.locked().rearPartId());
      var tires = request.machineType() == RacingType.BOOST ? List.<MachinePart>of()
          : parts(MachinePartType.TIRE, request.locked().tirePartId());
      if (racers.isEmpty() || fronts.isEmpty() || rears.isEmpty()
          || (request.machineType() != RacingType.BOOST && tires.isEmpty()))
        return result(NO_LEGAL_COMPLETION, "No legal catalog completion exists for the chosen machine type and locks.");
      UUID front = bestComponent(fronts.stream().map(MachinePart::id).toList(), catalog.partStats(), request.current().frontPartId());
      UUID rear = bestComponent(rears.stream().map(MachinePart::id).toList(), catalog.partStats(), request.current().rearPartId());
      UUID tire = request.machineType() == RacingType.BOOST ? null
          : bestComponent(tires.stream().map(MachinePart::id).toList(), catalog.partStats(), request.current().tirePartId());
      if (front == null || rear == null || (request.machineType() != RacingType.BOOST && tire == null))
        return result(UNAVAILABLE, "Legal machine parts exist, but required slots lack fully verified base stats for this patch.");

      // Rules depend on racer TYPE, never racer ID. Null is its own group, evaluated honestly by the calculator.
      var types = new ArrayList<RacingType>(Arrays.asList(RacingType.values()));
      types.add(null);
      for (var type : types) {
        UUID racer = bestComponent(racers.stream().filter(r -> r.racingType() == type).map(Racer::id).toList(),
            catalog.racerStats(), request.current().racerId());
        if (racer == null) continue;
        var base = new BuildSelection(racer, front, rear, tire, request.locked().gadgetIds());
        var lockedEffects = passive(type, request.locked().gadgetIds());
        if (lockedEffects.coverage() != PassiveStatsResult.Coverage.CALCULATED) {
          lockedEffects.effects().stream().filter(e -> e.status() == PassiveStatsResult.Status.UNSUPPORTED
              || e.status() == PassiveStatsResult.Status.REQUIRES_SELECTION)
              .forEach(e -> restrictions.add("Excluded racer type " + (type == null ? "unknown" : type)
                  + " with locked " + e.gadgetName() + ": " + e.explanation()));
          continue;
        }
        var optional = new ArrayList<UUID>();
        for (var gadget : catalog.gadgets().values().stream().sorted(Comparator.comparing(Gadget::id, IDS)).toList()) {
          step();
          if (request.locked().gadgetIds().contains(gadget.id()) || !GadgetPlate.canFit(Collections.singletonList(gadget.slotCost()))) continue;
          var alone = passive(type, List.of(gadget.id()));
          if (alone.coverage() != PassiveStatsResult.Coverage.CALCULATED) continue;
          // Zero-only new gadgets cannot improve the objective and lose the new-gadget/cost tie-breaks.
          // Retained utility gadgets remain candidates because fewer changes precedes slot consumption.
          if (request.current().gadgetIds().contains(gadget.id())
              || StatPriority.compare(alone.adjustments(), PassiveGadgetRules.ZERO, request.priorities()) != 0)
            optional.add(gadget.id());
        }
        search(base, type, optional, 0, new ArrayList<>(request.locked().gadgetIds()));
      }
      return best == null ? result(UNAVAILABLE, "Legal completions exist, but no fully evaluable passive-stat candidate is available with these locks.")
          : result(ESTABLISHED, explanation(true));
    } catch (SearchLimit reached) {
      return result(best == null ? LIMIT_WITHOUT_CANDIDATE : BEST_FOUND, best == null
          ? "Search limit reached before a fully evaluated candidate was found. This does not establish infeasibility."
          : explanation(false));
    }
  }

  private void search(BuildSelection base, RacingType racerType, List<UUID> optional, int start, List<UUID> selected) {
    step();
    if (!GadgetPlate.canFit(selected.stream().map(id -> catalog.gadgets().get(id).slotCost()).toList())) return;
    var effects = passive(racerType, selected);
    // For fixed types, adding gadgets cannot cure unknown effects or an unresolved applied-modifier stack.
    if (effects.coverage() != PassiveStatsResult.Coverage.CALCULATED) return;
    var selection = new BuildSelection(base.racerId(), base.frontPartId(), base.rearPartId(), base.tirePartId(), ordered(selected));
    var stats = BaseStats.sum(List.of(baseStats(selection).total(), effects.adjustments()));
    var candidate = new Candidate(selection, stats);
    if (best == null || compare(candidate, best) > 0) best = candidate;
    for (int index = start; index < optional.size(); index++) {
      selected.add(optional.get(index));
      search(base, racerType, optional, index + 1, selected);
      selected.removeLast();
    }
  }

  private List<UUID> ordered(List<UUID> selected) {
    var result = new ArrayList<UUID>();
    request.current().gadgetIds().stream().filter(selected::contains).forEach(result::add);
    selected.stream().filter(id -> !result.contains(id)).sorted(IDS).forEach(result::add);
    return result;
  }

  private UUID bestComponent(List<UUID> ids, Map<UUID, BaseStats> values, UUID current) {
    UUID winner = null;
    for (var id : ids) {
      step();
      if (!StatPriority.complete(values.get(id))) {
        restrictions.add("Some racers or parts have missing base values for the selected patch and are excluded.");
        continue;
      }
      int compared = winner == null ? 1 : StatPriority.compare(values.get(id), values.get(winner), request.priorities());
      if (compared > 0 || (compared == 0 && (id.equals(current)
          || (!winner.equals(current) && IDS.compare(id, winner) < 0)))) winner = id;
    }
    return winner;
  }

  private List<MachinePart> parts(MachinePartType slot, UUID locked) {
    return catalog.parts().values().stream().filter(part -> part.type() == slot
        && (locked == null || part.id().equals(locked)) && catalog.machines().containsKey(part.sourceMachineId())
        && catalog.machines().get(part.sourceMachineId()).racingType() == request.machineType())
        .sorted(Comparator.comparing(MachinePart::id, IDS)).toList();
  }

  private int compare(Candidate left, Candidate right) {
    int stats = StatPriority.compare(left.stats(), right.stats(), request.priorities());
    if (stats != 0) return stats;
    int changes = Integer.compare(changes(right.selection()), changes(left.selection()));
    if (changes != 0) return changes;
    int additions = Long.compare(newGadgets(right.selection()), newGadgets(left.selection()));
    if (additions != 0) return additions;
    int costs = Integer.compare(cost(right.selection()), cost(left.selection()));
    if (costs != 0) return costs;
    return stableKey(right.selection()).compareTo(stableKey(left.selection()));
  }

  private int changes(BuildSelection selection) {
    var current = request.current();
    int components = (Objects.equals(selection.racerId(), current.racerId()) ? 0 : 1)
        + (Objects.equals(selection.frontPartId(), current.frontPartId()) ? 0 : 1)
        + (Objects.equals(selection.rearPartId(), current.rearPartId()) ? 0 : 1)
        + (Objects.equals(selection.tirePartId(), current.tirePartId()) ? 0 : 1);
    return components + (int)newGadgets(selection)
        + (int)current.gadgetIds().stream().filter(id -> !selection.gadgetIds().contains(id)).count();
  }

  private long newGadgets(BuildSelection selection) {
    return selection.gadgetIds().stream().filter(id -> !request.current().gadgetIds().contains(id)).count();
  }

  private int cost(BuildSelection selection) {
    return selection.gadgetIds().stream().mapToInt(id -> catalog.gadgets().get(id).slotCost()).sum();
  }

  private static String stableKey(BuildSelection selection) {
    return selection.racerId() + "/" + selection.frontPartId() + "/" + selection.rearPartId() + "/"
        + selection.tirePartId() + "/" + selection.gadgetIds().stream().sorted(IDS).toList();
  }

  private PassiveStatsResult passive(RacingType racerType, List<UUID> ids) {
    return PassiveStatsCalculator.calculate(ZERO, catalog.version().version(), racerType,
        request.machineType(), ids.stream().map(catalog.gadgets()::get).toList(), true);
  }

  private PassiveStatsResult evaluate(BuildSelection selection, RacingType type) {
    return PassiveStatsCalculator.calculate(baseStats(selection), catalog.version().version(),
        catalog.racers().get(selection.racerId()).racingType(), type,
        selection.gadgetIds().stream().map(catalog.gadgets()::get).toList(), true);
  }

  private BaseStatsBreakdown baseStats(BuildSelection selection) {
    return BaseStatsBreakdown.calculate(selection.racerId(), selection.frontPartId(), selection.rearPartId(),
        selection.tirePartId(), catalog.racerStats(), catalog.partStats());
  }

  private RacingType legalType(BuildSelection selection) {
    if (selection.racerId() == null || selection.frontPartId() == null || selection.rearPartId() == null
        || !GadgetPlate.canFit(selection.gadgetIds().stream().map(id -> catalog.gadgets().get(id).slotCost()).toList())) return null;
    try {
      var type = MachineCompatibility.requireCompatible(id -> Optional.ofNullable(catalog.machines().get(id)),
          catalog.parts().get(selection.frontPartId()), catalog.parts().get(selection.rearPartId()),
          selection.tirePartId() == null ? null : catalog.parts().get(selection.tirePartId()));
      return type != RacingType.BOOST && selection.tirePartId() == null ? null : type;
    } catch (IllegalArgumentException invalid) { return null; }
  }

  private void validate() {
    if (!catalog.version().id().equals(request.gameVersionId())) throw new IllegalArgumentException("Patch does not match the catalog snapshot");
    validateSelection(request.current());
    validateSelection(request.locked());
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

  private static void requireKept(String slot, UUID locked, UUID current) {
    if (locked != null && !locked.equals(current)) throw new IllegalArgumentException("Locked " + slot + " must be currently selected");
  }

  private String explanation(boolean complete) {
    String prefix = complete ? "Best supported result established within the reviewed catalog and locks. " : "Best found before the search limit; optimality is not established. ";
    if (complete && best.selection().equals(request.current())) return prefix + "Your current setup is already best under this strict priority order.";
    if (currentStats == null) return prefix + "Current passive stats are unavailable, so no numerical improvement over the current draft is claimed.";
    for (var priority : request.priorities()) {
      if (priority.value(best.stats()).compareTo(priority.value(currentStats)) != 0)
        return prefix + "Compared with the current setup, the first differing priority is " + priority
            + ". Earlier priorities are equal; lower stats cannot compensate for a loss in an earlier priority.";
    }
    return prefix + "All five values equal the current setup; selection changes follow the active machine constraint and deterministic tie-breaks.";
  }

  private RecommendationResult result(RecommendationResult.Outcome outcome, String reason) {
    return new RecommendationResult(outcome, best == null ? null : best.selection(), currentStats,
        best == null ? null : best.stats(), outcome == ESTABLISHED && best != null && best.selection().equals(request.current()),
        reason, List.copyOf(restrictions), PassiveGadgetRules.RULESET, PassiveStatsCalculator.ARITHMETIC_NOTE,
        work, Math.max(0, (clock.getAsLong() - started) / 1_000_000));
  }

  private void step() {
    if (work >= budget.maxWork() || clock.getAsLong() - started >= budget.time().toNanos() || Thread.currentThread().isInterrupted())
      throw new SearchLimit();
    work++;
  }

  private static final class SearchLimit extends RuntimeException {
    SearchLimit() { super(null, null, false, false); }
  }
}
