package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.gamedata.*;
import java.util.List;
import java.util.UUID;

/** Composes reviewed calculators for the requested objective; never loads facts or searches. */
final class RecommendationStatsEvaluator {
  private static final BaseStatsBreakdown ZERO = new BaseStatsBreakdown(BaseStats.ZERO, BaseStats.ZERO, BaseStats.ZERO);
  private final RecommendationCatalog catalog;
  private final RecommendationRequest request;

  RecommendationStatsEvaluator(RecommendationCatalog catalog, RecommendationRequest request) {
    this.catalog = catalog;
    this.request = request;
  }

  record Evaluation(PassiveStatsResult passive, ScenarioStatsResult scenario) {
    boolean eligible() {
      return passive.coverage() == PassiveStatsResult.Coverage.CALCULATED
          && (scenario == null || scenario.coverage() == ScenarioStatsResult.Coverage.CALCULATED)
          && StatPriority.complete(total());
    }
    BaseStats total() { return scenario == null ? passive.adjusted() : scenario.total(); }
    BaseStats adjustments() {
      return scenario == null ? passive.adjustments() : BaseStats.sum(List.of(passive.adjustments(), scenario.adjustments()));
    }
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
    var scenario = request.basis() == RecommendationBasis.CURRENT_SCENARIO
        ? ScenarioStatsCalculator.calculate(catalog.rules(), passive, request.scenario()) : null;
    return new Evaluation(passive, scenario);
  }
}
