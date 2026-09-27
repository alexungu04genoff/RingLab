package dev.ringlab.adapter.in.rest.gamedata.response;

import dev.ringlab.domain.gamedata.PassiveStatsResult;
import java.util.List;
import java.util.UUID;

public record PassiveStatsResponse(BuildStatsResponse base, StatsResponse adjustments, StatsResponse adjusted,
    PassiveStatsResult.Coverage coverage, String ruleset, String supportedVersion, String note, List<Effect> effects) {
  public record Effect(UUID gadgetId, String gadgetName, String effectId, String label,
      PassiveStatsResult.Status status, StatsResponse adjustment, String explanation, List<String> sources) {}
  public static PassiveStatsResponse from(PassiveStatsResult value) {
    return new PassiveStatsResponse(BuildStatsResponse.from(value.base()),StatsResponse.from(value.adjustments()),
        StatsResponse.from(value.adjusted()),value.coverage(),value.ruleset(),value.supportedVersion(),value.note(),
        value.effects().stream().map(e -> new Effect(e.gadgetId(),e.gadgetName(),e.effectId(),e.label(),e.status(),
            StatsResponse.from(e.adjustment()),e.explanation(),e.sources())).toList());
  }
}
