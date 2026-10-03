package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.gamedata.BaseStats;
import java.util.List;

public record RecommendationResult(Outcome outcome, BuildSelection selection, BaseStats currentStats,
    BaseStats recommendedStats, boolean alreadyBest, String reason, List<String> restrictions,
    String ruleset, String note, long work, long elapsedMillis, BalancedDetails balanced) {
  public record BalancedDetails(boolean proven, List<BalancedStage> stages) {
    public BalancedDetails { stages = List.copyOf(stages); }
  }
  public enum Outcome { ESTABLISHED, BEST_FOUND, NO_LEGAL_COMPLETION, NO_FEASIBLE_CANDIDATE, UNAVAILABLE, LIMIT_WITHOUT_CANDIDATE }
  public RecommendationResult { restrictions = List.copyOf(restrictions); }
}
