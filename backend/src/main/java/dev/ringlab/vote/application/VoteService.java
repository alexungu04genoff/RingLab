package dev.ringlab.vote.application;

import dev.ringlab.build.application.BuildService;
import dev.ringlab.shared.application.AppException;
import dev.ringlab.vote.application.port.out.VoteStore;
import dev.ringlab.vote.domain.Vote;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.UUID;

@ApplicationScoped
public class VoteService {
  public record Result(long score, int myVote) {}

  private final VoteStore votes;
  private final BuildService builds;

  public VoteService(VoteStore votes, BuildService builds) {
    this.votes = votes;
    this.builds = builds;
  }

  public long score(UUID build) {
    return votes.score(build);
  }

  public Result get(UUID user, UUID build) {
    builds.get(build);
    return new Result(votes.score(build), votes.value(user, build));
  }

  @Transactional
  public Result put(UUID user, UUID build, int value) {
    builds.get(build);
    if (value != 1 && value != -1) throw new AppException(400, "Vote must be -1 or 1");
    votes.put(new Vote(user, build, value));
    return get(user, build);
  }

  @Transactional
  public Result remove(UUID user, UUID build) {
    builds.get(build);
    votes.remove(user, build);
    return get(user, build);
  }
}
