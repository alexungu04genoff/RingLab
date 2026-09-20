package dev.ringlab.application.gamedata;

import dev.ringlab.application.ValidationException;
import dev.ringlab.domain.gamedata.Machine;
import dev.ringlab.domain.gamedata.MachineComposition;
import dev.ringlab.domain.gamedata.RacingType;
import dev.ringlab.domain.gamedata.MachinePart;
import dev.ringlab.domain.gamedata.MachinePartType;
import dev.ringlab.port.out.GameDataRepository;
import java.util.Locale;

/** Checks supplied parts only; publishing separately requires a complete setup. */
public final class MachineCompatibility {
  private MachineCompatibility() {}

  public static RacingType requireCompatible(GameDataRepository game, MachinePart... parts) {
    return requireCompatible(game::findMachine, parts);
  }

  public static RacingType requireCompatible(
      java.util.function.Function<java.util.UUID, java.util.Optional<Machine>> machines,
      MachinePart... parts) {
    Machine reference = null;
    String referenceSlot = null;
    for (var part : parts) {
      if (part == null) continue;
      String slot = label(part.type().name());
      var machine = machines.apply(part.sourceMachineId())
          .orElseThrow(() -> new ValidationException("Unknown source machine ID"));
      if (machine.racingType() == null) {
        throw new ValidationException(slot + " compatibility cannot be verified: source machine racing type is unknown.");
      }
      if (reference != null) {
        if (reference.racingType() != machine.racingType()) {
          throw new ValidationException(slot + " must come from a " + label(reference.racingType().name())
              + " machine to match the selected " + referenceSlot + ".");
        }
      }
      if (!MachineComposition.requiredSlots(machine.racingType()).contains(part.type())) {
        throw new ValidationException("Boost machines do not use a tire part");
      }
      if (reference == null) {
        reference = machine;
        referenceSlot = slot;
      }
    }
    return reference == null ? null : reference.racingType();
  }

  private static String label(String value) {
    return value.charAt(0) + value.substring(1).toLowerCase(Locale.ROOT);
  }
}
