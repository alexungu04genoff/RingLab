package dev.ringlab.adapter.in.rest.build.request;

import dev.ringlab.domain.build.recommendation.*;
import dev.ringlab.domain.gamedata.RacingType;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import java.math.BigDecimal;

public record BuildRecommendationRequest(@NotNull UUID gameVersionId, @NotNull RacingType machineType,
    @NotNull @Size(max = 5) List<@NotNull StatPriority> priorities,
    @NotNull @Valid Selection current, @NotNull @Valid Selection locked,
    RecommendationMode mode, @Valid Balanced balanced, GadgetRecommendationScope gadgetScope) {
  /** Removed objective fields must not silently change a client's requested calculation. */
  @JsonAnySetter
  public void rejectUnknownField(String name, Object value) {
    throw new IllegalArgumentException("Unsupported recommendation field: " + name
        + ". Recommendations use passive stats only; use Scenario Preview for race-state effects.");
  }
  public record Balanced(@NotNull Map<StatPriority, @NotNull BigDecimal> maximumLossPercent,
      @NotNull @JsonAlias("secondary") List<@NotNull StatPriority> ignored) {
    BalancedConfiguration toDomain() { return new BalancedConfiguration(maximumLossPercent, ignored); }
  }
  public record Selection(UUID racerId, UUID frontPartId, UUID rearPartId, UUID tirePartId,
      @NotNull @Size(max = 6) List<@NotNull UUID> gadgetIds) {
    BuildSelection toDomain() { return new BuildSelection(racerId, frontPartId, rearPartId, tirePartId, gadgetIds); }
  }

  public RecommendationRequest toDomain() {
    return new RecommendationRequest(gameVersionId, machineType, priorities,
        current == null ? null : current.toDomain(), locked == null ? null : locked.toDomain(),
        mode, balanced == null ? null : balanced.toDomain(), gadgetScope);
  }
}
