package dev.ringlab.domain.build.recommendation;

import java.math.BigDecimal;
import java.util.*;

/** Active sacrifices are below 100%; legacy 100% values normalize to explicit Ignore. */
public record BalancedConfiguration(Map<StatPriority, BigDecimal> maximumLossPercent, List<StatPriority> ignored) {
  public BalancedConfiguration {
    if (maximumLossPercent == null || ignored == null
        || maximumLossPercent.keySet().stream().anyMatch(Objects::isNull) || ignored.stream().anyMatch(Objects::isNull)
        || new HashSet<>(ignored).size() != ignored.size()
        || maximumLossPercent.values().stream().anyMatch(v -> v == null || v.signum() < 0 || v.compareTo(BigDecimal.valueOf(100)) > 0))
      throw new IllegalArgumentException("Supply distinct ignored stats and sacrifice percentages from 0 to 100");
    var losses = new EnumMap<StatPriority,BigDecimal>(StatPriority.class);
    losses.putAll(maximumLossPercent);
    var exclusions = new ArrayList<>(ignored);
    for (var stat : StatPriority.values()) if (losses.containsKey(stat) && losses.get(stat).compareTo(BigDecimal.valueOf(100)) == 0) {
      losses.remove(stat);
      if (!exclusions.contains(stat)) exclusions.add(stat);
    }
    maximumLossPercent = Map.copyOf(losses);
    ignored = List.copyOf(exclusions);
  }

  /** Also accepts the old active-only priority list plus ignored tail. */
  public List<StatPriority> completeOrder(List<StatPriority> priorities) {
    if (new HashSet<>(priorities).size() != priorities.size())
      throw new IllegalArgumentException("Each priority must appear once");
    var order = new ArrayList<>(priorities);
    ignored.stream().filter(s -> !order.contains(s)).forEach(order::add);
    var active = new HashSet<>(order); active.removeAll(ignored);
    if (order.size() != 5 || !maximumLossPercent.keySet().equals(active))
      throw new IllegalArgumentException("Order all five stats and supply one sacrifice for every active stat");
    return List.copyOf(order);
  }
}
