package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.gamedata.*;
import java.util.*;
import java.math.BigDecimal;
import static dev.ringlab.domain.build.recommendation.RecommendationCandidateOrder.IDS;
import dev.ringlab.domain.build.recommendation.RecommendationCandidateOrder.Candidate;

/** Balanced branch-and-bound over every complete component choice, using the frozen objective. */
final class BalancedRecommendationSearch {
  private record Component(UUID id, BaseStats stats) {}
  private record Components(List<List<Component>> slots, List<BaseStats> remainingMaximum) {}
  private final RecommendationCatalog catalog;
  private final RecommendationRequest request;
  private final RecommendationGadgetSearch gadgets;
  private final RecommendationCandidateOrder order;
  private final RecommendationSearchBudget budget;
  private final Set<String> restrictions;
  private final BalancedObjective balanced;

  BalancedRecommendationSearch(RecommendationCatalog catalog, RecommendationRequest request,
      RecommendationGadgetSearch gadgets, RecommendationCandidateOrder order,
      RecommendationSearchBudget budget, Set<String> restrictions, BalancedObjective balanced) {
    this.catalog = catalog;
    this.request = request;
    this.gadgets = gadgets;
    this.order = order;
    this.budget = budget;
    this.restrictions = restrictions;
    this.balanced = balanced;
  }

  void search(List<Racer> racers, List<MachinePart> fronts, List<MachinePart> rears, List<MachinePart> tires) {
    var frontChoices = components(fronts.stream().map(MachinePart::id).toList(), catalog.partStats(), request.current().frontPartId());
    var rearChoices = components(rears.stream().map(MachinePart::id).toList(), catalog.partStats(), request.current().rearPartId());
    var tireChoices = request.machineType() == RacingType.BOOST ? List.of(new Component(null, BaseStats.ZERO))
        : components(tires.stream().map(MachinePart::id).toList(), catalog.partStats(), request.current().tirePartId());
    var types = new ArrayList<RacingType>(Arrays.asList(RacingType.values()));
    types.add(null);
    for (var type : types) {
      var racerChoices = components(racers.stream().filter(r -> r.racingType() == type).map(Racer::id).toList(),
          catalog.racerStats(), request.current().racerId());
      var slots = List.of(racerChoices, frontChoices, rearChoices, tireChoices);
      if (slots.stream().anyMatch(List::isEmpty)) continue;
      var maximum = new ArrayList<BaseStats>(Collections.nCopies(5, BaseStats.ZERO));
      for (int index = 3; index >= 0; index--)
        maximum.set(index, BaseStats.sum(List.of(maximum.get(index + 1), maximum(slots.get(index)))));
      var components = new Components(slots, maximum);
      gadgets.search(type, (selected, adjustments) -> searchComponents(components, 0, adjustments, new UUID[4], selected));
    }
  }

  private List<Component> components(List<UUID> ids, Map<UUID, BaseStats> values, UUID current) {
    var choices = new ArrayList<Component>();
    for (var id : ids) {
      budget.step();
      if (StatPriority.complete(values.get(id))) choices.add(new Component(id, values.get(id)));
      else restrictions.add("Some racers or parts have missing base values for the selected patch and are excluded.");
    }
    // Search promising choices first, without discarding a trade-off in any component.
    choices.sort((left, right) -> {
      int compared = balanced.compare(right.stats(), left.stats());
      if (compared != 0) return compared;
      int kept = Boolean.compare(Objects.equals(right.id(), current), Objects.equals(left.id(), current));
      return kept != 0 ? kept : IDS.compare(left.id(), right.id());
    });
    return choices;
  }

  private static BaseStats maximum(List<Component> choices) {
    var values = new EnumMap<StatPriority, BigDecimal>(StatPriority.class);
    for (var stat : StatPriority.values())
      values.put(stat, choices.stream().map(c -> stat.value(c.stats())).max(BigDecimal::compareTo).orElseThrow());
    return new BaseStats(values.get(StatPriority.SPEED), values.get(StatPriority.ACCELERATION),
        values.get(StatPriority.HANDLING), values.get(StatPriority.POWER), values.get(StatPriority.BOOST));
  }

  private void searchComponents(Components components, int slot, BaseStats partial, UUID[] ids, List<UUID> gadgets) {
    budget.step();
    var upper = BaseStats.sum(List.of(partial, components.remainingMaximum().get(slot)));
    // Each stat's independent maximum is an optimistic complete-loadout bound. Positive
    // coefficients make Q monotone. Strict inequality preserves ALL later tie-breaks.
    if (!balanced.feasible(upper) || (order.best() != null && balanced.compareActive(upper, order.best().stats()) < 0)) return;
    if (slot == 4) {
      order.consider(new Candidate(new BuildSelection(ids[0], ids[1], ids[2], ids[3], gadgets), partial));
      return;
    }
    for (var choice : components.slots().get(slot)) {
      ids[slot] = choice.id();
      searchComponents(components, slot + 1, BaseStats.sum(List.of(partial, choice.stats())), ids, gadgets);
    }
  }
}
