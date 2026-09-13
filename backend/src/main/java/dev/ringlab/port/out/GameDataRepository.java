package dev.ringlab.port.out;

import dev.ringlab.domain.gamedata.Gadget;
import dev.ringlab.domain.gamedata.GameVersion;
import dev.ringlab.domain.gamedata.Machine;
import dev.ringlab.domain.gamedata.MachinePart;
import dev.ringlab.domain.gamedata.Racer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameDataRepository {
  /** Release date descending, then UUID ascending (canonical textual/unsigned order).
   * The first entry is the latest known catalog version; version strings are not sorted.
   */
  List<GameVersion> listGameVersions();

  Optional<GameVersion> findGameVersion(UUID id);

  /** Catalog racers ordered by name ascending using the store's text collation. */
  List<Racer> listRacers();

  Optional<Racer> findRacer(UUID id);

  /** Source machines ordered by name ascending using the store's text collation. */
  List<Machine> listMachines();

  Optional<Machine> findMachine(UUID id);

  /** Parts ordered by source-machine UUID ascending, then type name ascending. */
  List<MachinePart> listMachineParts();

  Optional<MachinePart> findMachinePart(UUID id);

  /** Catalog gadgets ordered by name ascending using the store's text collation. */
  List<Gadget> listGadgets();

  Optional<Gadget> findGadget(UUID id);
}
