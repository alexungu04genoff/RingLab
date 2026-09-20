package dev.ringlab.application.community;

import dev.ringlab.application.ValidationException;
import dev.ringlab.application.gamedata.MachineCompatibility;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.build.GadgetPlate;
import dev.ringlab.domain.gamedata.*;
import java.util.*;

/** Read-only selection policy; normal browsing deliberately does not use these exclusions. */
public final class CommunityEligibility {
  private CommunityEligibility() {}
  private static final List<String> DEMO_PREFIXES = List.of(
      "[Wilson Demo]", "[Comment Demo]", "[Pagination Demo]", "[Compare Demo]",
      "[Remix Demo]", "[Machine Demo]", "[Gadget Demo]", "[Ownership Demo]",
      "[Vote Demo]", "[Version Demo]", "[DEMO]");

  public static boolean controlledDemo(String title) {
    return DEMO_PREFIXES.stream().anyMatch(title::startsWith);
  }

  public static boolean valid(Build b, Map<UUID, MachinePart> parts,
      Map<UUID, Machine> machines, Map<UUID, Gadget> gadgets) {
    var front = parts.get(b.frontPartId());
    var rear = parts.get(b.rearPartId());
    var tire = b.tirePartId() == null ? null : parts.get(b.tirePartId());
    if (front == null || front.type() != MachinePartType.FRONT
        || rear == null || rear.type() != MachinePartType.REAR) return false;
    try {
      var type = MachineCompatibility.requireCompatible(id -> Optional.ofNullable(machines.get(id)), front, rear, tire);
      boolean needsTire = MachineComposition.requiredSlots(type).contains(MachinePartType.TIRE);
      if (needsTire ? tire == null || tire.type() != MachinePartType.TIRE : b.tirePartId() != null) return false;
    } catch (ValidationException invalid) {
      return false;
    }
    if (b.gadgetIds().size() > GadgetPlate.TOTAL_CAPACITY
        || new HashSet<>(b.gadgetIds()).size() != b.gadgetIds().size()) return false;
    var costs = new ArrayList<Integer>();
    for (var id : b.gadgetIds()) {
      var gadget = gadgets.get(id);
      if (gadget == null) return false;
      costs.add(gadget.slotCost());
    }
    return GadgetPlate.canFit(costs);
  }
}
