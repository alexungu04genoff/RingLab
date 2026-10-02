package dev.ringlab.application;

import dev.ringlab.application.gamedata.ReviewedGadgetRules;
import dev.ringlab.domain.gamedata.GameVersion;
import dev.ringlab.domain.gamedata.GadgetRuleSnapshot;
import dev.ringlab.domain.gamedata.PassiveGadgetRules;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReviewedGadgetRulesTest {
  private static final GadgetRuleSnapshot SNAPSHOT = new GadgetRuleSnapshot(
      new GameVersion(new UUID(0, 1), PassiveGadgetRules.VERSION, LocalDate.of(2026, 9, 28)),
      "reviewed-passive", "reviewed-scenario", Map.of(), List.of(), Set.of());

  @Test void successfulSnapshotIsLoadedOnceAndShared() {
    var loads = new AtomicInteger();
    var reviewed = new ReviewedGadgetRules(version -> {
      assertEquals(PassiveGadgetRules.VERSION, version);
      loads.incrementAndGet();
      return Optional.of(SNAPSHOT);
    });
    assertSame(SNAPSHOT, reviewed.snapshot());
    assertSame(SNAPSHOT, reviewed.snapshot());
    assertEquals(1, loads.get());
  }

  @Test void missingSnapshotIsRetriedUntilSuccessful() {
    var loads = new AtomicInteger();
    var reviewed = new ReviewedGadgetRules(version ->
        loads.incrementAndGet() == 1 ? Optional.empty() : Optional.of(SNAPSHOT));
    var missing = assertThrows(IllegalStateException.class, reviewed::snapshot);
    assertEquals("Reviewed gadget rule snapshot is missing; apply the schema migrations", missing.getMessage());
    assertSame(SNAPSHOT, reviewed.snapshot());
    assertSame(SNAPSHOT, reviewed.snapshot());
    assertEquals(2, loads.get());
  }

  @Test void repositoryFailureIsRetriedUntilSuccessful() {
    var loads = new AtomicInteger();
    var failure = new IllegalStateException("Database unavailable");
    var reviewed = new ReviewedGadgetRules(version -> {
      if (loads.incrementAndGet() == 1) throw failure;
      return Optional.of(SNAPSHOT);
    });
    assertSame(failure, assertThrows(IllegalStateException.class, reviewed::snapshot));
    assertSame(SNAPSHOT, reviewed.snapshot());
    assertSame(SNAPSHOT, reviewed.snapshot());
    assertEquals(2, loads.get());
  }

  @Test void concurrentCallersShareOneRepositoryLoad() throws Exception {
    var loads = new AtomicInteger();
    var reviewed = new ReviewedGadgetRules(version -> {
      loads.incrementAndGet();
      return Optional.of(SNAPSHOT);
    });
    int callers = 8;
    var ready = new CountDownLatch(callers);
    var start = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(callers);
    try {
      var results = new ArrayList<Future<GadgetRuleSnapshot>>();
      for (int i = 0; i < callers; i++) results.add(executor.submit(() -> {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return reviewed.snapshot();
      }));
      assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      for (var result : results) assertSame(SNAPSHOT, result.get(5, TimeUnit.SECONDS));
      assertEquals(1, loads.get());
    } finally {
      start.countDown();
      executor.shutdownNow();
    }
  }
}
