package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.gamedata.RacingType;
import dev.ringlab.domain.gamedata.ScenarioContext;
import java.util.*;

public record RecommendationRequest(UUID gameVersionId, RacingType machineType,
    List<StatPriority> priorities, BuildSelection current, BuildSelection locked,
    RecommendationMode mode, BalancedConfiguration balanced, RecommendationBasis basis, ScenarioContext scenario) {
  public RecommendationRequest(UUID gameVersionId, RacingType machineType, List<StatPriority> priorities,
      BuildSelection current, BuildSelection locked, RecommendationMode mode, BalancedConfiguration balanced) {
    this(gameVersionId, machineType, priorities, current, locked, mode, balanced, RecommendationBasis.PASSIVE, null);
  }
  public RecommendationRequest(UUID gameVersionId, RacingType machineType, List<StatPriority> priorities,
      BuildSelection current, BuildSelection locked) {
    this(gameVersionId, machineType, priorities, current, locked, RecommendationMode.STRICT, null);
  }

  public RecommendationRequest {
    if (basis == null) throw new IllegalArgumentException("Recommendation basis is required");
    if (basis == RecommendationBasis.PASSIVE && scenario != null)
      throw new IllegalArgumentException("PASSIVE does not accept a scenario");
    if (basis == RecommendationBasis.CURRENT_SCENARIO && scenario == null)
      throw new IllegalArgumentException("CURRENT_SCENARIO requires a scenario");
    if (gameVersionId == null) throw new IllegalArgumentException("Select a game version / patch");
    if (machineType == null) throw new IllegalArgumentException("Choose a machine type");
    mode = mode == null ? RecommendationMode.STRICT : mode;
    if (priorities == null || priorities.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Priorities must contain every stat exactly once");
    if (mode == RecommendationMode.STRICT) {
      if (priorities.size() != 5 || new HashSet<>(priorities).size() != 5)
        throw new IllegalArgumentException("Priorities must contain every stat exactly once");
    } else {
      if (balanced == null) throw new IllegalArgumentException("Balanced configuration is required");
      balanced.validate(priorities);
    }
    priorities = List.copyOf(priorities);
    if (current == null || locked == null) throw new IllegalArgumentException("Current selections and locks are required");
  }
}
