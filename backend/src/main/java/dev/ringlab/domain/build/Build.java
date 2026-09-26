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
    Set<UUID> recommendedMapIds,
    Instant createdAt,
    Instant updatedAt) {
  public Build {
    Objects.requireNonNull(frontPartId);
    Objects.requireNonNull(rearPartId);
    gadgetIds = List.copyOf(gadgetIds);
    recommendedMapIds = Set.copyOf(recommendedMapIds);
  }

  /** Existing fixture/generator callers default to no map-specific preference. */
  public Build(UUID id, String title, String description, UUID authorId, UUID racerId,
      UUID frontPartId, UUID rearPartId, UUID tirePartId, UUID gameVersionId,
      UUID remixedFromBuildId, List<UUID> gadgetIds, Instant createdAt, Instant updatedAt) {
    this(id, title, description, authorId, racerId, frontPartId, rearPartId, tirePartId,
        gameVersionId, remixedFromBuildId, gadgetIds, Set.of(), createdAt, updatedAt);
  }
}
