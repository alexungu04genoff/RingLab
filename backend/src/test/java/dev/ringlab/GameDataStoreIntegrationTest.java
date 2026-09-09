package dev.ringlab;

import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.application.gamedata.port.out.GameDataStore;
import dev.ringlab.domain.gamedata.Gadget;
import dev.ringlab.domain.gamedata.Machine;
import dev.ringlab.domain.gamedata.Racer;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class GameDataStoreIntegrationTest {
  @Inject GameDataStore gameData;

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

    assertNotNull(racer.racingType());
    assertNotNull(machine.racingType());
    assertNull(gadget.slotCost());
    assertTrue(gameData.findRacer(UUID.randomUUID()).isEmpty());
    assertTrue(gameData.findMachine(UUID.randomUUID()).isEmpty());
    assertTrue(gameData.findGadget(UUID.randomUUID()).isEmpty());
  }
}
