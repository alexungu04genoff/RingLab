package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.gamedata.BaseStats;
import java.math.BigDecimal;
import java.util.List;

public enum StatPriority {
  SPEED, ACCELERATION, HANDLING, BOOST, POWER;

  public BigDecimal value(BaseStats stats) {
    return switch (this) {
      case SPEED -> stats.speed();
      case ACCELERATION -> stats.acceleration();
      case HANDLING -> stats.handling();
      case BOOST -> stats.boost();
      case POWER -> stats.power();
    };
  }

  /** Exact lexicographic comparison; only fully known vectors may compete. */
  public static int compare(BaseStats left, BaseStats right, List<StatPriority> order) {
    for (var stat : order) {
      int difference = stat.value(left).compareTo(stat.value(right));
      if (difference != 0) return difference;
    }
    return 0;
  }

  public static boolean complete(BaseStats stats) {
    return stats != null && java.util.Arrays.stream(values()).allMatch(stat -> stat.value(stats) != null);
  }
}
