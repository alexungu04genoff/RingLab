package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.gamedata.*;
import java.util.List;
import java.util.UUID;

/** Evaluates reviewed passive stats only; never loads facts or searches. */
final class RecommendationStatsEvaluator {
  private static final BaseStatsBreakdown ZERO = new BaseStatsBreakdown(BaseStats.ZERO, BaseStats.ZERO, BaseStats.ZERO);
  private final RecommendationCatalog catalog;
  private final RecommendationRequest request;

  RecommendationStatsEvaluator(RecommendationCatalog catalog, RecommendationRequest request) {
    this.catalog = catalog;
    this.request = request;
  }

  record Evaluation(PassiveStatsResult passive) {
    boolean eligible() {
      return passive.coverage() == PassiveStatsResult.Coverage.CALCULATED
          && StatPriority.complete(total());
    }
    BaseStats total() { return passive.adjusted(); }
    BaseStats adjustments() { return passive.adjustments(); }
  }

  Evaluation selection(BuildSelection selection, RacingType machineType) {
    var base = BaseStatsBreakdown.calculate(selection.racerId(), selection.frontPartId(), selection.rearPartId(),
        selection.tirePartId(), catalog.racerStats(), catalog.partStats());
    return evaluate(base, catalog.racers().get(selection.racerId()).racingType(), machineType, selection.gadgetIds());
  }

  Evaluation gadgets(RacingType racerType, List<UUID> gadgets) {
    return evaluate(ZERO, racerType, request.machineType(), gadgets);
  }

  private Evaluation evaluate(BaseStatsBreakdown base, RacingType racerType, RacingType machineType, List<UUID> gadgets) {
    var passive = PassiveStatsCalculator.calculate(catalog.rules(), base, catalog.version().version(), racerType,
        machineType, gadgets.stream().map(catalog.gadgets()::get).toList(), true);
    return new Evaluation(passive);
  }
}
