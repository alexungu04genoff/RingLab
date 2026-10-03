package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.gamedata.*;
import java.util.*;
import java.math.BigDecimal;
import static dev.ringlab.domain.build.recommendation.RecommendationCandidateOrder.IDS;
import dev.ringlab.domain.build.recommendation.RecommendationCandidateOrder.Candidate;

/** Streaming passes over survivors. No candidate population is materialized. */
final class BalancedRecommendationSearch {
  private record Component(UUID id, BaseStats stats) {}
  private record Group(RacingType type, List<List<Component>> slots, List<BaseStats> minimum,
      List<BaseStats> maximum, long[] counts) {}
  private static final class Pass {
    final StatPriority target; // null is the final active-lexicographic selection pass
    long count;
    Candidate maximum;
    Pass(StatPriority target) { this.target = target; }
  }
  private final RecommendationCatalog catalog;
  private final RecommendationRequest request;
  private final RecommendationGadgetSearch gadgets;
  private final RecommendationCandidateOrder order;
  private final RecommendationSearchBudget budget;
  private final Set<String> restrictions;
  private final Map<StatPriority,BigDecimal> floors = new EnumMap<>(StatPriority.class);
  private final List<BalancedStage> stages = new ArrayList<>();

  BalancedRecommendationSearch(RecommendationCatalog catalog, RecommendationRequest request,
      RecommendationGadgetSearch gadgets, RecommendationCandidateOrder order,
      RecommendationSearchBudget budget, Set<String> restrictions) {
    this.catalog = catalog; this.request = request; this.gadgets = gadgets;
    this.order = order; this.budget = budget; this.restrictions = restrictions;
  }

  List<BalancedStage> stages() { return List.copyOf(stages); }

  boolean search(List<Racer> racers, List<MachinePart> fronts, List<MachinePart> rears, List<MachinePart> tires) {
    var groups = groups(racers,fronts,rears,tires);
    BalancedStage pending = null;
    var active = request.activePriorities();
    for (int index = 0; index <= active.size(); index++) {
      var pass = scan(groups,index == active.size() ? null : active.get(index));
      if (pass.count == 0) { order.clear(); return false; }
      if (pending != null) stages.add(new BalancedStage(pending.stat(),pending.lossPercent(),pending.best(),
          pending.threshold(),pending.candidatesBefore(),pass.count));
      if (pass.target == null) return true;
      var best = pass.target.value(pass.maximum.stats());
      var loss = request.balanced().maximumLossPercent().get(pass.target);
      var floor = BalancedStage.threshold(best,loss);
      floors.put(pass.target,floor);
      pending = new BalancedStage(pass.target,loss,best,floor,pass.count,0);
      // The maximum witness always survives its own threshold, even for negative/zero best.
      // Keep a usable legal incumbent if a later pass hits its budget before visiting a leaf.
      if (order.best() == null || !survives(order.best().stats())) {
        order.clear();
        order.consider(pass.maximum);
      }
    }
    throw new IllegalStateException("Missing final Balanced pass");
  }

  private Pass scan(List<Group> groups, StatPriority target) {
    var pass = new Pass(target);
    var priorities = target == null ? request.activePriorities() : List.of(target);
    var current = Arrays.asList(request.current().racerId(),request.current().frontPartId(),
        request.current().rearPartId(),request.current().tirePartId());
    for (var group : groups) {
      var best = new ArrayList<Component>();
      for (int slot = 0; slot < 4; slot++) {
        Component chosen = null;
        for (var candidate : group.slots().get(slot)) {
          budget.step();
          if (chosen == null || compare(candidate,chosen,priorities,current.get(slot)) > 0) chosen = candidate;
        }
        best.add(chosen);
      }
      gadgets.search(group.type(), (selected, adjustments) ->
          visit(group,best,pass,0,adjustments,new UUID[4],selected));
    }
    return pass;
  }

  private void visit(Group group, List<Component> best, Pass pass, int slot, BaseStats partial, UUID[] ids, List<UUID> selected) {
    budget.step();
    var upper = BaseStats.sum(List.of(partial,group.maximum().get(slot)));
    // Every completion is <= this coordinate-wise upper bound. Failed proven floors safely prune it.
    if (!survives(upper)) return;
    var lower = BaseStats.sum(List.of(partial,group.minimum().get(slot)));
    if (survives(lower)) {
      // Every completion survives. Fixed type/gadgets give a constant adjustment, so independent
      // component maxima achieve the stage maximum (or final active lexicographic maximum).
      // Among equal component vectors, retaining current IDs and then stable IDs minimizes the
      // additive change count and stable key. Gadget additions/cost are fixed in this subtree.
      // Thus we may count the Cartesian product without visiting/storing each complete build.
      var total = partial;
      for (int remaining = slot; remaining < 4; remaining++) {
        var choice = best.get(remaining); ids[remaining] = choice.id();
        total = BaseStats.sum(List.of(total,choice.stats()));
      }
      pass.count = Math.addExact(pass.count,group.counts()[slot]);
      var candidate = new Candidate(new BuildSelection(ids[0],ids[1],ids[2],ids[3],selected),total);
      if (pass.target != null && (pass.maximum == null
          || pass.target.value(total).compareTo(pass.target.value(pass.maximum.stats())) > 0)) pass.maximum = candidate;
      order.consider(candidate);
      return;
    }
    for (var choice : group.slots().get(slot)) {
      ids[slot] = choice.id();
      visit(group,best,pass,slot+1,BaseStats.sum(List.of(partial,choice.stats())),ids,selected);
    }
  }

  private boolean survives(BaseStats stats) {
    return floors.entrySet().stream().allMatch(e -> e.getKey().value(stats).compareTo(e.getValue()) >= 0);
  }

  private static int compare(Component a, Component b, List<StatPriority> priorities, UUID current) {
    int stats = StatPriority.compare(a.stats(),b.stats(),priorities);
    if (stats != 0) return stats;
    int kept = Boolean.compare(Objects.equals(a.id(),current),Objects.equals(b.id(),current));
    if (kept != 0) return kept;
    return a.id() == null ? 0 : IDS.compare(b.id(),a.id());
  }

  private List<Group> groups(List<Racer> racers,List<MachinePart> fronts,List<MachinePart> rears,List<MachinePart> tires) {
    var front = components(fronts.stream().map(MachinePart::id).toList(),catalog.partStats());
    var rear = components(rears.stream().map(MachinePart::id).toList(),catalog.partStats());
    var tire = request.machineType() == RacingType.BOOST ? List.of(new Component(null,BaseStats.ZERO))
        : components(tires.stream().map(MachinePart::id).toList(),catalog.partStats());
    var groups = new ArrayList<Group>();
    var types = new ArrayList<RacingType>(Arrays.asList(RacingType.values())); types.add(null);
    for (var type : types) {
      var racer = components(racers.stream().filter(r -> r.racingType() == type).map(Racer::id).toList(),catalog.racerStats());
      var slots = List.of(racer,front,rear,tire);
      if (slots.stream().anyMatch(List::isEmpty)) continue;
      var minimum = new ArrayList<>(Collections.nCopies(5,BaseStats.ZERO));
      var maximum = new ArrayList<>(Collections.nCopies(5,BaseStats.ZERO));
      var counts = new long[5]; counts[4] = 1;
      for (int slot = 3; slot >= 0; slot--) {
        minimum.set(slot,BaseStats.sum(List.of(minimum.get(slot+1),bound(slots.get(slot),false))));
        maximum.set(slot,BaseStats.sum(List.of(maximum.get(slot+1),bound(slots.get(slot),true))));
        counts[slot] = Math.multiplyExact(counts[slot+1],slots.get(slot).size());
      }
      groups.add(new Group(type,slots,minimum,maximum,counts));
    }
    return groups;
  }
  private List<Component> components(List<UUID> ids,Map<UUID,BaseStats> stats) {
    var values = new ArrayList<Component>();
    for (var id : ids) {
      budget.step();
      if (StatPriority.complete(stats.get(id))) values.add(new Component(id,stats.get(id)));
      else restrictions.add("Some racers or parts have missing base values for the selected patch and are excluded.");
    }
    return values;
  }
  private static BaseStats bound(List<Component> values,boolean maximum) {
    var result = new EnumMap<StatPriority,BigDecimal>(StatPriority.class);
    for (var stat : StatPriority.values()) {
      var stream = values.stream().map(c -> stat.value(c.stats()));
      result.put(stat,(maximum ? stream.max(BigDecimal::compareTo) : stream.min(BigDecimal::compareTo)).orElseThrow());
    }
    return new BaseStats(result.get(StatPriority.SPEED),result.get(StatPriority.ACCELERATION),
        result.get(StatPriority.HANDLING),result.get(StatPriority.POWER),result.get(StatPriority.BOOST));
  }
}
