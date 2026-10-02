package dev.ringlab.domain.gamedata;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Reviewed interpretation permissions. Fact values and presentation live in versioned rule snapshots. */
public final class PassiveGadgetRules {
  // Importing a future snapshot does not approve its calculations/recommendations automatically.
  public static final String VERSION = "1.4.1";
  public static final BaseStats ZERO = BaseStats.ZERO;
  private static final Set<UUID> REVIEWED_TUNERS = IntStream.rangeClosed(52, 61).mapToObj(PassiveGadgetRules::id).collect(Collectors.toUnmodifiableSet());
  private PassiveGadgetRules() {}

  public static UUID id(int suffix) {
    return UUID.fromString("70000000-0000-4000-8000-" + String.format("%012d", suffix));
  }
  public static boolean reviewedStackingGroup(GadgetEffectRule rule) {
    if (!"machine-tuners".equals(rule.stackingGroup()) || !"stats".equals(rule.effectId())
        || rule.kind() != GadgetEffectRule.Kind.PASSIVE || rule.subject() != GadgetEffectRule.Subject.MACHINE) return false;
    return REVIEWED_TUNERS.contains(rule.gadgetId());
  }
  /** A CSV group label alone can never grant stacking to an arbitrary effect. */
  public static boolean verifiedStack(GadgetEffectRule first, GadgetEffectRule second) {
    if (first.gadgetId().equals(second.gadgetId())) return false;
    if (reviewedStackingGroup(first) && reviewedStackingGroup(second)) return true;
    if (!first.effectId().equals("stats") || !second.effectId().equals("stats")) return false;
    // Only Acceleration Tuner 2 + Acceleration Machine Kit; recovery is a separate excluded effect.
    return first.gadgetId().equals(id(55)) && second.gadgetId().equals(id(34))
        || first.gadgetId().equals(id(34)) && second.gadgetId().equals(id(55));
  }
  public static BaseStats points(int speed, int acceleration, int handling, int power, int boost) {
    return new BaseStats(BigDecimal.valueOf(speed), BigDecimal.valueOf(acceleration),
        BigDecimal.valueOf(handling), BigDecimal.valueOf(power), BigDecimal.valueOf(boost));
  }
}
