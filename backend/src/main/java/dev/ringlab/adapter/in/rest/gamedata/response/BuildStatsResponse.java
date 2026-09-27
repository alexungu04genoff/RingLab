package dev.ringlab.adapter.in.rest.gamedata.response;

import dev.ringlab.domain.gamedata.BaseStatsBreakdown;
import java.math.BigDecimal;

public record BuildStatsResponse(BigDecimal speed, BigDecimal acceleration, BigDecimal handling,
                                 BigDecimal power, BigDecimal boost,
                                 StatsResponse character, StatsResponse machine,
                                 @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                                 PassiveStatsResponse passive) {
  public BuildStatsResponse(BigDecimal speed, BigDecimal acceleration, BigDecimal handling,
      BigDecimal power, BigDecimal boost, StatsResponse character, StatsResponse machine) {
    this(speed,acceleration,handling,power,boost,character,machine,null);
  }
  public static BuildStatsResponse withPassive(dev.ringlab.domain.gamedata.PassiveStatsResult result) {
    var base = from(result.base());
    return new BuildStatsResponse(base.speed(),base.acceleration(),base.handling(),base.power(),base.boost(),
        base.character(),base.machine(),PassiveStatsResponse.from(result));
  }
  public static BuildStatsResponse from(BaseStatsBreakdown stats) {
    var total = stats.total();
    return new BuildStatsResponse(total.speed(), total.acceleration(), total.handling(), total.power(), total.boost(),
        StatsResponse.from(stats.character()), StatsResponse.from(stats.machine()));
  }
}
