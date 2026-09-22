package dev.ringlab.adapter.in.rest.gamedata.response;

import dev.ringlab.domain.gamedata.BaseStatsBreakdown;
import java.math.BigDecimal;

public record BuildStatsResponse(BigDecimal speed, BigDecimal acceleration, BigDecimal handling,
                                 BigDecimal power, BigDecimal boost,
                                 StatsResponse character, StatsResponse machine) {
  public static BuildStatsResponse from(BaseStatsBreakdown stats) {
    var total = stats.total();
    return new BuildStatsResponse(total.speed(), total.acceleration(), total.handling(), total.power(), total.boost(),
        StatsResponse.from(stats.character()), StatsResponse.from(stats.machine()));
  }
}
