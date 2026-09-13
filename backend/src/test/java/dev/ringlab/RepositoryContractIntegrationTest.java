package dev.ringlab;

import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.application.AlreadyExistsException;
import dev.ringlab.domain.auth.User;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.comment.Comment;
import dev.ringlab.domain.gamedata.MachinePartType;
import dev.ringlab.domain.vote.Vote;
import dev.ringlab.domain.vote.VoteSummary;
import dev.ringlab.port.out.*;
import dev.ringlab.adapter.out.db.gamedata.GameVersionDbEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RepositoryContractIntegrationTest {
  @Inject EntityManager em;
  @Inject BuildRepository builds;
  @Inject CommentRepository comments;
  @Inject VoteRepository votes;
  @Inject UserRepository users;
  @Inject GameDataRepository game;

  @Test
  @TestTransaction
  void deletingBuildRemovesRelationsAndClearsOnlyRemixProvenance() {
    User author = user();
    Build source = build(author.id(), "Source", null);
    Build remix = build(author.id(), "Remix", source.id());
    votes.put(new Vote(author.id(), source.id(), 1));
    votes.put(new Vote(author.id(), remix.id(), -1));
    comments.create(new Comment(UUID.randomUUID(), source.id(), author.id(), "Source comment", Instant.now()));
    comments.create(new Comment(UUID.randomUUID(), remix.id(), author.id(), "Remix comment", Instant.now()));
    em.flush();

    builds.delete(source.id());
    em.flush();
    em.clear();

    assertTrue(builds.find(source.id()).isEmpty());
    assertEquals(0, rows("votes", source.id()));
    assertEquals(0, rows("comments", source.id()));
    assertEquals(0, rows("build_gadgets", source.id()));
    Build remaining = builds.find(remix.id()).orElseThrow();
    assertEquals(new Build(remix.id(), remix.title(), remix.description(), remix.authorId(),
        remix.racerId(), remix.frontPartId(), remix.rearPartId(), remix.tirePartId(),
        remix.gameVersionId(), null, remix.gadgetIds(), remix.createdAt(), remix.updatedAt()), remaining);
    assertEquals(1, rows("votes", remix.id()));
    assertEquals(1, rows("comments", remix.id()));
    assertEquals(remix.gadgetIds().size(), rows("build_gadgets", remix.id()));
  }

  @Test
  @TestTransaction
  void searchIsLiteralIgnoresBlankAndMatchesAnySelectedSourcePart() {
    User author = user();
    var machines = game.listMachines();
    UUID target = machines.get(0).id();
    UUID other = machines.get(1).id();
    Set<UUID> expected = new HashSet<>();
    for (MachinePartType slot : MachinePartType.values()) {
      Build base = build(author.id(), "Literal %_ MiXeD", null);
      Build mixed = new Build(base.id(), base.title(), base.description(), author.id(), base.racerId(),
          part(slot == MachinePartType.FRONT ? target : other, MachinePartType.FRONT),
          part(slot == MachinePartType.REAR ? target : other, MachinePartType.REAR),
          part(slot == MachinePartType.TIRE ? target : other, MachinePartType.TIRE),
          null, null, base.gadgetIds(), base.createdAt(), base.updatedAt());
      builds.save(mixed);
      expected.add(mixed.id());
    }
    Build decoy = build(author.id(), "Literal XX mixed", null);
    em.flush();
    assertEquals(expected, ids(builds.search(new BuildRepository.Filter("%_ mixed", null, target, author.id(), null))));
    for (String blank : List.of("", " \t\n")) {
      Set<UUID> all = new HashSet<>(expected);
      all.add(decoy.id());
      assertEquals(all, ids(builds.search(new BuildRepository.Filter(blank, null, null, author.id(), null))));
    }
  }

  @Test
  @TestTransaction
  void commentsOrderBeforePaginationAndRetainTotalOnEmptyPages() {
    User author = user();
    Build build = build(author.id(), "Comments", null);
    Instant time = Instant.parse("2026-01-01T00:00:00Z");
    UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID second = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    UUID newest = UUID.randomUUID();
    comments.create(new Comment(second, build.id(), author.id(), "Second", time));
    comments.create(new Comment(newest, build.id(), author.id(), "Newest", time.plusSeconds(1)));
    comments.create(new Comment(first, build.id(), author.id(), "First", time));
    em.flush();
    assertEquals(List.of(first, second), comments.list(build.id(), 0, 2).items().stream().map(Comment::id).toList());
    assertEquals(List.of(newest), comments.list(build.id(), 1, 2).items().stream().map(Comment::id).toList());
    var empty = comments.list(build.id(), 2, 2);
    assertTrue(empty.items().isEmpty());
    assertEquals(3, empty.total());
  }

  @Test
  @TestTransaction
  void gameVersionsUseReleaseDateThenUnsignedUuidOrder() {
    UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID second = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    UUID newest = UUID.randomUUID();
    version(second, "contract-second", LocalDate.of(2100, 1, 1));
    version(first, "contract-first", LocalDate.of(2100, 1, 1));
    version(newest, "contract-newest", LocalDate.of(2101, 1, 1));
    em.flush();
    assertEquals(List.of(newest, first, second), game.listGameVersions().stream().limit(3).map(v -> v.id()).toList());
  }

  @Test
  @TestTransaction
  void voteReplacementRemovalAndSummaryConventionsMatchPort() {
    User author = user();
    Build voted = build(author.id(), "Voted", null);
    Build unvoted = build(author.id(), "Unvoted", null);
    assertEquals(0, votes.value(author.id(), voted.id()));
    assertEquals(new VoteSummary(0, 0), votes.summary(unvoted.id()));
    assertTrue(votes.summaries(List.of()).isEmpty());
    votes.put(new Vote(author.id(), voted.id(), 1));
    votes.put(new Vote(author.id(), voted.id(), 1));
    votes.put(new Vote(author.id(), voted.id(), -1));
    assertEquals(1, rows("votes", voted.id()));
    assertEquals(-1, votes.value(author.id(), voted.id()));
    assertEquals(new VoteSummary(0, 1), votes.summary(voted.id()));
    var summaries = votes.summaries(List.of(voted.id(), unvoted.id()));
    assertEquals(new VoteSummary(0, 1), summaries.get(voted.id()));
    assertEquals(new VoteSummary(0, 0), summaries.getOrDefault(unvoted.id(), new VoteSummary(0, 0)));
    assertTrue(votes.summaries(List.of(unvoted.id())).keySet().stream().allMatch(unvoted.id()::equals));
    votes.remove(author.id(), voted.id());
    votes.remove(author.id(), voted.id());
    assertEquals(0, rows("votes", voted.id()));
    assertEquals(0, votes.value(author.id(), voted.id()));
  }

  @Test
  @TestTransaction
  void usernameConstraintHasDuplicateAccountMeaning() {
    User original = user();
    assertThrows(AlreadyExistsException.class, () -> users.create(new User(UUID.randomUUID(),
        original.username(), "other@example.test", "unused", Instant.now())));
  }

  @Test
  void concurrentVoteWritesLeaveExactlyOneVote() throws Exception {
    Build target = QuarkusTransaction.requiringNew().call(() -> build(user().id(), "Concurrent votes", null));
    try {
      var ready = new CyclicBarrier(2);
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var up = executor.submit(() -> {
          ready.await(10, TimeUnit.SECONDS);
          QuarkusTransaction.requiringNew().timeout(10)
              .run(() -> votes.put(new Vote(target.authorId(), target.id(), 1)));
          return null;
        });
        var down = executor.submit(() -> {
          ready.await(10, TimeUnit.SECONDS);
          QuarkusTransaction.requiringNew().timeout(10)
              .run(() -> votes.put(new Vote(target.authorId(), target.id(), -1)));
          return null;
        });
        up.get(20, TimeUnit.SECONDS);
        down.get(20, TimeUnit.SECONDS);
      }
      QuarkusTransaction.requiringNew().run(() -> {
        assertEquals(1, rows("votes", target.id()));
        int value = votes.value(target.authorId(), target.id());
        assertTrue(value == 1 || value == -1);
        assertEquals(value == 1 ? new VoteSummary(1, 0) : new VoteSummary(0, 1), votes.summary(target.id()));
      });
    } finally {
      QuarkusTransaction.requiringNew().run(() -> {
        builds.delete(target.id());
        em.flush();
        em.createQuery("delete UserDbEntity where id = :id")
            .setParameter("id", target.authorId()).executeUpdate();
      });
    }
  }

  @Test
  @TestTransaction
  void emailConstraintHasDuplicateAccountMeaning() {
    User original = user();
    assertThrows(AlreadyExistsException.class, () -> users.create(new User(UUID.randomUUID(),
        "other_account", original.email(), "unused", Instant.now())));
  }

  private User user() {
    String name = "contract_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    User user = new User(UUID.randomUUID(), name, name + "@example.test", "unused", Instant.now());
    users.create(user);
    return user;
  }

  private Build build(UUID author, String title, UUID source) {
    UUID machine = game.listMachines().getFirst().id();
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    Build build = new Build(UUID.randomUUID(), title, "Description", author, game.listRacers().getFirst().id(),
        part(machine, MachinePartType.FRONT), part(machine, MachinePartType.REAR), part(machine, MachinePartType.TIRE),
        null, source, List.of(game.listGadgets().getFirst().id()), now, now);
    builds.save(build);
    em.flush();
    return build;
  }

  private UUID part(UUID source, MachinePartType type) {
    return game.listMachineParts().stream().filter(p -> p.sourceMachineId().equals(source) && p.type() == type)
        .findFirst().orElseThrow().id();
  }

  private Set<UUID> ids(List<Build> result) {
    return result.stream().map(Build::id).collect(java.util.stream.Collectors.toSet());
  }

  private long rows(String table, UUID build) {
    // Table names are fixed test literals, never user input.
    return ((Number) em.createNativeQuery("select count(*) from " + table + " where build_id = :build")
        .setParameter("build", build).getSingleResult()).longValue();
  }

  private void version(UUID id, String name, LocalDate releasedAt) {
    var entity = new GameVersionDbEntity();
    entity.id = id;
    entity.version = name;
    entity.releasedAt = releasedAt;
    em.persist(entity);
  }
}
