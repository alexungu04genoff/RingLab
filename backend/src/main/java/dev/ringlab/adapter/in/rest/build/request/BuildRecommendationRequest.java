package dev.ringlab.adapter.in.rest.build.request;

import dev.ringlab.domain.build.recommendation.*;
import dev.ringlab.domain.gamedata.RacingType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;

public record BuildRecommendationRequest(@NotNull UUID gameVersionId, @NotNull RacingType machineType,
    @NotNull @Size(min = 5, max = 5) List<@NotNull StatPriority> priorities,
    @NotNull @Valid Selection current, @NotNull @Valid Selection locked) {
  public record Selection(UUID racerId, UUID frontPartId, UUID rearPartId, UUID tirePartId,
      @NotNull @Size(max = 6) List<@NotNull UUID> gadgetIds) {
    BuildSelection toDomain() { return new BuildSelection(racerId, frontPartId, rearPartId, tirePartId, gadgetIds); }
  }

  public RecommendationRequest toDomain() {
    return new RecommendationRequest(gameVersionId, machineType, priorities,
        current == null ? null : current.toDomain(), locked == null ? null : locked.toDomain());
  }
}
