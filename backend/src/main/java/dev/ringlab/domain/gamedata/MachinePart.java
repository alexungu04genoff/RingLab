package dev.ringlab.domain.gamedata;

import java.util.Objects;
import java.util.UUID;

public record MachinePart(UUID id, UUID sourceMachineId, MachinePartType type) {
  public MachinePart {
    Objects.requireNonNull(id);
    Objects.requireNonNull(sourceMachineId);
    Objects.requireNonNull(type);
  }
}
