package dev.ringlab.application.gamedata;

import dev.ringlab.application.ValidationException;
import dev.ringlab.domain.gamedata.Machine;
import dev.ringlab.domain.gamedata.MachineFamily;
import dev.ringlab.domain.gamedata.MachinePart;
import dev.ringlab.domain.gamedata.MachinePartType;
import dev.ringlab.port.out.GameDataRepository;
import java.util.Locale;

/** Checks supplied parts only; publishing separately requires a complete setup. */
public final class MachineCompatibility {
  private MachineCompatibility() {}

  public static MachineFamily requireCompatible(GameDataRepository game, MachinePart... parts) {
    Machine reference = null;
    String referenceSlot = null;
    for (var part : parts) {
      if (part == null) continue;
      String slot = label(part.type().name());
      var machine = game.findMachine(part.sourceMachineId())
          .orElseThrow(() -> new ValidationException("Unknown source machine ID"));
      if (machine.family() == null || machine.racingType() == null) {
        throw new ValidationException(slot + " compatibility cannot be verified: source machine family or racing type is unknown.");
      }
      if (reference != null) {
        if (reference.family() != machine.family()) {
          throw new ValidationException("All machine parts must belong to the same machine family");
        }
        if (reference.racingType() != machine.racingType()) {
          throw new ValidationException(slot + " must come from a " + label(reference.racingType().name())
              + " machine to match the selected " + referenceSlot + ".");
        }
      }
      if (machine.family() == MachineFamily.BOARD && part.type() == MachinePartType.TIRE) {
        throw new ValidationException("Board builds do not use a tire part");
      }
      if (reference == null) {
        reference = machine;
        referenceSlot = slot;
      }
    }
    return reference == null ? null : reference.family();
  }

  private static String label(String value) {
    return value.charAt(0) + value.substring(1).toLowerCase(Locale.ROOT);
  }
}
