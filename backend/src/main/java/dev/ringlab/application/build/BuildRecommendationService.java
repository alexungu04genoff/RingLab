package dev.ringlab.application.build;

import dev.ringlab.application.ExternalServiceUnavailableException;
import dev.ringlab.application.ValidationException;
import dev.ringlab.domain.build.recommendation.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class BuildRecommendationService {
  public static final BuildRecommendationSolver.Budget BUDGET = new BuildRecommendationSolver.Budget(100_000, Duration.ofSeconds(2));
  private final RecommendationCatalogLoader catalog;
  private final Semaphore calculations = new Semaphore(2);

  /** No transaction spans the CPU search, even when invoked from another transactional caller. */
  @Transactional(Transactional.TxType.NOT_SUPPORTED)
  public RecommendationResult recommend(RecommendationRequest request) {
    if (request == null) throw new ValidationException("Recommendation request is required");
    if (!calculations.tryAcquire())
      throw new ExternalServiceUnavailableException("Recommendation capacity is busy. Try again shortly.");
    try {
      var snapshot = catalog.load(request.gameVersionId());
      return new BuildRecommendationSolver(snapshot, request, BUDGET).solve();
    } catch (IllegalArgumentException invalid) {
      throw new ValidationException(invalid.getMessage());
    } finally {
      calculations.release();
    }
  }
}
