package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.gamedata.BaseStats;
import java.math.BigDecimal;
import java.util.*;

/** Fixed normalization, exact floors and score ordering, without rounding or an epsilon. */
public final class BalancedObjective {
  private final List<StatPriority> active;
  private final List<StatPriority> secondary;
  private final Map<StatPriority, BigDecimal> coefficients = new EnumMap<>(StatPriority.class);
  private final Map<StatPriority, BigDecimal> minimum = new EnumMap<>(StatPriority.class);

  public BalancedObjective(BaseStats reference, List<StatPriority> active, BalancedConfiguration configuration) {
    configuration.validate(active);
    if (!StatPriority.complete(reference) || Arrays.stream(StatPriority.values()).anyMatch(s -> s.value(reference).signum() < 0))
      throw new IllegalArgumentException("Balanced requires fully supported, nonnegative reference values for all five stats");
    this.active = List.copyOf(active);
    this.secondary = configuration.secondary();
    // Multiply Q by the positive product of denominators. The baseline term is constant
    // across candidates, so it can be omitted for comparisons. No division is performed.
    for (int i = 0; i < active.size(); i++) {
      var stat = active.get(i);
      var coefficient = BigDecimal.valueOf(active.size() - i);
      for (var other : active) if (other != stat)
        coefficient = coefficient.multiply(other.value(reference).signum() == 0 ? BigDecimal.ONE : other.value(reference));
      coefficients.put(stat, coefficient);
      minimum.put(stat, stat.value(reference).multiply(BigDecimal.ONE.subtract(configuration.maximumLossPercent().get(stat).movePointLeft(2))));
    }
  }

  public Map<StatPriority, BigDecimal> minimum() { return Map.copyOf(minimum); }

  public boolean feasible(BaseStats stats) {
    return StatPriority.complete(stats) && active.stream().allMatch(s -> s.value(stats).compareTo(minimum.get(s)) >= 0);
  }

  public BigDecimal score(BaseStats stats) {
    var sum = BigDecimal.ZERO;
    for (var stat : active) sum = sum.add(coefficients.get(stat).multiply(stat.value(stats)));
    return sum;
  }

  public int compareActive(BaseStats left, BaseStats right) {
    int score = score(left).compareTo(score(right));
    return score != 0 ? score : StatPriority.compare(left, right, active);
  }

  public BigDecimal secondaryTotal(BaseStats stats) {
    return secondary.stream().map(s -> s.value(stats)).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public int compare(BaseStats left, BaseStats right) {
    int activeComparison = compareActive(left, right);
    return activeComparison != 0 ? activeComparison : secondaryTotal(left).compareTo(secondaryTotal(right));
  }
}
