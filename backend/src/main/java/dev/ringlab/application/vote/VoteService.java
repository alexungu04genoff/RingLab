package dev.ringlab.application.vote;

import dev.ringlab.port.in.VoteUseCase;

import lombok.RequiredArgsConstructor;

import dev.ringlab.application.build.BuildService;
import dev.ringlab.application.ValidationException;
import dev.ringlab.domain.vote.Vote;
import dev.ringlab.domain.vote.VoteSummary;
import dev.ringlab.port.out.VoteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class VoteService implements VoteUseCase {

  private final VoteRepository votes;
  private final BuildService builds;

  public VoteSummary summary(UUID build) {
    return votes.summary(build);
  }

  public java.util.Map<UUID, VoteSummary> summaries(java.util.Collection<UUID> builds) {
    return votes.summaries(builds);
  }

  public Result get(UUID user, UUID build) {
    builds.get(build);
    var summary = votes.summary(build);
    return new Result(summary.score(), summary.upvotes(), summary.downvotes(), votes.value(user, build));
  }

  @Transactional
  public Result put(UUID user, UUID build, int value) {
    builds.lockPublic(build);
    if (value != 1 && value != -1) throw new ValidationException("Vote must be -1 or 1");
    votes.put(new Vote(user, build, value));
    return get(user, build);
  }

  @Transactional
  public Result remove(UUID user, UUID build) {
    builds.lockPublic(build);
    votes.remove(user, build);
    return get(user, build);
  }
}
