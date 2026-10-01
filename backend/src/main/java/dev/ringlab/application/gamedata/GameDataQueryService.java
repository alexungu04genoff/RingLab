package dev.ringlab.application.gamedata;

import dev.ringlab.domain.gamedata.*;
import dev.ringlab.port.in.GameDataQueryUseCase;
import dev.ringlab.port.out.GameDataRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/** Provides catalog reads at the application boundary without adding catalog policy. */
@ApplicationScoped
@RequiredArgsConstructor
public class GameDataQueryService implements GameDataQueryUseCase {
  private final GameDataRepository game;

  public List<RaceMap> listRaceMaps() { return game.listRaceMaps(); }
  public List<GameVersion> listGameVersions() { return game.listGameVersions(); }
  public Optional<GameVersion> findGameVersion(UUID id) { return game.findGameVersion(id); }
  public List<Racer> listRacers() { return game.listRacers(); }
  public Optional<Racer> findRacer(UUID id) { return game.findRacer(id); }
  public List<Machine> listMachines() { return game.listMachines(); }
  public Optional<Machine> findMachine(UUID id) { return game.findMachine(id); }
  public List<MachinePart> listMachineParts() { return game.listMachineParts(); }
  public Optional<MachinePart> findMachinePart(UUID id) { return game.findMachinePart(id); }
  public List<Gadget> listGadgets() { return game.listGadgets(); }
  public Optional<Gadget> findGadget(UUID id) { return game.findGadget(id); }
}
