package dev.ringlab.domain.build.recommendation;

import java.util.List;
import java.util.UUID;

/** Also describes an incomplete draft or the selected IDs that are locked. */
public record BuildSelection(UUID racerId, UUID frontPartId, UUID rearPartId, UUID tirePartId,
    List<UUID> gadgetIds) {
  public BuildSelection {
    if (gadgetIds == null || gadgetIds.stream().anyMatch(java.util.Objects::isNull))
      throw new IllegalArgumentException("Gadget IDs must be supplied and cannot be null");
    gadgetIds = List.copyOf(gadgetIds);
  }
}
