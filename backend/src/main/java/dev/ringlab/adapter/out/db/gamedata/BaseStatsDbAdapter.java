package dev.ringlab.adapter.out.db.gamedata;

import dev.ringlab.domain.gamedata.BaseStats;
import dev.ringlab.port.out.BaseStatsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class BaseStatsDbAdapter implements BaseStatsRepository {
  private final EntityManager em;

  public Map<UUID, BaseStats> racerStats(UUID gameVersionId) {
    return read("SELECT racer_id, speed, acceleration, handling, power, boost "
        + "FROM racer_stats WHERE game_version_id = :version", gameVersionId);
  }

  public Map<UUID, BaseStats> machinePartStats(UUID gameVersionId) {
    return read("SELECT machine_part_id, speed, acceleration, handling, power, boost "
        + "FROM machine_part_stats WHERE game_version_id = :version", gameVersionId);
  }

  private Map<UUID, BaseStats> read(String sql, UUID version) {
    Map<UUID, BaseStats> result = new HashMap<>();
    for (Object item : em.createNativeQuery(sql).setParameter("version", version).getResultList()) {
      Object[] row = (Object[]) item;
      result.put((UUID) row[0], new BaseStats((BigDecimal) row[1], (BigDecimal) row[2],
          (BigDecimal) row[3], (BigDecimal) row[4], (BigDecimal) row[5]));
    }
    return result;
  }
}
