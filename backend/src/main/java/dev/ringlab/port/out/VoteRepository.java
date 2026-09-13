package dev.ringlab.port.out;

import dev.ringlab.domain.vote.Vote;
import dev.ringlab.domain.vote.VoteSummary;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface VoteRepository {
  void put(Vote vote);

  void remove(UUID userId, UUID buildId);

  VoteSummary summary(UUID buildId);

  Map<UUID, VoteSummary> summaries(Collection<UUID> buildIds);

  int value(UUID userId, UUID buildId);
}
