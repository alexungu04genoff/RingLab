package dev.ringlab.port.out;

import dev.ringlab.domain.gamedata.importing.GameDataSet;

/** Consistent read-only snapshot. Deliberately exposes no import or mutation capability. */
public interface GameDataSnapshotReader {
  GameDataSet read();
}
