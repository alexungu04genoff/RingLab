package dev.ringlab;

import static org.junit.jupiter.api.Assertions.*;
import dev.ringlab.application.NotFoundException;
import dev.ringlab.application.build.BuildService;
import dev.ringlab.domain.auth.User;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.comment.Comment;
import dev.ringlab.domain.gamedata.MachinePartType;
import dev.ringlab.domain.vote.Vote;
import dev.ringlab.port.out.*;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ParentDeletionIntegrationTest {
  @Inject BuildRepository builds;
  @Inject BuildService buildService;
  @Inject CommentRepository comments;
  @Inject VoteRepository votes;
  @Inject UserRepository users;
  @Inject GameDataRepository game;
  @Inject EntityManager em;

  @Test
  void parentDeletionRacesBecomeNotFoundAndRollBackEveryWrite() throws Exception {
    for (String operation : List.of("vote", "comment", "remix")) {
      UUID author = UUID.randomUUID();
      Build parent = QuarkusTransaction.requiringNew().call(() -> {
        String name = "race_" + author.toString().substring(0, 20).replace("-", "");
        users.create(new User(author, name, name + "@example.test", "unused", Instant.now()));
        return saveBuild(author, null);
      });
      Build survivor = QuarkusTransaction.requiringNew().call(() -> saveBuild(author, null));
      UUID marker = UUID.randomUUID();
      UUID attempted = UUID.randomUUID();
      var checked = new CountDownLatch(1);
      var deleted = new CountDownLatch(1);
      try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var writer = executor.submit(() -> {
          try {
            QuarkusTransaction.requiringNew().timeout(20).run(() -> {
              buildService.get(parent.id());
              comments.create(new Comment(marker, survivor.id(), author, "Rollback marker", Instant.now()));
              checked.countDown();
              await(deleted);
              switch (operation) {
                case "vote" -> votes.put(new Vote(author, parent.id(), 1));
                case "comment" -> comments.create(new Comment(attempted, parent.id(), author, "Racing comment", Instant.now()));
                case "remix" -> builds.save(new Build(attempted, "Racing remix", "", author,
                    parent.racerId(), parent.frontPartId(), parent.rearPartId(), parent.tirePartId(),
                    null, parent.id(), List.of(), Instant.now(), Instant.now()));
                default -> throw new AssertionError(operation);
              }
            });
            return null;
          } catch (RuntimeException exception) {
            return exception;
          }
        });
        assertTrue(checked.await(10, TimeUnit.SECONDS), "Writer must reach the existence check");
        QuarkusTransaction.requiringNew().run(() -> builds.delete(parent.id()));
        deleted.countDown();
        Throwable failure = writer.get(15, TimeUnit.SECONDS);
        assertNotNull(failure, operation + " must fail");
        while (!(failure instanceof NotFoundException) && failure.getCause() != null) failure = failure.getCause();
        assertInstanceOf(NotFoundException.class, failure);
        QuarkusTransaction.requiringNew().run(() -> {
          assertTrue(comments.find(marker).isEmpty(), "Earlier transaction writes must roll back");
          assertTrue(comments.find(attempted).isEmpty());
          assertTrue(builds.find(attempted).isEmpty());
          assertEquals(0, votes.value(author, parent.id()));
          assertTrue(builds.find(parent.id()).isEmpty());
          assertTrue(builds.find(survivor.id()).isPresent());
        });
      } finally {
        deleted.countDown();
        QuarkusTransaction.requiringNew().run(() -> {
          em.createQuery("delete BuildDbEntity where authorId=:author").setParameter("author", author).executeUpdate();
          em.createQuery("delete UserDbEntity where id=:author").setParameter("author", author).executeUpdate();
        });
      }
    }
  }

  private void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Deletion did not complete");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }

  private Build saveBuild(UUID author, UUID source) {
    Build build = new Build(UUID.randomUUID(), "Race fixture", "", author,
        game.listRacers().getFirst().id(), part(MachinePartType.FRONT), part(MachinePartType.REAR),
        part(MachinePartType.TIRE), null, source, List.of(), Instant.now(), Instant.now());
    builds.save(build);
    return build;
  }

  private UUID part(MachinePartType type) {
    return game.listMachineParts().stream().filter(part -> part.type() == type).findFirst().orElseThrow().id();
  }
}
