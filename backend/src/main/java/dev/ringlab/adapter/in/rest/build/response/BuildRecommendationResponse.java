package dev.ringlab.adapter.in.rest.build.response;

import dev.ringlab.adapter.in.rest.gamedata.response.StatsResponse;
import dev.ringlab.domain.build.recommendation.*;
import java.util.*;

public record BuildRecommendationResponse(RecommendationResult.Outcome outcome, Selection selection,
    StatsResponse currentStats, StatsResponse recommendedStats, boolean alreadyBest, String reason,
    List<String> restrictions, String ruleset, String note, long work, long elapsedMillis,
    RecommendationResult.BalancedDetails balanced) {
  public record Selection(UUID racerId, UUID frontPartId, UUID rearPartId, UUID tirePartId, List<UUID> gadgetIds) {}

  public static BuildRecommendationResponse from(RecommendationResult result) {
    var selection = result.selection();
    return new BuildRecommendationResponse(result.outcome(), selection == null ? null : new Selection(
        selection.racerId(), selection.frontPartId(), selection.rearPartId(), selection.tirePartId(), selection.gadgetIds()),
        result.currentStats() == null ? null : StatsResponse.from(result.currentStats()),
        result.recommendedStats() == null ? null : StatsResponse.from(result.recommendedStats()),
        result.alreadyBest(), result.reason(), result.restrictions(), result.ruleset(), result.note(), result.work(), result.elapsedMillis(), result.balanced());
  }
}
