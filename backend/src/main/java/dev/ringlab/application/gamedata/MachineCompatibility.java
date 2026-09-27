package dev.ringlab.application.gamedata;

import dev.ringlab.application.ValidationException;
import dev.ringlab.domain.gamedata.Machine;
import dev.ringlab.domain.gamedata.RacingType;
import dev.ringlab.domain.gamedata.MachinePart;
import dev.ringlab.port.out.GameDataRepository;

/** Checks supplied parts only; publishing separately requires a complete setup. */
public final class MachineCompatibility {
  private MachineCompatibility() {}

  public static RacingType requireCompatible(GameDataRepository game, MachinePart... parts) {
    return requireCompatible(game::findMachine, parts);
  }

  public static RacingType requireCompatible(
      java.util.function.Function<java.util.UUID, java.util.Optional<Machine>> machines,
      MachinePart... parts) {
    try {
      return dev.ringlab.domain.gamedata.MachineCompatibility.requireCompatible(machines, parts);
    } catch (IllegalArgumentException invalid) {
      throw new ValidationException(invalid.getMessage());
    }
  }
}
