package dev.ringlab.domain.build.recommendation;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static dev.ringlab.domain.build.recommendation.StatPriority.*;
import static org.junit.jupiter.api.Assertions.*;

class BalancedConfigurationTest {
  static BalancedConfiguration config(List<StatPriority> active, String... percentages) {
    var losses = new EnumMap<StatPriority, BigDecimal>(StatPriority.class);
    for (int i = 0; i < active.size(); i++) losses.put(active.get(i),new BigDecimal(percentages[i]));
    return new BalancedConfiguration(losses,Arrays.stream(values()).filter(s -> !active.contains(s)).toList());
  }

  @Test void signedThresholdsAreExactIncludingZeroAndDecimalBoundaries() {
    for (var example : List.of(List.of("100","5","95"),List.of("-10","20","-12"),
        List.of("0","99.9","0"),List.of("-10","0","-10"),List.of("1.25","0.1","1.24875"))) {
      assertEquals(0,new BigDecimal(example.get(2)).compareTo(BalancedStage.threshold(
          new BigDecimal(example.get(0)),new BigDecimal(example.get(1)))));
    }
  }

  @Test void invalidPartitionsAndPercentagesAreRejectedAndConfigurationIsImmutable() {
    for (String value : List.of("-1","100.01"))
      assertThrows(IllegalArgumentException.class,() -> config(List.of(BOOST),value));
    var valid = config(List.of(BOOST,SPEED),"5","10");
    assertThrows(IllegalArgumentException.class,() -> valid.completeOrder(List.of()));
    assertThrows(IllegalArgumentException.class,() -> valid.completeOrder(List.of(BOOST,BOOST)));
    assertThrows(IllegalArgumentException.class,() -> valid.completeOrder(List.of(BOOST)));
    assertThrows(IllegalArgumentException.class,() -> new BalancedConfiguration(Map.of(BOOST,BigDecimal.ZERO),List.of(SPEED,SPEED,HANDLING,POWER)));
    assertThrows(IllegalArgumentException.class,() -> new BalancedConfiguration(null,List.of()));
    assertThrows(IllegalArgumentException.class,() -> new BalancedConfiguration(Map.of(),null));
    var nullKey = new HashMap<StatPriority,BigDecimal>(); nullKey.put(null,BigDecimal.ZERO);
    assertThrows(IllegalArgumentException.class,() -> new BalancedConfiguration(nullKey,List.of()));
    var nullValue = new HashMap<StatPriority,BigDecimal>(); nullValue.put(BOOST,null);
    assertThrows(IllegalArgumentException.class,() -> new BalancedConfiguration(nullValue,List.of()));
    assertThrows(IllegalArgumentException.class,() -> new BalancedConfiguration(Map.of(),Arrays.asList((StatPriority)null)));
    var map = new EnumMap<StatPriority,BigDecimal>(StatPriority.class); map.put(BOOST,BigDecimal.ZERO);
    var ignored = new ArrayList<>(List.of(SPEED,ACCELERATION,HANDLING,POWER));
    var copy = new BalancedConfiguration(map,ignored); map.clear(); ignored.clear();
    assertEquals(1,copy.maximumLossPercent().size()); assertEquals(4,copy.ignored().size());
    assertEquals(List.of(BOOST,SPEED,ACCELERATION,HANDLING,POWER),copy.completeOrder(List.of(BOOST)));
    var legacy = config(List.of(BOOST),"100");
    assertTrue(legacy.maximumLossPercent().isEmpty()); assertEquals(5,legacy.ignored().size());
    assertEquals(5,legacy.completeOrder(List.of()).size());
  }
}
