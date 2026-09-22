package dev.ringlab.domain.gamedata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pure character/machine breakdown from an already resolved version's contributions. */
public record BaseStatsBreakdown(BaseStats total, BaseStats character, BaseStats machine) {
  public static final BaseStatsBreakdown UNKNOWN =
      new BaseStatsBreakdown(BaseStats.UNKNOWN, BaseStats.UNKNOWN, BaseStats.UNKNOWN);

  public static BaseStatsBreakdown calculate(UUID racer, UUID front, UUID rear, UUID tire,
      Map<UUID, BaseStats> racers, Map<UUID, BaseStats> parts) {
    var character = value(racers, racer);
    var contributions = new ArrayList<BaseStats>();
    contributions.add(value(parts, front));
    contributions.add(value(parts, rear));
    if (tire != null) contributions.add(value(parts, tire));
    var machine = BaseStats.sum(contributions);
    return new BaseStatsBreakdown(BaseStats.sum(List.of(character, machine)), character, machine);
  }

  private static BaseStats value(Map<UUID, BaseStats> values, UUID id) {
    return id == null ? BaseStats.UNKNOWN : values.getOrDefault(id, BaseStats.UNKNOWN);
  }
}
