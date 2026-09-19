package dev.ringlab.port.out;

import dev.ringlab.domain.gamedata.BaseStats;
import java.util.Map;
import java.util.UUID;

public interface BaseStatsRepository {
  Map<UUID, BaseStats> racerStats(UUID gameVersionId);
  Map<UUID, BaseStats> machinePartStats(UUID gameVersionId);
}
