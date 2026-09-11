package dev.ringlab.application;

import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.application.build.BuildService;
import dev.ringlab.application.vote.VoteService;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.vote.Vote;
import dev.ringlab.port.out.BuildRepository;
import dev.ringlab.port.out.VoteRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VoteServiceTest {
  private final UUID userId = UUID.randomUUID();
  private final UUID buildId = UUID.randomUUID();
  private InMemoryBuildRepository builds;
  private VoteRepositoryStub votes;
  private VoteService service;

  @BeforeEach
  void setUp() {
    builds = new InMemoryBuildRepository();
    builds.builds.put(buildId, build(buildId));
    votes = new VoteRepositoryStub();
    service = new VoteService(votes, new BuildService(builds, null));
  }

  @Test
  void scoreDelegatesToRepository() {
    votes.score = 7;

    assertEquals(7, service.score(buildId));
  }

  @Test
  void acceptsUpvoteAndDownvote() {
    VoteService.Result upvote = service.put(userId, buildId, 1);
    assertEquals(new Vote(userId, buildId, 1), votes.lastVote);
    assertEquals(new VoteService.Result(1, 1), upvote);

    VoteService.Result downvote = service.put(userId, buildId, -1);
    assertEquals(new Vote(userId, buildId, -1), votes.lastVote);
    assertEquals(new VoteService.Result(-1, -1), downvote);
  }

  @Test
  void rejectsValuesOtherThanUpvoteOrDownvote() {
    for (int value : List.of(0, 2, -2)) {
      AppException error =
          assertThrows(AppException.class, () -> service.put(userId, buildId, value));
      assertEquals(400, error.status);
    }
    assertNull(votes.lastVote);
  }

  @Test
  void rejectsVoteWhenBuildIsMissing() {
    UUID missingBuild = UUID.randomUUID();

    AppException error =
        assertThrows(AppException.class, () -> service.put(userId, missingBuild, 1));

    assertEquals(404, error.status);
    assertNull(votes.lastVote);
  }

  @Test
  void removesVoteAndReturnsUpdatedState() {
    service.put(userId, buildId, 1);

    VoteService.Result result = service.remove(userId, buildId);

    assertEquals(userId, votes.removedUserId);
    assertEquals(buildId, votes.removedBuildId);
    assertEquals(new VoteService.Result(0, 0), result);
  }

  private static Build build(UUID id) {
    UUID owner = UUID.randomUUID();
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    return new Build(id, "Build", "", owner, owner, owner, owner, owner, List.of(), now, now);
  }

  private static final class InMemoryBuildRepository implements BuildRepository {
    private final Map<UUID, Build> builds = new HashMap<>();

    @Override
    public Optional<Build> find(UUID id) {
      return Optional.ofNullable(builds.get(id));
    }

    @Override
    public Page list(Filter filter) {
      return new Page(List.copyOf(builds.values()), builds.size());
    }

    @Override
    public void save(Build build) {
      builds.put(build.id(), build);
    }

    @Override
    public void delete(UUID id) {
      builds.remove(id);
    }
  }

  private static final class VoteRepositoryStub implements VoteRepository {
    private Vote lastVote;
    private UUID removedUserId;
    private UUID removedBuildId;
    private long score;
    private int value;

    @Override
    public void put(Vote vote) {
      lastVote = vote;
      score = vote.value();
      value = vote.value();
    }

    @Override
    public void remove(UUID userId, UUID buildId) {
      removedUserId = userId;
      removedBuildId = buildId;
      score = 0;
      value = 0;
    }

    @Override
    public long score(UUID buildId) {
      return score;
    }

    @Override
    public int value(UUID userId, UUID buildId) {
      return value;
    }
  }
}
