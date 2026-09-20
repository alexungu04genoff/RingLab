package dev.ringlab.domain.gamedata;

import java.util.List;

/** RingLab's current CrossWorlds machine policy; never applied to racer types. */
public final class MachineComposition {
  private MachineComposition() {}

  public static List<MachinePartType> requiredSlots(RacingType machineType) {
    if (machineType == null) throw new IllegalArgumentException("Machine racing type is unknown");
    return machineType == RacingType.BOOST
        ? List.of(MachinePartType.FRONT, MachinePartType.REAR)
        : List.of(MachinePartType.FRONT, MachinePartType.REAR, MachinePartType.TIRE);
  }
}
