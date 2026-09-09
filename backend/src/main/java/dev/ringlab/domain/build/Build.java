package dev.ringlab.domain.build;

import java.time.Instant;
import java.util.*;

public record Build(
    UUID id,
    String title,
    String description,
    UUID authorId,
    UUID racerId,
    UUID machineId,
    List<UUID> gadgetIds,
    Instant createdAt,
    Instant updatedAt) {
  public Build {
    gadgetIds = List.copyOf(gadgetIds);
  }
}
