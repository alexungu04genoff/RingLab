package dev.ringlab;

import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.adapter.out.db.build.BuildDbEntity;
import dev.ringlab.adapter.out.db.vote.VoteDbEntity;
import dev.ringlab.domain.build.Build;
import dev.ringlab.port.out.BuildRepository;
import dev.ringlab.port.out.GameDataRepository;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BuildRankingIntegrationTest {
  @Inject EntityManager em;
  @Inject BuildRepository builds;
  @Inject GameDataRepository game;

  @Test
  @TestTransaction
  void ranksConfidenceBeforePaginationAndPreservesExistingSorts() {
    UUID author = user();
    UUID racer = game.listRacers().getFirst().id();
    UUID machine = game.listMachines().getFirst().id();
    var strong = build(author, racer, machine, "strong", 40, 1, 1);
    var perfect = build(author, racer, machine, "perfect", 20, 0, 2);
    var good = build(author, racer, machine, "good", 30, 5, 3);
    var eight = build(author, racer, machine, "eight", 8, 0, 4);
    var tiny = build(author, racer, machine, "tiny", 3, 0, 5);
    var poor = build(author, racer, machine, "poor", 20, 40, 6);
    var zero = build(author, racer, machine, "zero", 0, 0, 7);
    var down = build(author, racer, machine, "down", 0, 1, 8);
    em.flush();

    var rated = List.of(strong, perfect, good, eight, tiny, poor, zero, down);
    assertEquals(rated, ids(author, "rated", 0, 50));
    assertEquals(List.of(down, zero, poor, tiny, eight, good, perfect, strong),
        ids(author, "newest", 0, 50));
    assertEquals(List.of(strong, good, perfect, eight, tiny, zero, down, poor),
        ids(author, "score", 0, 50));
    List<UUID> paged = new ArrayList<>();
    for (int page = 0; page < 4; page++) {
      var result = builds.list(new BuildRepository.Filter(null, null, null, author, "rated", page, 2));
      assertEquals(8, result.total());
      paged.addAll(result.items().stream().map(Build::id).toList());
    }
    assertEquals(rated, paged);
  }

  @Test
  @TestTransaction
  void ratedRespectsEveryFilterAndLiteralCaseInsensitiveSearch() {
    UUID author = user();
    UUID other = user();
    UUID racer = game.listRacers().getFirst().id();
    UUID machine = game.listMachines().getFirst().id();
    var match = build(author, racer, machine, "Test %_ target", 3, 0, 1);
    build(author, racer, machine, "Test XX target", 40, 1, 2);
    build(other, racer, machine, "Test %_ target", 40, 1, 3);
    build(author, game.listRacers().get(1).id(), machine, "Test %_ target", 40, 1, 4);
    build(author, racer, game.listMachines().get(1).id(), "Test %_ target", 40, 1, 5);
    em.flush();
    var filter = new BuildRepository.Filter("TEST %_", racer, machine, author, "rated", 0, 1);
    var result = builds.list(filter);
    assertEquals(1, result.total());
    assertEquals(List.of(match), result.items().stream().map(Build::id).toList());
    assertTrue(builds.list(new BuildRepository.Filter("TEST %_", racer, machine, author,
        "rated", 1, 1)).items().isEmpty());
  }

  @Test
  @TestTransaction
  void equalRatingsUseCreationTimeThenUuid() {
    UUID author = user();
    UUID racer = game.listRacers().getFirst().id();
    UUID machine = game.listMachines().getFirst().id();
    var old = build(author, racer, machine, "old", 3, 0, 1);
    var first = build(author, racer, machine, "first", 3, 0, 2);
    var second = build(author, racer, machine, "second", 3, 0, 2);
    em.flush();
    // PostgreSQL UUID ordering is unsigned byte order, equivalent to canonical text order.
    var tied = new ArrayList<>(List.of(first, second));
    tied.sort(java.util.Comparator.comparing(UUID::toString));
    tied.add(old);
    assertEquals(tied, ids(author, "rated", 0, 50));
  }

  private List<UUID> ids(UUID author, String sort, int page, int size) {
    return builds.list(new BuildRepository.Filter(null, null, null, author, sort, page, size))
        .items().stream().map(Build::id).toList();
  }

  private UUID part(UUID machine, String type) {
    return game.listMachineParts().stream()
        .filter(p -> p.sourceMachineId().equals(machine) && p.type().name().equals(type))
        .findFirst().orElseThrow().id();
  }

  @Test
  @TestTransaction
  void sourceFilterMatchesEachSlotAndComposesWithAllSortsAndPagination() {
    UUID author = user();
    UUID racer = game.listRacers().getFirst().id();
    UUID source = game.listMachines().getFirst().id();
    UUID other = game.listMachines().get(1).id();
    var front = build(author, racer, other, "Mixed %_ front", 3, 0, 1);
    var rear = build(author, racer, other, "Mixed %_ rear", 40, 1, 2);
    var tire = build(author, racer, other, "Mixed %_ tire", 8, 0, 3);
    em.find(BuildDbEntity.class, front).frontPartId = part(source, "FRONT");
    em.find(BuildDbEntity.class, rear).rearPartId = part(source, "REAR");
    em.find(BuildDbEntity.class, tire).tirePartId = part(source, "TIRE");
    build(author, racer, other, "Mixed %_ no source", 50, 0, 4);
    build(user(), racer, source, "Mixed %_ other author", 50, 0, 5);
    build(author, game.listRacers().get(1).id(), source, "Mixed %_ other racer", 50, 0, 6);
    build(author, racer, source, "Mixed XX wildcard decoy", 50, 0, 7);
    em.flush();
    em.clear();
    for (String sort : List.of("newest", "score", "rated")) {
      var expected = sort.equals("newest") ? List.of(tire, rear, front) : List.of(rear, tire, front);
      List<UUID> actual = new ArrayList<>();
      for (int page = 0; page < 3; page++) {
        var result = builds.list(new BuildRepository.Filter("MIXED %_", racer, source, author, sort, page, 1));
        assertEquals(3, result.total());
        actual.addAll(result.items().stream().map(Build::id).toList());
      }
      assertEquals(expected, actual);
    }
  }

  private UUID user() {
    UUID id = UUID.randomUUID();
    String name = id.toString().replace("-", "").substring(0, 24);
    em.createNativeQuery("INSERT INTO users (id, username, email, password_hash, created_at) "
        + "VALUES (:id, :name, :email, 'unused-test-hash', CURRENT_TIMESTAMP)")
        .setParameter("id", id).setParameter("name", name)
        .setParameter("email", name + "@example.test").executeUpdate();
    return id;
  }

  private UUID build(UUID author, UUID racer, UUID machine, String title,
      int up, int down, int age) {
    var entity = new BuildDbEntity();
    entity.id = UUID.randomUUID();
    entity.authorId = author;
    entity.racerId = racer;
    entity.frontPartId = part(machine, "FRONT");
    entity.rearPartId = part(machine, "REAR");
    entity.tirePartId = part(machine, "TIRE");
    entity.title = title;
    entity.description = "Ranking test";
    entity.createdAt = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(age);
    entity.updatedAt = entity.createdAt;
    em.persist(entity);
    for (int i = 0; i < up + down; i++) {
      var vote = new VoteDbEntity();
      vote.id = UUID.randomUUID();
      vote.buildId = entity.id;
      vote.userId = user();
      vote.value = (short) (i < up ? 1 : -1);
      em.persist(vote);
    }
    return entity.id;
  }
}
