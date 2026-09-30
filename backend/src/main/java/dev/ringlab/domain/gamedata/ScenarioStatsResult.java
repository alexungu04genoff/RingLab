package dev.ringlab.domain.gamedata;

import java.util.List;
import java.util.UUID;

public record ScenarioStatsResult(PassiveStatsResult passive, BaseStats adjustments, BaseStats knownSubtotal,
    BaseStats total, Coverage coverage, String ruleset, String supportedVersion, String note, List<Effect> effects) {
  public enum Coverage { CALCULATED, PARTIAL, UNAVAILABLE }
  public enum Status { ACTIVE_AND_APPLIED, CONDITION_NOT_MET, CONDITION_UNKNOWN, ACTIVE_NON_STAT, UNSUPPORTED }
  public record Effect(UUID gadgetId, String gadgetName, String effectId, String label, Status status,
      boolean statEffect, BaseStats adjustment, String explanation, List<String> sources) {
    public Effect { sources = List.copyOf(sources); }
  }
  public ScenarioStatsResult { effects = List.copyOf(effects); }
}
