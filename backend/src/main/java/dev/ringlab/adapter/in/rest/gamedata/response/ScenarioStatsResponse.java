package dev.ringlab.adapter.in.rest.gamedata.response;

import dev.ringlab.domain.gamedata.ScenarioStatsResult;
import java.util.List;
import java.util.UUID;

public record ScenarioStatsResponse(PassiveStatsResponse passive, StatsResponse adjustments,
    StatsResponse knownSubtotal, StatsResponse total, ScenarioStatsResult.Coverage coverage,
    String ruleset, String supportedVersion, String note, List<Effect> effects) {
  public record Effect(UUID gadgetId, String gadgetName, String effectId, String label,
      ScenarioStatsResult.Status status, boolean statEffect, StatsResponse adjustment,
      String explanation, List<String> sources) {}
  public static ScenarioStatsResponse from(ScenarioStatsResult value) {
    return new ScenarioStatsResponse(PassiveStatsResponse.from(value.passive()), StatsResponse.from(value.adjustments()),
        StatsResponse.from(value.knownSubtotal()), value.total() == null ? null : StatsResponse.from(value.total()),
        value.coverage(), value.ruleset(), value.supportedVersion(), value.note(),
        value.effects().stream().map(e -> new Effect(e.gadgetId(), e.gadgetName(), e.effectId(), e.label(),
            e.status(), e.statEffect(), e.adjustment() == null ? null : StatsResponse.from(e.adjustment()),
            e.explanation(), e.sources())).toList());
  }
}
