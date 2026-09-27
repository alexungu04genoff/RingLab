package dev.ringlab.domain.gamedata;

import java.util.List;
import java.util.UUID;

/** Reviewed stat-point rules, separate from race events and other measurement units. */
public record GadgetEffectRule(UUID gadgetId, String effectId, String label, Kind kind,
    Subject subject, RacingType requiredType, BaseStats matching, BaseStats nonMatching,
    String explanation, List<String> sources, String stackingGroup) {
  public enum Kind { PASSIVE, CONDITIONAL, NON_STAT, UNSUPPORTED }
  public enum Subject { ANY, MACHINE, RACER }
  public GadgetEffectRule { sources = List.copyOf(sources); }
}
