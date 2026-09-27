package dev.ringlab.domain.build.recommendation;

import java.math.BigDecimal;
import java.util.*;

/** Losses belong only to active priorities. Together the two sets partition all five stats. */
public record BalancedConfiguration(Map<StatPriority, BigDecimal> maximumLossPercent,
    List<StatPriority> secondary) {
  public BalancedConfiguration {
    if (maximumLossPercent == null || secondary == null
        || maximumLossPercent.keySet().stream().anyMatch(Objects::isNull) || secondary.stream().anyMatch(Objects::isNull)
        || maximumLossPercent.values().stream().anyMatch(v -> v == null || v.signum() < 0 || v.compareTo(BigDecimal.valueOf(100)) > 0))
      throw new IllegalArgumentException("Maximum losses must be finite percentages from 0 to 100; secondary stats are required");
    maximumLossPercent = Map.copyOf(maximumLossPercent);
    secondary = List.copyOf(secondary);
  }

  public void validate(List<StatPriority> active) {
    var all = new ArrayList<>(active);
    all.addAll(secondary);
    if (active.isEmpty() || all.size() != 5 || new HashSet<>(all).size() != 5
        || !maximumLossPercent.keySet().equals(new HashSet<>(active)))
      throw new IllegalArgumentException("Balanced requires at least one priority, every stat exactly once, and one maximum loss per active stat");
  }
}
