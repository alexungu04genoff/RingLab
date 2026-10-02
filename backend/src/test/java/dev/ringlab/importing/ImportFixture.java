package dev.ringlab.importing;

import dev.ringlab.domain.gamedata.*;
import dev.ringlab.domain.gamedata.importing.*;
import dev.ringlab.domain.gamedata.importing.GameDataSet.*;
import java.time.LocalDate;
import java.util.*;

public final class ImportFixture {
  private ImportFixture() {}
  public static UUID id(int n) { return UUID.fromString("00000000-0000-4000-8000-%012d".formatted(n)); }
  public static <T> CatalogRow<T> row(T value) { return new CatalogRow<>("fixture.csv", 2, value); }
  public static GameDataSet sample() {
    return new GameDataSet(List.of(row(new Racer(id(1), "Racer", RacingType.SPEED, null))),
        List.of(row(new Machine(id(2), "Machine", RacingType.SPEED, null))),
        List.of(row(new MachinePart(id(3), id(2), MachinePartType.FRONT)),
            row(new MachinePart(id(4), id(2), MachinePartType.REAR)),
            row(new MachinePart(id(5), id(2), MachinePartType.TIRE))),
        List.of(row(new Gadget(id(6), "Gadget", null, 1, null))),
        List.of(row(new RaceMap(id(7), "Map", RaceMap.Category.MAIN_COURSE, null, null, 1))),
        List.of(row(new GameVersion(id(8), "1.0", LocalDate.of(2026, 1, 1)))),
        List.of(new VersionStats(id(8), List.of(row(new StatRow(id(1), BaseStats.ZERO))),
            List.of(row(new StatRow(id(3), BaseStats.UNKNOWN)), row(new StatRow(id(4), BaseStats.ZERO)),
                row(new StatRow(id(5), BaseStats.ZERO))))));
  }

  public static GameDataSet empty() { return new GameDataSet(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()); }

  /** Readable test edits without a production builder or mutable catalog model. */
  public static final class Edit {
    public final List<CatalogRow<Racer>> racers;
    public final List<CatalogRow<Machine>> machines;
    public final List<CatalogRow<MachinePart>> parts;
    public final List<CatalogRow<Gadget>> gadgets;
    public final List<CatalogRow<RaceMap>> maps;
    public final List<CatalogRow<GameVersion>> versions;
    public final List<VersionStats> snapshots;
    public final List<GameDataRuleSet> ruleSets;
    public Edit(GameDataSet d) {
      racers = new ArrayList<>(d.racers()); machines = new ArrayList<>(d.machines());
      parts = new ArrayList<>(d.parts()); gadgets = new ArrayList<>(d.gadgets());
      maps = new ArrayList<>(d.maps()); versions = new ArrayList<>(d.versions());
      snapshots = new ArrayList<>(d.snapshots());
      ruleSets = new ArrayList<>(d.ruleSets());
    }
    public GameDataSet build() { return new GameDataSet(racers, machines, parts, gadgets, maps, versions, snapshots, ruleSets); }
  }
}
