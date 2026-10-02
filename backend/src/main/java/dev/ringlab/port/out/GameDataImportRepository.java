package dev.ringlab.port.out;

import dev.ringlab.domain.gamedata.importing.GameDataImportPlan;
import dev.ringlab.domain.gamedata.importing.GameDataSet;
import java.util.function.Function;

public interface GameDataImportRepository {
  /** Reads a consistent snapshot without writes. */
  GameDataSet read();

  /** Locks competing imports, reads, calls preparation before any write, then saves atomically.
   * Preparation must reject unsafe/stale plans by throwing; any failure rolls back. */
  GameDataImportPlan apply(Function<GameDataSet, GameDataImportPlan> prepare);
}
