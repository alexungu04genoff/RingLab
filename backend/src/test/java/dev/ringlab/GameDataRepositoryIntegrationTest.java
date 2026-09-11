package dev.ringlab;

import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.domain.gamedata.Gadget;
import dev.ringlab.domain.gamedata.Machine;
import dev.ringlab.domain.gamedata.Racer;
import dev.ringlab.domain.gamedata.RacingType;
import dev.ringlab.port.out.GameDataRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class GameDataRepositoryIntegrationTest {
  @Inject GameDataRepository gameData;

  @Test
  void retrievesRacersMachinesAndGadgetsAsTheirOwnDomainTypes() {
    Racer racer = gameData.listRacers().stream().filter(r -> r.name().equals("Amy Rose")).findFirst().orElseThrow();
    Machine machine = gameData.listMachines().stream().filter(m -> m.name().equals("Dark Reaper")).findFirst().orElseThrow();
    Gadget gadget = gameData.listGadgets().getFirst();

    assertEquals(53, gameData.listRacers().size());
    assertEquals(27, gameData.listMachines().size());
    assertEquals(81, gameData.listMachineParts().size());
    for (var source : gameData.listMachines()) {
      var parts = gameData.listMachineParts().stream()
          .filter(p -> p.sourceMachineId().equals(source.id())).toList();
      assertEquals(java.util.Set.of("FRONT", "REAR", "TIRE"),
          parts.stream().map(p -> p.type().name()).collect(java.util.stream.Collectors.toSet()));
      for (var part : parts) assertEquals(part, gameData.findMachinePart(part.id()).orElseThrow());
    }
    assertEquals(20, gameData.listGadgets().size());

    assertEquals(racer, gameData.findRacer(racer.id()).orElseThrow());
    assertEquals(machine, gameData.findMachine(machine.id()).orElseThrow());
    assertEquals(gadget, gameData.findGadget(gadget.id()).orElseThrow());

    assertEquals(RacingType.HANDLING, racer.racingType());
    assertEquals(RacingType.SPEED, machine.racingType());
    assertNull(gameData.listRacers().stream().filter(r -> r.name().equals("Amigo")).findFirst().orElseThrow().racingType());
    assertNull(gameData.listMachines().stream().filter(m -> m.name().equals("Locomotive de Amigo")).findFirst().orElseThrow().racingType());
    assertNull(gadget.slotCost());
    assertTrue(gameData.findRacer(UUID.randomUUID()).isEmpty());
    assertTrue(gameData.findMachine(UUID.randomUUID()).isEmpty());
    assertTrue(gameData.findGadget(UUID.randomUUID()).isEmpty());
  }
}
