package dev.ringlab.domain.gamedata;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Reviewed interpretation permissions. Fact values and presentation live in versioned rule snapshots. */
public final class PassiveGadgetRules {
  // Importing a future snapshot does not approve its calculations/recommendations automatically.
  public static final String VERSION = "1.4.1";
  public static final BaseStats ZERO = BaseStats.ZERO;
  public static final String POLICY = "crossworlds-1.4.1-passive-additive-v1";
  public static final String EVIDENCE = "SUPPORTED_BY_COMMUNITY_CALCULATOR";
  // Closed effect identities, not CSV group labels. Values remain in the immutable fact snapshot.
  private static final Set<UUID> REVIEWED_NUMERIC = Stream.concat(
      Stream.of(11,12,13,16,20,23,34,37,38,49,50,52,53,54,55,56,57,58,59,60,61,62).map(PassiveGadgetRules::id),
      Stream.of(UUID.fromString("f6f75d95-16dd-538d-89b5-49c71cc8a346"))).collect(Collectors.toUnmodifiableSet());
  private PassiveGadgetRules() {}

  public static UUID id(int suffix) {
    return UUID.fromString("70000000-0000-4000-8000-" + String.format("%012d", suffix));
  }
  public static boolean reviewedNumeric(GadgetEffectRule rule) {
    return rule.kind() == GadgetEffectRule.Kind.PASSIVE && rule.effectId().equals("stats")
        && REVIEWED_NUMERIC.contains(rule.gadgetId());
  }
  public static BaseStats points(int speed, int acceleration, int handling, int power, int boost) {
    return new BaseStats(BigDecimal.valueOf(speed), BigDecimal.valueOf(acceleration),
        BigDecimal.valueOf(handling), BigDecimal.valueOf(power), BigDecimal.valueOf(boost));
  }
}
