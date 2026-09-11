package dev.ringlab.application;

import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.application.build.BuildService;
import dev.ringlab.application.comment.CommentService;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.comment.Comment;
import dev.ringlab.port.out.BuildRepository;
import dev.ringlab.port.out.CommentRepository;
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

  @BeforeEach
  void setUp() {
    builds = new InMemoryBuildRepository();
    builds.builds.put(buildId, build(buildId));
    comments = new InMemoryCommentRepository();
    service = new CommentService(comments, new BuildService(builds, null));
  }

  @Test
  void listValidatesBuildAndReturnsRepositoryPage() {
    Comment comment = comment(UUID.randomUUID(), authorId, "Text");
    comments.comments.put(comment.id(), comment);

    assertEquals(new CommentRepository.Page(List.of(comment), 1), service.list(buildId, 2, 5));
    assertEquals(buildId, comments.listedBuildId);
    assertEquals(2, comments.listedPage);
    assertEquals(5, comments.listedSize);

    AppException error =
        assertThrows(AppException.class, () -> service.list(UUID.randomUUID(), 0, 20));
    assertEquals(404, error.status);
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

    AppException error =
        assertThrows(AppException.class, () -> service.delete(comment.id(), UUID.randomUUID()));

    assertEquals(403, error.status);
    assertNull(comments.deletedId);
  }

  @Test
  void missingCommentCannotBeDeleted() {
    AppException error =
        assertThrows(AppException.class, () -> service.delete(UUID.randomUUID(), authorId));

    assertEquals(404, error.status);
    assertEquals("Comment not found", error.getMessage());
  }

  private Comment comment(UUID id, UUID author, String text) {
    return new Comment(id, buildId, author, text, Instant.parse("2026-01-01T00:00:00Z"));
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
