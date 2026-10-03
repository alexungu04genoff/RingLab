package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.gamedata.*;
import java.util.*;
import static dev.ringlab.domain.build.recommendation.RecommendationCandidateOrder.IDS;
import dev.ringlab.domain.build.recommendation.RecommendationCandidateOrder.Candidate;

/** Strict separability reduction: best part per slot and best racer per type, then gadget subsets. */
final class StrictRecommendationSearch {
  private final RecommendationCatalog catalog;
  private final RecommendationRequest request;
  private final RecommendationCandidates candidates;
  private final RecommendationGadgetSearch gadgets;
  private final RecommendationCandidateOrder order;
  private final RecommendationSearchBudget budget;
  private final Set<String> restrictions;

  StrictRecommendationSearch(RecommendationCatalog catalog, RecommendationRequest request,
      RecommendationCandidates candidates, RecommendationGadgetSearch gadgets, RecommendationCandidateOrder order,
      RecommendationSearchBudget budget, Set<String> restrictions) {
    this.catalog = catalog;
    this.request = request;
    this.candidates = candidates;
    this.gadgets = gadgets;
    this.order = order;
    this.budget = budget;
    this.restrictions = restrictions;
  }

  /** False means legal parts exist but a required slot has no complete base contribution. */
  boolean search(List<Racer> racers, List<MachinePart> fronts, List<MachinePart> rears, List<MachinePart> tires) {
    UUID front = bestComponent(fronts.stream().map(MachinePart::id).toList(), catalog.partStats(), request.current().frontPartId());
    UUID rear = bestComponent(rears.stream().map(MachinePart::id).toList(), catalog.partStats(), request.current().rearPartId());
    UUID tire = request.machineType() == RacingType.BOOST ? null
        : bestComponent(tires.stream().map(MachinePart::id).toList(), catalog.partStats(), request.current().tirePartId());
    if (front == null || rear == null || (request.machineType() != RacingType.BOOST && tire == null))
      return false;

    // Rules depend on racer TYPE, never racer ID. Null is its own group, evaluated honestly by the calculator.
    var types = new ArrayList<RacingType>(Arrays.asList(RacingType.values()));
    types.add(null);
    for (var type : types) {
      UUID racer = bestComponent(racers.stream().filter(r -> r.racingType() == type).map(Racer::id).toList(),
          catalog.racerStats(), request.current().racerId());
      if (racer == null) continue;
      var base = new BuildSelection(racer, front, rear, tire, request.locked().gadgetIds());
      var lockedEffects = candidates.effects(type, request.locked().gadgetIds());
      if (!lockedEffects.eligible()) {
        lockedEffects.passive().effects().stream().filter(e -> e.status() == PassiveStatsResult.Status.UNSUPPORTED
            || e.status() == PassiveStatsResult.Status.REQUIRES_SELECTION)
            .forEach(e -> restrictions.add("Excluded racer type " + (type == null ? "unknown" : type)
                + " with locked " + e.gadgetName() + ": " + e.explanation()));
        continue;
      }
      gadgets.search(type, (selected, adjustments) -> {
        var selection = new BuildSelection(base.racerId(), base.frontPartId(), base.rearPartId(), base.tirePartId(), selected);
        order.consider(new Candidate(selection, BaseStats.sum(List.of(candidates.baseStats(selection).total(), adjustments))));
      });
    }
    return true;
  }

  private UUID bestComponent(List<UUID> ids, Map<UUID, BaseStats> values, UUID current) {
    UUID winner = null;
    for (var id : ids) {
      budget.step();
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
}
