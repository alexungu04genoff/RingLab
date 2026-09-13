package dev.ringlab.port.out;

import dev.ringlab.domain.vote.Vote;
import dev.ringlab.domain.vote.VoteSummary;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface VoteRepository {
  /** Atomically inserts or replaces the vote for (userId, buildId). Repeated and
   * concurrent writes must never create more than one vote for that pair.
   */
  void put(Vote vote);

  /** Removes this user's vote; absence is a no-op. */
  void remove(UUID userId, UUID buildId);

  /** Raw up/down counts; an unvoted build returns (0, 0). */
  VoteSummary summary(UUID buildId);

  /** Raw counts only for requested IDs. Unvoted builds may be omitted; callers
   * must interpret missing entries as (0, 0). Empty input returns an empty map.
   */
  Map<UUID, VoteSummary> summaries(Collection<UUID> buildIds);

  /** Returns -1 or +1 for the stored vote, or 0 when no vote exists. */
  int value(UUID userId, UUID buildId);
}
