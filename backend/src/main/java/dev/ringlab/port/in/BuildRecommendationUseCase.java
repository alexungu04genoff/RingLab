package dev.ringlab.port.in;

import dev.ringlab.domain.build.recommendation.RecommendationRequest;
import dev.ringlab.domain.build.recommendation.RecommendationResult;
import java.util.UUID;

public interface BuildRecommendationUseCase {
  RecommendationResult recommend(UUID actor, RecommendationRequest request);
}
