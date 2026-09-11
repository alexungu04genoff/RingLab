package dev.ringlab;

import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.domain.build.Build;
import dev.ringlab.application.AppException;
import dev.ringlab.domain.vote.Vote;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class DomainTest {
  @Test
  void buildTakesAnImmutableOrderedSnapshot() {
    var ids = new ArrayList<>(List.of(UUID.randomUUID(), UUID.randomUUID()));
    var snapshot = List.copyOf(ids);
    var id = UUID.randomUUID();
    var build = new Build(id, "Title", "", id, id, id, id, id, null, ids, Instant.now(), Instant.now());
    ids.clear();
    assertEquals(snapshot, build.gadgetIds());
    assertThrows(UnsupportedOperationException.class, () -> build.gadgetIds().clear());
  }

  @Test
  void voteValuesAndOwnershipAreExplicit() {
    var owner = UUID.randomUUID();
    var other = UUID.randomUUID();
    assertThrows(IllegalArgumentException.class, () -> new Vote(owner, other, 0));
    assertThrows(IllegalArgumentException.class, () -> new Vote(owner, other, 2));
    assertDoesNotThrow(() -> new Vote(owner, other, -1));
    assertDoesNotThrow(() -> AppException.requireOwner(owner, owner));
    assertEquals(
        403,
        assertThrows(AppException.class, () -> AppException.requireOwner(owner, other)).status);
  }
}
