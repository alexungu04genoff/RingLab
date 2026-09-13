package dev.ringlab.application;

import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.application.build.BuildService;
import dev.ringlab.application.comment.CommentService;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.comment.Comment;
import dev.ringlab.domain.vote.Vote;
import dev.ringlab.domain.vote.VoteSummary;
import dev.ringlab.port.out.BuildRepository;
import dev.ringlab.port.out.CommentRepository;
import dev.ringlab.port.out.VoteRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CommentServiceTest {
  private final UUID buildId = UUID.randomUUID();
  private final UUID authorId = UUID.randomUUID();
  private InMemoryBuildRepository builds;
  private InMemoryCommentRepository comments;
  private CommentService service;
  private StubProfanityPolicy profanity;

  @BeforeEach
  void setUp() {
    builds = new InMemoryBuildRepository();
    builds.builds.put(buildId, build(buildId));
    comments = new InMemoryCommentRepository();
    profanity = new StubProfanityPolicy();
    service = new CommentService(
        comments, new BuildService(builds, null, new EmptyVoteRepository(), profanity), profanity);
  }

  @Test
  void listValidatesBuildAndReturnsRepositoryPage() {
    Comment comment = comment(UUID.randomUUID(), authorId, "Text");
    comments.comments.put(comment.id(), comment);

    assertEquals(new CommentRepository.Page(List.of(comment), 1), service.list(buildId, 2, 5));
    assertEquals(buildId, comments.listedBuildId);
    assertEquals(2, comments.listedPage);
    assertEquals(5, comments.listedSize);

    assertThrows(NotFoundException.class, () -> service.list(UUID.randomUUID(), 0, 20));
  }

  @Test
  void createTrimsTextAndUsesExpectedBuildAndAuthor() {
    Comment created = service.create(buildId, authorId, "  Useful setup  ");

    assertSame(created, comments.lastCreated);
    assertEquals(buildId, created.buildId());
    assertEquals(authorId, created.authorId());
    assertEquals("Useful setup", created.text());
    assertNotNull(created.id());
    assertNotNull(created.createdAt());
  }

  @Test
  void rejectsProfaneCommentBeforePersistence() {
    profanity.blocked.add("Blocked comment");

    var error = assertThrows(ValidationException.class,
        () -> service.create(buildId, authorId, "Blocked comment"));

    assertEquals("Text contains inappropriate language", error.getMessage());
    assertNull(comments.lastCreated);
    assertEquals(List.of(new StubProfanityPolicy.Check("Blocked comment", null)), profanity.checks);
  }

  @Test
  void authorCanDeleteOwnComment() {
    Comment comment = comment(UUID.randomUUID(), authorId, "Text");
    comments.comments.put(comment.id(), comment);

    service.delete(comment.id(), authorId);

    assertEquals(comment.id(), comments.deletedId);
  }

  @Test
  void anotherUserCannotDeleteComment() {
    Comment comment = comment(UUID.randomUUID(), authorId, "Text");
    comments.comments.put(comment.id(), comment);

    ForbiddenException error =
        assertThrows(ForbiddenException.class, () -> service.delete(comment.id(), UUID.randomUUID()));

    assertEquals("Only the author may change this resource", error.getMessage());
    assertNull(comments.deletedId);
  }

  @Test
  void missingCommentCannotBeDeleted() {
    NotFoundException error =
        assertThrows(NotFoundException.class, () -> service.delete(UUID.randomUUID(), authorId));

    assertEquals("Comment not found", error.getMessage());
  }

  @Test
  void rejectsNullBlankAndOversizedCommentsWithoutSaving() {
    for (String text : Arrays.asList(null, "", " \t\n", "x".repeat(2001))) {
      assertThrows(ValidationException.class, () -> service.create(buildId, authorId, text));
    }
    assertThrows(ValidationException.class, () -> service.create(buildId, null, "Text"));
    assertThrows(ValidationException.class, () -> service.create(null, authorId, "Text"));
    assertNull(comments.lastCreated);
    assertEquals("x".repeat(2000), service.create(buildId, authorId, "x".repeat(2000)).text());
  }

  @Test
  void rejectsInvalidPaginationBeforeRepositoryCall() {
    for (int[] bounds : List.of(new int[] {-1, 20}, new int[] {0, 0}, new int[] {0, 51})) {
      assertThrows(ValidationException.class, () -> service.list(buildId, bounds[0], bounds[1]));
    }
    assertNull(comments.listedBuildId);
    assertDoesNotThrow(() -> service.list(buildId, 0, 50));
  }

  private Comment comment(UUID id, UUID author, String text) {
    return new Comment(id, buildId, author, text, Instant.parse("2026-01-01T00:00:00Z"));
  }

  private static Build build(UUID id) {
    UUID owner = UUID.randomUUID();
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    return new Build(id, "Build", "", owner, owner, owner, owner, owner, null, null, List.of(), now, now);
  }

  private static final class InMemoryBuildRepository implements BuildRepository {
    private final Map<UUID, Build> builds = new HashMap<>();

    @Override
    public Optional<Build> find(UUID id) {
      return Optional.ofNullable(builds.get(id));
    }

    @Override
    public List<dev.ringlab.domain.build.ranking.BuildRanking.Candidate> searchCandidates(Filter filter) {
      return builds.values().stream().map(build ->
          new dev.ringlab.domain.build.ranking.BuildRanking.Candidate(build.id(), build.createdAt())).toList();
    }

    @Override
    public List<Build> findAll(Collection<UUID> ids) {
      return ids.stream().map(builds::get).filter(Objects::nonNull).toList();
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

  private static final class EmptyVoteRepository implements VoteRepository {
    public void put(Vote vote) {}
    public void remove(UUID userId, UUID buildId) {}
    public VoteSummary summary(UUID buildId) { return new VoteSummary(0, 0); }
    public Map<UUID, VoteSummary> summaries(Collection<UUID> buildIds) { return Map.of(); }
    public int value(UUID userId, UUID buildId) { return 0; }
  }

  private static final class InMemoryCommentRepository implements CommentRepository {
    private final Map<UUID, Comment> comments = new LinkedHashMap<>();
    private Comment lastCreated;
    private UUID listedBuildId;
    private int listedPage;
    private int listedSize;
    private UUID deletedId;

    @Override
    public Page list(UUID buildId, int page, int size) {
      listedBuildId = buildId;
      listedPage = page;
      listedSize = size;
      var matching = comments.values().stream().filter(c -> c.buildId().equals(buildId)).toList();
      return new Page(matching, matching.size());
    }

    @Override
    public Optional<Comment> find(UUID id) {
      return Optional.ofNullable(comments.get(id));
    }

    @Override
    public void create(Comment comment) {
      lastCreated = comment;
      comments.put(comment.id(), comment);
    }

    @Override
    public void delete(UUID id) {
      deletedId = id;
      comments.remove(id);
    }
  }
}
