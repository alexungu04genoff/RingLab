package dev.ringlab.port.out;

import dev.ringlab.domain.vote.Vote;
import java.util.UUID;

public interface VoteRepository {
  void put(Vote vote);

  void remove(UUID userId, UUID buildId);

  long score(UUID buildId);

  int value(UUID userId, UUID buildId);
}
