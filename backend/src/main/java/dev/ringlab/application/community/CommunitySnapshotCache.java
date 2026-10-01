package dev.ringlab.application.community;

import dev.ringlab.port.in.CommunitySnapshot;
import dev.ringlab.port.in.CommunityUseCase;
import dev.ringlab.domain.gamedata.*;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** One entry per application instance. Failures never replace a successful snapshot. */
@ApplicationScoped
public class CommunitySnapshotCache implements CommunityUseCase {
  private final Supplier<CommunitySnapshot> loader;
  private final Duration lifetime;
  private final Clock clock;
  private CommunitySnapshot snapshot;
  private Instant expiresAt = Instant.MIN;

  @Inject
  public CommunitySnapshotCache(CommunitySelectionService selection,
      @ConfigProperty(name = "ringlab.community.snapshot-lifetime", defaultValue = "PT60S") Duration lifetime) {
    this(selection::select, lifetime, Clock.systemUTC());
  }

  public CommunitySnapshotCache(Supplier<CommunitySnapshot> loader, Duration lifetime, Clock clock) {
    if (lifetime.compareTo(Duration.ofSeconds(1)) < 0 || lifetime.compareTo(Duration.ofMinutes(5)) > 0)
      throw new IllegalArgumentException("Community snapshot lifetime must be between 1 and 300 seconds");
    this.loader = loader;
    this.lifetime = lifetime;
    this.clock = clock;
  }

  public synchronized CommunitySnapshot get() {
    var startedAt = clock.instant();
    if (snapshot == null || !startedAt.isBefore(expiresAt)) {
      var loaded = loader.get(); // Transaction has committed before publishing the snapshot.
      snapshot = loaded;
      expiresAt = startedAt.plus(lifetime);
    }
    return snapshot;
  }

  public PassiveStatsResult passiveStats(CommunitySnapshot.Entry entry) {
    var machines = new java.util.HashMap<UUID, Machine>();
    for (var part : java.util.Arrays.asList(entry.front(), entry.rear(), entry.tire())) {
      if (part != null && part.source() != null) machines.put(part.source().id(), part.source());
    }
    return dev.ringlab.application.gamedata.PassiveStatsService.resolved(entry.stats(), entry.patch(), entry.racer(),
        entry.front() == null ? null : entry.front().part(), entry.rear() == null ? null : entry.rear().part(),
        entry.tire() == null ? null : entry.tire().part(), machines, entry.gadgets(),
        dev.ringlab.domain.build.GadgetPlate.canFit(entry.gadgets().stream().map(Gadget::slotCost).toList()));
  }
}
