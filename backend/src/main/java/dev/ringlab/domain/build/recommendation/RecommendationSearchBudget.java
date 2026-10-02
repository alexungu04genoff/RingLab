package dev.ringlab.domain.build.recommendation;

import java.util.function.LongSupplier;

/** One step is charged only after all limits pass; the deadline starts at solver construction. */
final class RecommendationSearchBudget {
  private final BuildRecommendationSolver.Budget budget;
  private final LongSupplier clock;
  private final long started;
  private long work;

  RecommendationSearchBudget(BuildRecommendationSolver.Budget budget, LongSupplier clock) {
    this.budget = budget;
    this.clock = clock;
    this.started = clock.getAsLong();
  }

  void step() {
    if (work >= budget.maxWork() || clock.getAsLong() - started >= budget.time().toNanos() || Thread.currentThread().isInterrupted())
      throw new SearchLimit();
    work++;
  }

  long work() { return work; }
  long elapsedMillis() { return Math.max(0, (clock.getAsLong() - started) / 1_000_000); }

  static final class SearchLimit extends RuntimeException {
    SearchLimit() { super(null, null, false, false); }
  }
}
