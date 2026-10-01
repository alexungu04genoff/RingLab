package dev.ringlab.port.in;

import dev.ringlab.domain.gamedata.BaseStats;
import dev.ringlab.domain.gamedata.BaseStatsBreakdown;
import java.util.Map;
import java.util.UUID;

public interface BaseStatsUseCase {
  record Catalog(UUID gameVersionId, Map<UUID, BaseStats> racers,
                 Map<UUID, BaseStats> machineParts, Map<UUID, BaseStats> machines) {}
  Catalog catalog(UUID version);
  BaseStatsBreakdown buildBreakdown(UUID version, UUID racer, UUID front, UUID rear, UUID tire);
}
