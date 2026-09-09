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
    Racer racer = gameData.listRacers().getFirst();
    Machine machine = gameData.listMachines().getFirst();
    Gadget gadget = gameData.listGadgets().getFirst();

    assertEquals(6, gameData.listRacers().size());
    assertEquals(10, gameData.listMachines().size());
    assertEquals(20, gameData.listGadgets().size());

    assertEquals(racer, gameData.findRacer(racer.id()).orElseThrow());
    assertEquals(machine, gameData.findMachine(machine.id()).orElseThrow());
    assertEquals(gadget, gameData.findGadget(gadget.id()).orElseThrow());

    assertEquals(RacingType.HANDLING, racer.racingType());
    assertEquals(RacingType.SPEED, machine.racingType());
    assertNull(gadget.slotCost());
    assertTrue(gameData.findRacer(UUID.randomUUID()).isEmpty());
    assertTrue(gameData.findMachine(UUID.randomUUID()).isEmpty());
    assertTrue(gameData.findGadget(UUID.randomUUID()).isEmpty());
  }
}
