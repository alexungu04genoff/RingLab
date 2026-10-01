package dev.ringlab.application.community;

import dev.ringlab.port.in.CommunitySnapshot;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommunitySnapshotCacheTest {
  @Test void mapEditsRefreshWithTheExistingLifetimeEvenWhenVotesStayUnchanged() {
    var time = new AtomicReference<>(Instant.EPOCH);
    Clock clock = new Clock() {
      public ZoneId getZone() { return ZoneOffset.UTC; }
      public Clock withZone(ZoneId zone) { return this; }
      public Instant instant() { return time.get(); }
    };
    var map = new dev.ringlab.domain.gamedata.RaceMap(UUID.randomUUID(), "E-Stadium",
        dev.ringlab.domain.gamedata.RaceMap.Category.MAIN_COURSE, null, null, 1);
    var selection = new AtomicReference<List<dev.ringlab.domain.gamedata.RaceMap>>(List.of());
    UUID buildId = UUID.randomUUID(), author = UUID.randomUUID(), racer = UUID.randomUUID(), part = UUID.randomUUID();
    var votes = new dev.ringlab.domain.vote.VoteSummary(10, 0);
    var cache = new CommunitySnapshotCache(() -> {
      var maps = selection.get();
      var build = new dev.ringlab.domain.build.Build(buildId, "Setup", "", author, racer, part, part,
          null, null, null, List.of(), maps.stream().map(dev.ringlab.domain.gamedata.RaceMap::id)
              .collect(java.util.stream.Collectors.toSet()), Instant.EPOCH, clock.instant());
      var entry = new CommunitySnapshot.Entry(build, "author", null, null, null, null, null,
          List.of(), votes, null, null, maps);
      return new CommunitySnapshot(UUID.randomUUID(), clock.instant(), List.of(entry));
    }, Duration.ofSeconds(60), clock);
    var before = cache.get();
    selection.set(List.of(map));
    assertSame(before, cache.get());
    time.set(Instant.EPOCH.plusSeconds(60));
    var after = cache.get();
    assertNotEquals(before.revision(), after.revision());
    assertEquals(List.of(map), after.items().getFirst().recommendedMaps());
    assertEquals(before.items().getFirst().votes(), after.items().getFirst().votes());
    assertEquals(buildId, after.items().getFirst().build().id());
  }

  @Test void cachesExpiresAndDoesNotCacheFailures() {
    var time = new AtomicReference<>(Instant.EPOCH);
    Clock clock = new Clock() {
      public ZoneId getZone() { return ZoneOffset.UTC; }
      public Clock withZone(ZoneId zone) { return this; }
      public Instant instant() { return time.get(); }
    };
    var calls = new AtomicInteger();
    var fail = new AtomicBoolean();
    var cache = new CommunitySnapshotCache(() -> {
      calls.incrementAndGet();
      if (fail.get()) throw new IllegalStateException("Database unavailable");
      return new CommunitySnapshot(UUID.randomUUID(), clock.instant(), List.of());
    }, Duration.ofSeconds(60), clock);
    var first = cache.get();
    assertSame(first, cache.get());
    assertEquals(1, calls.get());
    time.set(Instant.EPOCH.plusSeconds(60));
    fail.set(true);
    assertThrows(IllegalStateException.class, cache::get);
    fail.set(false);
    assertNotEquals(first.revision(), cache.get().revision());
    assertEquals(3, calls.get());
  }

  @Test void concurrentMissesComputeOnce() throws Exception {
    var calls = new AtomicInteger();
    var cache = new CommunitySnapshotCache(() -> {
      calls.incrementAndGet();
      return new CommunitySnapshot(UUID.randomUUID(), Instant.now(), List.of());
    }, Duration.ofSeconds(60), Clock.systemUTC());
    try (var executor = Executors.newFixedThreadPool(8)) {
      var start = new CountDownLatch(1);
      var futures = new ArrayList<Future<CommunitySnapshot>>();
      for (int i = 0; i < 8; i++) futures.add(executor.submit(() -> { start.await(); return cache.get(); }));
      start.countDown();
      var result = futures.getFirst().get(5, TimeUnit.SECONDS);
      for (var future : futures) assertSame(result, future.get(5, TimeUnit.SECONDS));
      assertEquals(1, calls.get());
    }
    assertThrows(IllegalArgumentException.class, () -> new CommunitySnapshotCache(() -> null, Duration.ZERO, Clock.systemUTC()));
    assertThrows(IllegalArgumentException.class, () -> new CommunitySnapshotCache(() -> null, Duration.ofHours(1), Clock.systemUTC()));
  }
}
