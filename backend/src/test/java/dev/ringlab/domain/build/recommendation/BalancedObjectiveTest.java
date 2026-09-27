package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.gamedata.BaseStats;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static dev.ringlab.domain.build.recommendation.StatPriority.*;
import static org.junit.jupiter.api.Assertions.*;

class BalancedObjectiveTest {
  static BaseStats stats(String speed, String acceleration, String handling, String power, String boost) {
    return new BaseStats(new BigDecimal(speed), new BigDecimal(acceleration), new BigDecimal(handling), new BigDecimal(power), new BigDecimal(boost));
  }
  static BalancedConfiguration config(List<StatPriority> active, String... percentages) {
    var losses = new EnumMap<StatPriority, BigDecimal>(StatPriority.class);
    for (int i = 0; i < active.size(); i++) losses.put(active.get(i), new BigDecimal(percentages[i]));
    return new BalancedConfiguration(losses, Arrays.stream(values()).filter(s -> !active.contains(s)).toList());
  }
  static BalancedObjective objective(BaseStats reference, List<StatPriority> active, String... percentages) {
    return new BalancedObjective(reference, active, config(active, percentages));
  }

  @Test void floorsAreExactIndependentAndNeverCompensated() {
    var base = stats("80", "80", "80", "80", "80");
    var policy = objective(base, List.of(BOOST, SPEED), "5", "0");
    assertEquals(0, policy.minimum().get(BOOST).compareTo(new BigDecimal("76")));
    assertTrue(policy.feasible(stats("80", "0", "0", "0", "76")));
    assertFalse(policy.feasible(stats("999999", "80", "80", "80", "75.99")));
    assertFalse(policy.feasible(stats("79.999999999999999999", "80", "80", "80", "1000")));
    assertTrue(policy.feasible(stats("81", "80", "80", "80", "81")));
    var sacrifice = stats("90", "80", "80", "80", "78");
    assertTrue(policy.feasible(sacrifice));
    assertTrue(policy.compare(sacrifice, base) > 0); // Only 2.5% of the permitted 5% was used.
    assertFalse(policy.feasible(stats("100", "80", "80", "80", "75"))); // Reuse never compounds.
  }

  @Test void rankWeightsChangeOnReorderRemovalAndRestore() {
    var base = stats("100", "100", "100", "100", "100");
    var boostGain = stats("100", "100", "100", "100", "110");
    var speedGain = stats("110", "100", "100", "100", "100");
    assertTrue(objective(base, List.of(BOOST, SPEED), "0", "0").compare(boostGain, speedGain) > 0);
    assertTrue(objective(base, List.of(SPEED, BOOST), "0", "0").compare(boostGain, speedGain) < 0);
    var removed = objective(base, List.of(SPEED), "0");
    assertEquals(0, removed.score(base).compareTo(removed.score(boostGain)));
    assertFalse(removed.minimum().containsKey(BOOST));
    assertTrue(objective(base, List.of(BOOST, SPEED), "0", "0").compare(boostGain, speedGain) > 0);
  }

  @Test void exactPrimaryTiesUseActiveOrderThenCombinedSecondaryTotal() {
    var policy = objective(stats("100", "100", "100", "100", "100"), List.of(BOOST, SPEED), "100", "100");
    var left = stats("100", "0", "0", "0", "110");
    var right = stats("120", "999", "999", "999", "100");
    assertEquals(0, policy.score(left).compareTo(policy.score(right)));
    assertTrue(policy.compare(left, right) > 0);
    assertTrue(policy.compare(stats("100", "0", "0", "75", "110"), stats("100", "0", "0", "10", "110")) > 0);
    assertTrue(policy.compare(stats("100", "0", "65", "65", "110"), stats("100", "0", "20", "100", "110")) > 0);
    assertTrue(policy.compare(stats("100", "0", "0", "0", "110.00000000000000000001"), right) > 0);
    assertEquals(0, policy.compare(stats("100.00", "0", "20", "100", "110"), stats("100", "0", "65", "55", "110.0")));
  }

  @Test void zeroBaselineUsesOnePointAndNonterminatingRatiosAreComparedExactly() {
    var policy = objective(stats("3", "0", "0", "0", "0"), List.of(BOOST, SPEED), "100", "100");
    assertTrue(policy.feasible(stats("0", "0", "0", "0", "0")));
    assertFalse(policy.feasible(stats("30", "0", "0", "0", "-0.0001")));
    assertEquals(0, policy.score(stats("6", "0", "0", "0", "0")).compareTo(policy.score(stats("0", "0", "0", "0", "1"))));
    assertTrue(policy.compare(stats("0", "0", "0", "0", "1"), stats("6", "0", "0", "0", "0")) > 0);
    assertThrows(IllegalArgumentException.class, () -> objective(BaseStats.UNKNOWN, List.of(BOOST), "0"));
    assertThrows(IllegalArgumentException.class, () -> objective(stats("0", "0", "0", "-1", "80"), List.of(BOOST), "0"));
  }

  @Test void invalidPartitionsAndPercentagesAreRejectedAndConfigurationIsImmutable() {
    for (String value : List.of("-1", "100.01"))
      assertThrows(IllegalArgumentException.class, () -> config(List.of(BOOST), value));
    assertThrows(NumberFormatException.class, () -> new BigDecimal("NaN"));
    assertThrows(NumberFormatException.class, () -> new BigDecimal("Infinity"));
    var valid = config(List.of(BOOST, SPEED), "5", "10");
    assertThrows(IllegalArgumentException.class, () -> valid.validate(List.of()));
    assertThrows(IllegalArgumentException.class, () -> valid.validate(List.of(BOOST, BOOST)));
    assertThrows(IllegalArgumentException.class, () -> valid.validate(List.of(BOOST)));
    assertThrows(IllegalArgumentException.class, () -> new BalancedConfiguration(Map.of(BOOST, BigDecimal.ZERO), List.of(SPEED, SPEED, HANDLING, POWER)).validate(List.of(BOOST)));
    assertThrows(IllegalArgumentException.class, () -> new BalancedConfiguration(null, List.of()));
    assertThrows(IllegalArgumentException.class, () -> new BalancedConfiguration(Map.of(), null));
    var map = new EnumMap<StatPriority, BigDecimal>(StatPriority.class); map.put(BOOST, BigDecimal.ZERO);
    var secondary = new ArrayList<>(List.of(SPEED, ACCELERATION, HANDLING, POWER));
    var copy = new BalancedConfiguration(map, secondary); map.clear(); secondary.clear();
    assertEquals(1, copy.maximumLossPercent().size()); assertEquals(4, copy.secondary().size());
  }
}
