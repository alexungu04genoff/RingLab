package dev.ringlab.adapter.in.rest.build.response;

import dev.ringlab.adapter.in.rest.gamedata.response.PassiveStatsResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.ScenarioStatsResponse;
import dev.ringlab.domain.build.recommendation.RecommendationResult;
import dev.ringlab.domain.gamedata.*;
import java.util.List;

/** Diagnostics only: objective totals live in currentStats/recommendedStats, never in a partial subtotal. */
public record RecommendationScenarioResponse(ScenarioContext context, String ruleset, Evaluation current, Evaluation recommended) {
  public record Evaluation(PassiveStatsResponse passive, ScenarioStatsResult.Coverage coverage,
      List<ScenarioStatsResponse.Effect> effects, List<ScenarioAssumption> assumptions) {
    static Evaluation from(ScenarioStatsResult value) {
      if (value == null) return null;
      var response = ScenarioStatsResponse.from(value);
      return new Evaluation(response.passive(), value.coverage(), response.effects(), value.assumptions());
    }
  }
  static RecommendationScenarioResponse from(RecommendationResult.ScenarioDetails value) {
    return value == null ? null : new RecommendationScenarioResponse(value.context(), value.ruleset(),
        Evaluation.from(value.current()), Evaluation.from(value.recommended()));
  }
}
