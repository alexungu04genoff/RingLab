package dev.ringlab;

import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.adapter.in.rest.gamedata.response.MachinePartResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.MachineResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.RacerResponse;
import dev.ringlab.domain.gamedata.Machine;
import dev.ringlab.domain.gamedata.MachinePart;
import dev.ringlab.domain.gamedata.MachinePartType;
import dev.ringlab.domain.gamedata.Racer;
import dev.ringlab.domain.gamedata.RacingType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GameDataResponseTest {
  @Test
  void unverifiedTypesRemainNullWithoutPreventingCatalogResponses() {
    var racer = new Racer(UUID.randomUUID(), "Amigo", null, "/assets/racers/amigo.png");
    var machine = new Machine(UUID.randomUUID(), "Locomotive de Amigo", null, null);
    var part = new MachinePart(UUID.randomUUID(), machine.id(), MachinePartType.FRONT);

    var racerResponse = RacerResponse.from(racer);
    var machineResponse = MachineResponse.from(machine);
    var partResponse = MachinePartResponse.from(part, machine);

    assertNull(racerResponse.racingType());
    assertEquals(racer.imagePath(), racerResponse.imagePath());
    assertNull(machineResponse.racingType());
    assertNull(machineResponse.imagePath());
    assertNull(partResponse.racingType());
    assertEquals(machine.id(), partResponse.sourceMachineId());
    assertEquals(machine.name(), partResponse.sourceMachineName());
  }

  @Test
  void verifiedTypesKeepTheirExistingTransportValues() {
    for (var type : RacingType.values()) {
      var racer = new Racer(UUID.randomUUID(), "Racer", type, null);
      var machine = new Machine(UUID.randomUUID(), "Machine", type, null);
      var part = new MachinePart(UUID.randomUUID(), machine.id(), MachinePartType.TIRE);

      assertEquals(type.name(), RacerResponse.from(racer).racingType());
      assertEquals(type.name(), MachineResponse.from(machine).racingType());
      assertEquals(type, MachinePartResponse.from(part, machine).racingType());
    }
  }
}
