package dev.ringlab.domain.build;

import java.time.Instant;
import java.util.*;

public record Build(
    UUID id,
    String title,
    String description,
    UUID authorId,
    UUID racerId,
    UUID frontPartId,
    UUID rearPartId,
    UUID tirePartId,
    UUID gameVersionId,
    List<UUID> gadgetIds,
    Instant createdAt,
    Instant updatedAt) {
  public Build {
    Objects.requireNonNull(frontPartId);
    Objects.requireNonNull(rearPartId);
    Objects.requireNonNull(tirePartId);
    gadgetIds = List.copyOf(gadgetIds);
  }
}
