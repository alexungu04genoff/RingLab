package dev.ringlab.vote.application.port.out;

import dev.ringlab.vote.domain.Vote;
import java.util.UUID;

public interface VoteStore {
  void put(Vote vote);

  void remove(UUID userId, UUID buildId);

  long score(UUID buildId);

  int value(UUID userId, UUID buildId);
}
