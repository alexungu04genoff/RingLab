package dev.ringlab.domain.gamedata;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/** Base contributions only; null means unknown, including an absent historical record. */
public record BaseStats(BigDecimal speed, BigDecimal acceleration, BigDecimal handling,
                        BigDecimal power, BigDecimal boost) {
  public static final BaseStats UNKNOWN = new BaseStats(null, null, null, null, null);

  public static BaseStats sum(List<BaseStats> components) {
    return new BaseStats(sumStat(components, BaseStats::speed),
        sumStat(components, BaseStats::acceleration), sumStat(components, BaseStats::handling),
        sumStat(components, BaseStats::power), sumStat(components, BaseStats::boost));
  }

  private static BigDecimal sumStat(List<BaseStats> components, Function<BaseStats, BigDecimal> stat) {
    if (components.isEmpty()) return null;
    BigDecimal total = BigDecimal.ZERO;
    for (BaseStats component : components) {
      BigDecimal value = stat.apply(component);
      if (value == null) return null;
      total = total.add(value);
    }
    return total;
  }
}
