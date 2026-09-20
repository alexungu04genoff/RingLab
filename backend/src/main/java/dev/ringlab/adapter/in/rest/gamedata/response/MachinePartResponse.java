package dev.ringlab.adapter.in.rest.gamedata.response;

import dev.ringlab.domain.gamedata.Machine;
import dev.ringlab.domain.gamedata.MachinePart;
import dev.ringlab.domain.gamedata.MachinePartType;
import dev.ringlab.domain.gamedata.RacingType;
import java.util.UUID;

public record MachinePartResponse(
    UUID id, MachinePartType type, UUID sourceMachineId, String sourceMachineName,
    String sourceMachineImagePath, RacingType racingType) {
  public static MachinePartResponse from(MachinePart part, Machine source) {
    return new MachinePartResponse(
        part.id(), part.type(), source.id(), source.name(), source.imagePath(), source.racingType());
  }
}
