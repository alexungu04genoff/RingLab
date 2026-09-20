package dev.ringlab.adapter.in.rest.gamedata.response;

import dev.ringlab.domain.gamedata.BaseStats;
import java.math.BigDecimal;

public record StatsResponse(BigDecimal speed, BigDecimal acceleration, BigDecimal handling,
                            BigDecimal power, BigDecimal boost) {
  public static StatsResponse from(BaseStats stats) {
    return new StatsResponse(stats.speed(), stats.acceleration(), stats.handling(), stats.power(), stats.boost());
  }
}
