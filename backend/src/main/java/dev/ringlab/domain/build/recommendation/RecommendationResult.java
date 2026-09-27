package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.gamedata.BaseStats;
import java.util.List;

public record RecommendationResult(Outcome outcome, BuildSelection selection, BaseStats currentStats,
    BaseStats recommendedStats, boolean alreadyBest, String reason, List<String> restrictions,
    String ruleset, String note, long work, long elapsedMillis) {
  public enum Outcome { ESTABLISHED, BEST_FOUND, NO_LEGAL_COMPLETION, UNAVAILABLE, LIMIT_WITHOUT_CANDIDATE }
  public RecommendationResult { restrictions = List.copyOf(restrictions); }
}
