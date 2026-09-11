package dev.ringlab.port.out;

import dev.ringlab.domain.gamedata.Gadget;
import dev.ringlab.domain.gamedata.Machine;
import dev.ringlab.domain.gamedata.MachinePart;
import dev.ringlab.domain.gamedata.Racer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameDataRepository {
  List<Racer> listRacers();

  Optional<Racer> findRacer(UUID id);

  List<Machine> listMachines();

  Optional<Machine> findMachine(UUID id);

  List<MachinePart> listMachineParts();

  Optional<MachinePart> findMachinePart(UUID id);

  List<Gadget> listGadgets();

  Optional<Gadget> findGadget(UUID id);
}
