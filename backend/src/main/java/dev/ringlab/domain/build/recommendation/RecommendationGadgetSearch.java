package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.build.GadgetPlate;
import dev.ringlab.domain.collection.CollectionExclusions;
import dev.ringlab.domain.gamedata.*;
import java.util.*;
import java.util.function.BiConsumer;
import static dev.ringlab.domain.build.recommendation.RecommendationCandidateOrder.IDS;

/** Shared ordered gadget-subset traversal; each mode supplies its component evaluation. */
final class RecommendationGadgetSearch {
  private final RecommendationCatalog catalog;
  private final RecommendationRequest request;
  private final CollectionExclusions availability;
  private final RecommendationCandidates candidates;
  private final RecommendationSearchBudget budget;

  RecommendationGadgetSearch(RecommendationCatalog catalog, RecommendationRequest request,
      CollectionExclusions availability, RecommendationCandidates candidates, RecommendationSearchBudget budget) {
    this.catalog = catalog;
    this.request = request;
    this.availability = availability;
    this.candidates = candidates;
    this.budget = budget;
  }

  void search(RacingType racerType, BiConsumer<List<UUID>, BaseStats> visit) {
    search(racerType, optionalGadgets(racerType), 0, new ArrayList<>(request.locked().gadgetIds()), visit);
  }

  private List<UUID> optionalGadgets(RacingType type) {
    var optional = new ArrayList<UUID>();
    for (var gadget : catalog.gadgets().values().stream().sorted(Comparator.comparing(Gadget::id, IDS)).toList()) {
      if (!availability.gadgetAvailable(gadget.id())) continue;
      budget.step();
      if (request.locked().gadgetIds().contains(gadget.id()) || !GadgetPlate.canFit(Collections.singletonList(gadget.slotCost()))) continue;
      var alone = candidates.effects(type, List.of(gadget.id()));
      if (!alone.eligible()) continue;
      // Include secondary gains too. Only an all-five-zero new gadget can be omitted.
      if (request.current().gadgetIds().contains(gadget.id())
          || StatPriority.compare(alone.adjustments(), PassiveGadgetRules.ZERO, List.of(StatPriority.values())) != 0)
        optional.add(gadget.id());
    }
    return optional;
  }

  private void search(RacingType racerType, List<UUID> optional, int start, List<UUID> selected,
      BiConsumer<List<UUID>, BaseStats> visit) {
    budget.step();
    if (!GadgetPlate.canFit(selected.stream().map(id -> catalog.gadgets().get(id).slotCost()).toList())) return;
    var effects = candidates.effects(racerType, selected);
    // For fixed types, adding gadgets cannot cure unknown effects or an unresolved applied-modifier stack.
    if (!effects.eligible()) return;
    visit.accept(ordered(selected), effects.adjustments());
    for (int index = start; index < optional.size(); index++) {
      selected.add(optional.get(index));
      search(racerType, optional, index + 1, selected, visit);
      selected.removeLast();
    }
  }

  private List<UUID> ordered(List<UUID> selected) {
    var result = new ArrayList<UUID>();
    request.current().gadgetIds().stream().filter(selected::contains).forEach(result::add);
    selected.stream().filter(id -> !result.contains(id)).sorted(IDS).forEach(result::add);
    return result;
  }
}
