package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.build.GadgetPlate;
import dev.ringlab.domain.collection.CollectionExclusions;
import dev.ringlab.domain.gamedata.*;
import java.math.BigDecimal;
import java.util.*;

/** Small-catalog oracle: Cartesian products and bit masks, with no production search/comparator calls. */
final class RecommendationExhaustiveOracle {
  record Winner(BuildSelection selection, BaseStats stats) {}

  static Winner solve(RecommendationCatalog catalog, RecommendationRequest request, CollectionExclusions owned) {
    BaseStats reference = request.mode() == RecommendationMode.BALANCED ? evaluate(catalog, request, request.current()) : null;
    var fronts = parts(catalog, request, owned, MachinePartType.FRONT, request.locked().frontPartId());
    var rears = parts(catalog, request, owned, MachinePartType.REAR, request.locked().rearPartId());
    var tires = request.machineType() == RacingType.BOOST ? Collections.<UUID>singletonList(null)
        : parts(catalog, request, owned, MachinePartType.TIRE, request.locked().tirePartId());
    var gadgets = catalog.gadgets().values().stream().filter(g -> owned.gadgetAvailable(g.id())).toList();
    Winner best = null;
    for (var racer : catalog.racers().values()) {
      if (!owned.racerAvailable(racer.id()) || !keeps(request.locked().racerId(), racer.id())) continue;
      for (var front : fronts) for (var rear : rears) for (var tire : tires)
        for (int mask = 0; mask < (1 << gadgets.size()); mask++) {
          var selected = new ArrayList<UUID>();
          for (int i = 0; i < gadgets.size(); i++) if ((mask & (1 << i)) != 0) selected.add(gadgets.get(i).id());
          if (request.gadgetScope() == GadgetRecommendationScope.KEEP_CURRENT
              && !new HashSet<>(selected).equals(new HashSet<>(request.current().gadgetIds()))) continue;
          if (!selected.containsAll(request.locked().gadgetIds()) || selected.size() > 6
              || !GadgetPlate.canFit(selected.stream().map(id -> catalog.gadgets().get(id).slotCost()).toList())) continue;
          var ordered = new ArrayList<UUID>();
          request.current().gadgetIds().stream().filter(selected::contains).forEach(ordered::add);
          selected.stream().filter(id -> !ordered.contains(id)).sorted(Comparator.comparing(UUID::toString)).forEach(ordered::add);
          var selection = new BuildSelection(racer.id(), front, rear, tire, ordered);
          var stats = evaluate(catalog, request, selection);
          if (stats == null || !feasible(request, reference, stats)) continue;
          var candidate = new Winner(selection, stats);
          if (best == null || compare(catalog, request, reference, candidate, best) > 0) best = candidate;
        }
    }
    return best;
  }

  private static boolean keeps(UUID locked, UUID selected) { return locked == null || locked.equals(selected); }

  private static List<UUID> parts(RecommendationCatalog catalog, RecommendationRequest request,
      CollectionExclusions owned, MachinePartType slot, UUID locked) {
    return catalog.parts().values().stream().filter(p -> p.type() == slot && keeps(locked, p.id())
        && owned.machineAvailable(p.sourceMachineId())
        && catalog.machines().get(p.sourceMachineId()).racingType() == request.machineType()).map(MachinePart::id).toList();
  }

  private static BaseStats evaluate(RecommendationCatalog catalog, RecommendationRequest request, BuildSelection selection) {
    var base = BaseStatsBreakdown.calculate(selection.racerId(), selection.frontPartId(), selection.rearPartId(),
        selection.tirePartId(), catalog.racerStats(), catalog.partStats());
    var passive = PassiveStatsCalculator.calculate(catalog.rules(), base, catalog.version().version(),
        catalog.racers().get(selection.racerId()).racingType(), request.machineType(),
        selection.gadgetIds().stream().map(catalog.gadgets()::get).toList(), true);
    if (passive.coverage() != PassiveStatsResult.Coverage.CALCULATED) return null;
    return passive.adjusted();
  }

  private static boolean feasible(RecommendationRequest request, BaseStats reference, BaseStats stats) {
    if (request.mode() == RecommendationMode.STRICT) return true;
    for (var stat : request.priorities()) {
      var floor = stat.value(reference).multiply(new BigDecimal("100").subtract(request.balanced().maximumLossPercent().get(stat)));
      if (stat.value(stats).multiply(new BigDecimal("100")).compareTo(floor) < 0) return false;
    }
    return true;
  }

  private static int compare(RecommendationCatalog catalog, RecommendationRequest request, BaseStats reference, Winner a, Winner b) {
    int compared;
    if (request.mode() == RecommendationMode.BALANCED) {
      compared = score(request, reference, a.stats()).compareTo(score(request, reference, b.stats()));
      if (compared != 0) return compared;
    }
    for (var stat : request.priorities()) {
      compared = stat.value(a.stats()).compareTo(stat.value(b.stats()));
      if (compared != 0) return compared;
    }
    if (request.mode() == RecommendationMode.BALANCED) {
      compared = secondary(request, a.stats()).compareTo(secondary(request, b.stats()));
      if (compared != 0) return compared;
    }
    compared = Integer.compare(changes(request.current(), b.selection()), changes(request.current(), a.selection()));
    if (compared != 0) return compared;
    compared = Long.compare(additions(request.current(), b.selection()), additions(request.current(), a.selection()));
    if (compared != 0) return compared;
    compared = Integer.compare(cost(catalog, b.selection()), cost(catalog, a.selection()));
    return compared != 0 ? compared : key(b.selection()).compareTo(key(a.selection()));
  }

  private static BigDecimal score(RecommendationRequest request, BaseStats reference, BaseStats stats) {
    // Exact common denominator; no rounding and no production BalancedObjective dependency.
    var denominators = request.priorities().stream().map(s -> s.value(reference).signum() == 0 ? BigDecimal.ONE : s.value(reference)).toList();
    var common = denominators.stream().reduce(BigDecimal.ONE, BigDecimal::multiply);
    var score = BigDecimal.ZERO;
    for (int i = 0; i < denominators.size(); i++) score = score.add(
        request.priorities().get(i).value(stats).multiply(BigDecimal.valueOf(denominators.size() - i)).multiply(common.divide(denominators.get(i))));
    return score;
  }
  private static BigDecimal secondary(RecommendationRequest request, BaseStats stats) {
    return request.balanced().secondary().stream().map(s -> s.value(stats)).reduce(BigDecimal.ZERO, BigDecimal::add);
  }
  private static long additions(BuildSelection current, BuildSelection selection) {
    return selection.gadgetIds().stream().filter(id -> !current.gadgetIds().contains(id)).count();
  }
  private static int changes(BuildSelection current, BuildSelection selection) {
    int result = 0;
    var before = Arrays.asList(current.racerId(), current.frontPartId(), current.rearPartId(), current.tirePartId());
    var after = Arrays.asList(selection.racerId(), selection.frontPartId(), selection.rearPartId(), selection.tirePartId());
    for (int i = 0; i < before.size(); i++) if (!Objects.equals(before.get(i), after.get(i))) result++;
    return result + (int)additions(current, selection) + (int)current.gadgetIds().stream().filter(id -> !selection.gadgetIds().contains(id)).count();
  }
  private static int cost(RecommendationCatalog catalog, BuildSelection selection) {
    return selection.gadgetIds().stream().mapToInt(id -> catalog.gadgets().get(id).slotCost()).sum();
  }
  private static String key(BuildSelection selection) {
    return selection.racerId() + "/" + selection.frontPartId() + "/" + selection.rearPartId() + "/" + selection.tirePartId()
        + "/" + selection.gadgetIds().stream().sorted(Comparator.comparing(UUID::toString)).toList();
  }
}
