package dev.ringlab.domain.gamedata;

import java.util.List;
import java.util.UUID;

public record PassiveStatsResult(BaseStatsBreakdown base, BaseStats adjustments, BaseStats adjusted,
    Coverage coverage, String ruleset, String supportedVersion, String note, List<Effect> effects) {
  public enum Coverage { CALCULATED, PARTIAL, UNSUPPORTED_VERSION, INVALID_LOADOUT }
  public enum Status { APPLIED, NOT_MATCHED, REQUIRES_SELECTION, CONDITIONAL, NON_STAT, UNSUPPORTED }
  public record Effect(UUID gadgetId, String gadgetName, String effectId, String label,
      Status status, BaseStats adjustment, String explanation, List<String> sources) {
    public Effect { sources = List.copyOf(sources); }
  }
  public PassiveStatsResult { effects = List.copyOf(effects); }
}
