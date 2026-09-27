package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.gamedata.RacingType;
import java.util.*;

public record RecommendationRequest(UUID gameVersionId, RacingType machineType,
    List<StatPriority> priorities, BuildSelection current, BuildSelection locked) {
  public RecommendationRequest {
    if (gameVersionId == null) throw new IllegalArgumentException("Select a game version / patch");
    if (machineType == null) throw new IllegalArgumentException("Choose a machine type");
    if (priorities == null || priorities.size() != StatPriority.values().length
        || priorities.stream().anyMatch(Objects::isNull) || new HashSet<>(priorities).size() != StatPriority.values().length)
      throw new IllegalArgumentException("Priorities must contain every stat exactly once");
    priorities = List.copyOf(priorities);
    if (current == null || locked == null) throw new IllegalArgumentException("Current selections and locks are required");
  }
}
