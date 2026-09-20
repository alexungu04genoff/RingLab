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
    UUID remixedFromBuildId,
    List<UUID> gadgetIds,
    Instant createdAt,
    Instant updatedAt) {
  public Build {
    Objects.requireNonNull(frontPartId);
    Objects.requireNonNull(rearPartId);
    gadgetIds = List.copyOf(gadgetIds);
  }
}
