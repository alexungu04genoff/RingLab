package dev.ringlab.port.in;

import dev.ringlab.domain.gamedata.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Typed catalog facts for catalog pages and response enrichment. */
public interface GameDataQueryUseCase {
  List<RaceMap> listRaceMaps();
  List<GameVersion> listGameVersions();
  Optional<GameVersion> findGameVersion(UUID id);
  List<Racer> listRacers();
  Optional<Racer> findRacer(UUID id);
  List<Machine> listMachines();
  Optional<Machine> findMachine(UUID id);
  List<MachinePart> listMachineParts();
  Optional<MachinePart> findMachinePart(UUID id);
  List<Gadget> listGadgets();
  Optional<Gadget> findGadget(UUID id);
}
