package dev.ringlab.port.in;

import dev.ringlab.domain.vote.VoteSummary;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface VoteUseCase {
  record Result(long score, long upvotes, long downvotes, int myVote) {}
  VoteSummary summary(UUID build);
  Map<UUID, VoteSummary> summaries(Collection<UUID> builds);
  Result get(UUID user, UUID build);
  Result put(UUID user, UUID build, int value);
  Result remove(UUID user, UUID build);
}
