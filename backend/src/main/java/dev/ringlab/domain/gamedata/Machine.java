package dev.ringlab.domain.gamedata;

import java.util.UUID;
import java.util.Objects;

public record Machine(
    UUID id, String name, RacingType racingType, String imagePath, MachineFamily family) {
  public Machine {
    Objects.requireNonNull(id);
    Objects.requireNonNull(name);
    Objects.requireNonNull(family);
  }
}
