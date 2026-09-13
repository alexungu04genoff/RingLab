package dev.ringlab.port.out;

import dev.ringlab.domain.vote.Vote;
import dev.ringlab.domain.vote.VoteSummary;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface VoteRepository {
  /** Atomically inserts or replaces the user's single vote for the build. */
  void put(Vote vote);

  /** Removes the user's vote when present; absence is a no-op. */
  void remove(UUID userId, UUID buildId);

  /** Returns raw upvote and downvote counts, or zero counts when the build has no votes. */
  VoteSummary summary(UUID buildId);

  /**
   * Returns raw summaries only for build IDs that have votes; omitted IDs mean zero votes. No
   * ranking policy is applied.
   */
  Map<UUID, VoteSummary> summaries(Collection<UUID> buildIds);

  int value(UUID userId, UUID buildId);
}
