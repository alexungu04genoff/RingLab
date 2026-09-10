package dev.ringlab.adapter.in.rest.gamedata.response;

import dev.ringlab.domain.gamedata.Machine;
import java.util.UUID;

public record MachineResponse(UUID id, String name, String racingType, String imagePath) {
  public static MachineResponse from(Machine machine) {
    return new MachineResponse(
        machine.id(), machine.name(), machine.racingType().name(), machine.imagePath());
  }
}
