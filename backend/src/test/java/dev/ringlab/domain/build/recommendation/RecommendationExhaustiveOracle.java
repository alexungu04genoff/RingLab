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
    var fronts = parts(catalog, request, owned, MachinePartType.FRONT, request.locked().frontPartId());
    var rears = parts(catalog, request, owned, MachinePartType.REAR, request.locked().rearPartId());
    var tires = request.machineType() == RacingType.BOOST ? Collections.<UUID>singletonList(null)
        : parts(catalog, request, owned, MachinePartType.TIRE, request.locked().tirePartId());
    var gadgets = catalog.gadgets().values().stream().filter(g -> owned.gadgetAvailable(g.id())).toList();
    var population = new ArrayList<Winner>();
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
          if (stats != null) population.add(new Winner(selection, stats));
        }
    }
    // Intentionally materialize and filter directly: no production traversal, bounds or stages.
    if (request.mode() == RecommendationMode.BALANCED) {
      for (var stat : request.priorities()) {
        if (request.balanced().ignored().contains(stat) || population.isEmpty()) continue;
        var best = population.stream().map(c -> stat.value(c.stats())).max(BigDecimal::compareTo).orElseThrow();
        var allowance = best.abs().multiply(request.balanced().maximumLossPercent().get(stat)).divide(new BigDecimal("100"));
        var floor = best.subtract(allowance);
        population.removeIf(c -> stat.value(c.stats()).compareTo(floor) < 0);
      }
    }
    return population.stream().max((a,b) -> compare(catalog,request,a,b)).orElse(null);
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

  private static int compare(RecommendationCatalog catalog, RecommendationRequest request, Winner a, Winner b) {
    int compared;
    for (var stat : request.priorities()) {
      if (request.mode() == RecommendationMode.BALANCED && request.balanced().ignored().contains(stat)) continue;
      compared = stat.value(a.stats()).compareTo(stat.value(b.stats()));
      if (compared != 0) return compared;
    }
    compared = Integer.compare(changes(request.current(), b.selection()), changes(request.current(), a.selection()));
    if (compared != 0) return compared;
    compared = Long.compare(additions(request.current(), b.selection()), additions(request.current(), a.selection()));
    if (compared != 0) return compared;
    compared = Integer.compare(cost(catalog, b.selection()), cost(catalog, a.selection()));
    return compared != 0 ? compared : key(b.selection()).compareTo(key(a.selection()));
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
