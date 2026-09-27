package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.gamedata.BaseStats;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

public record RecommendationResult(Outcome outcome, BuildSelection selection, BaseStats currentStats,
    BaseStats recommendedStats, boolean alreadyBest, String reason, List<String> restrictions,
    String ruleset, String note, long work, long elapsedMillis, BalancedDetails balanced) {
  public record BalancedDetails(Map<StatPriority, BigDecimal> minimum, boolean secondaryTieBreakDecided) {
    public BalancedDetails { minimum = Map.copyOf(minimum); }
  }
  public enum Outcome { ESTABLISHED, BEST_FOUND, NO_LEGAL_COMPLETION, NO_FEASIBLE_CANDIDATE, UNAVAILABLE, LIMIT_WITHOUT_CANDIDATE }
  public RecommendationResult { restrictions = List.copyOf(restrictions); }
}
