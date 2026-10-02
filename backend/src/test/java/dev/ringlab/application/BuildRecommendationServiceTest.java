package dev.ringlab.application;

import dev.ringlab.application.build.*;
import dev.ringlab.domain.build.recommendation.*;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.port.out.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildRecommendationServiceTest {
  private static final UUID ACTOR = UUID.randomUUID();
  private final dev.ringlab.application.collection.CollectionService collection = new dev.ringlab.application.collection.CollectionService(null, null) {
    @Override public dev.ringlab.domain.collection.CollectionExclusions load(UUID actor) {
      assertEquals(ACTOR, actor);
      return dev.ringlab.domain.collection.CollectionExclusions.NONE;
    }
  };
  private static final UUID VERSION = UUID.randomUUID();
  private static final BuildSelection EMPTY = new BuildSelection(null, null, null, null, List.of());
  private static RecommendationRequest request() {
    return new RecommendationRequest(VERSION, RacingType.SPEED, List.of(StatPriority.values()), EMPTY, EMPTY);
  }
  private final Catalog game = new Catalog();
  private int statReads;
  private final BaseStatsRepository stats = new BaseStatsRepository() {
    public Map<UUID, BaseStats> racerStats(UUID id) { assertEquals(VERSION, id); statReads++; return Map.of(); }
    public Map<UUID, BaseStats> machinePartStats(UUID id) { assertEquals(VERSION, id); statReads++; return Map.of(); }
  };

  @Test void snapshotLoadsEveryFactOnceAndNeverUsesBuildOrCommunityPorts() {
    var service = new BuildRecommendationService(new RecommendationCatalogLoader(game, stats, dev.ringlab.importing.RuleFixtures.loader()), collection);
    assertEquals(RecommendationResult.Outcome.NO_LEGAL_COMPLETION, service.recommend(ACTOR, request()).outcome());
    assertEquals(5, game.reads); assertEquals(2, statReads);
    assertThrows(ValidationException.class, () -> service.recommend(ACTOR, null));
    var invalid = new RecommendationRequest(UUID.randomUUID(), RacingType.SPEED, List.of(StatPriority.values()), EMPTY, EMPTY);
    assertThrows(ValidationException.class, () -> service.recommend(ACTOR, invalid));
  }

  @Test void translatesDomainValidationAndReleasesCapacityAfterFailure() {
    var service = new BuildRecommendationService(new RecommendationCatalogLoader(game, stats, dev.ringlab.importing.RuleFixtures.loader()), collection);
    var unknown = new BuildSelection(UUID.randomUUID(), null, null, null, List.of());
    var invalid = new RecommendationRequest(VERSION, RacingType.SPEED, List.of(StatPriority.values()), unknown, EMPTY);
    for (int attempt = 0; attempt < 4; attempt++)
      assertTrue(assertThrows(ValidationException.class, () -> service.recommend(ACTOR, invalid)).getMessage().contains("Unknown racer"));
    assertEquals(RecommendationResult.Outcome.NO_LEGAL_COMPLETION, service.recommend(ACTOR, request()).outcome());
  }

  @Test void concurrentSearchesHaveTwoPermitsAndNeverQueueUnboundedWork() throws Exception {
    var entered = new CountDownLatch(2); var release = new CountDownLatch(1); var loads = new AtomicInteger();
    var snapshot = new RecommendationCatalog(new GameVersion(VERSION, "1.4.1", LocalDate.EPOCH),
        Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), dev.ringlab.importing.RuleFixtures.snapshot());
    var loader = new RecommendationCatalogLoader(game, stats, dev.ringlab.importing.RuleFixtures.loader()) {
      @Override public RecommendationCatalog load(UUID id) {
        loads.incrementAndGet(); entered.countDown();
        try { if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Test release missing"); }
        catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
        return snapshot;
      }
    };
    var service = new BuildRecommendationService(loader, collection);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> service.recommend(ACTOR, request()));
      var second = executor.submit(() -> service.recommend(ACTOR, request()));
      try {
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        assertThrows(ExternalServiceUnavailableException.class, () -> service.recommend(ACTOR, request()));
        assertEquals(2, loads.get());
      } finally { release.countDown(); }
      assertEquals(RecommendationResult.Outcome.NO_LEGAL_COMPLETION, first.get(10, TimeUnit.SECONDS).outcome());
      second.get(10, TimeUnit.SECONDS);
    }
    service.recommend(ACTOR, request()); assertEquals(3, loads.get());
  }

  private static final class Catalog implements GameDataRepository {
    int reads;
    public List<RaceMap> listRaceMaps() { throw new AssertionError("Maps do not affect recommendations"); }
    public List<GameVersion> listGameVersions() { throw new AssertionError("Only the selected patch is resolved"); }
    public Optional<GameVersion> findGameVersion(UUID id) { reads++; return id.equals(VERSION) ? Optional.of(new GameVersion(id, "1.4.1", LocalDate.EPOCH)) : Optional.empty(); }
    public List<Racer> listRacers() { reads++; return List.of(); }
    public List<Machine> listMachines() { reads++; return List.of(); }
    public List<MachinePart> listMachineParts() { reads++; return List.of(); }
    public List<Gadget> listGadgets() { reads++; return List.of(); }
    public Optional<Racer> findRacer(UUID id) { throw new AssertionError("No per-candidate I/O"); }
    public Optional<Machine> findMachine(UUID id) { throw new AssertionError("No per-candidate I/O"); }
    public Optional<MachinePart> findMachinePart(UUID id) { throw new AssertionError("No per-candidate I/O"); }
    public Optional<Gadget> findGadget(UUID id) { throw new AssertionError("No per-candidate I/O"); }
  }
}
