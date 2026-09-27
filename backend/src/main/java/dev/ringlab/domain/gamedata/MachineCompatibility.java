package dev.ringlab.domain.gamedata;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Shared compatibility rule. Missing slots are allowed; callers decide completeness. */
public final class MachineCompatibility {
  private MachineCompatibility() {}

  public static RacingType requireCompatible(Function<UUID, Optional<Machine>> machines, MachinePart... parts) {
    Machine reference = null;
    String referenceSlot = null;
    for (var part : parts) {
      if (part == null) continue;
      String slot = label(part.type().name());
      var machine = machines.apply(part.sourceMachineId())
          .orElseThrow(() -> new IllegalArgumentException("Unknown source machine ID"));
      if (machine.racingType() == null)
        throw new IllegalArgumentException(slot + " compatibility cannot be verified: source machine racing type is unknown.");
      if (reference != null && reference.racingType() != machine.racingType())
        throw new IllegalArgumentException(slot + " must come from a " + label(reference.racingType().name())
            + " machine to match the selected " + referenceSlot + ".");
      if (!MachineComposition.requiredSlots(machine.racingType()).contains(part.type()))
        throw new IllegalArgumentException("Boost machines do not use a tire part");
      if (reference == null) { reference = machine; referenceSlot = slot; }
    }
    return reference == null ? null : reference.racingType();
  }

  private static String label(String value) {
    return value.charAt(0) + value.substring(1).toLowerCase(Locale.ROOT);
  }
}
