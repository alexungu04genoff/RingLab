package dev.ringlab.application.community;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommunitySnapshotCacheTest {
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
