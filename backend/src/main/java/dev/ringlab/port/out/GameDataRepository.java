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
  /** Returns versions newest release date first, then ID. */
  List<GameVersion> listGameVersions();

  Optional<GameVersion> findGameVersion(UUID id);

  /** Returns racers alphabetically by name. */
  List<Racer> listRacers();

  Optional<Racer> findRacer(UUID id);

  /** Returns source machines alphabetically by name. */
  List<Machine> listMachines();

  Optional<Machine> findMachine(UUID id);

  /** Returns parts grouped by source-machine ID, then part type. */
  List<MachinePart> listMachineParts();

  Optional<MachinePart> findMachinePart(UUID id);

  /** Returns gadgets alphabetically by name. */
  List<Gadget> listGadgets();

  Optional<Gadget> findGadget(UUID id);
}
