package dev.ringlab.domain.gamedata.importing;

import dev.ringlab.domain.gamedata.*;
import java.util.List;
import java.util.UUID;

/** Complete supplied catalog; each version has an independent, explicit snapshot. */
public record GameDataSet(List<CatalogRow<Racer>> racers, List<CatalogRow<Machine>> machines,
    List<CatalogRow<MachinePart>> parts, List<CatalogRow<Gadget>> gadgets,
    List<CatalogRow<RaceMap>> maps, List<CatalogRow<GameVersion>> versions,
    List<VersionStats> snapshots) {
  public GameDataSet {
    racers = List.copyOf(racers);
    machines = List.copyOf(machines);
    parts = List.copyOf(parts);
    gadgets = List.copyOf(gadgets);
    maps = List.copyOf(maps);
    versions = List.copyOf(versions);
    snapshots = List.copyOf(snapshots);
  }

  public record StatRow(UUID itemId, BaseStats stats) {}

  public record VersionStats(UUID versionId, List<CatalogRow<StatRow>> racers,
                             List<CatalogRow<StatRow>> parts) {
    public VersionStats {
      racers = List.copyOf(racers);
      parts = List.copyOf(parts);
    }
  }
}
