package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.gamedata.*;
import java.util.*;

/** One detached, immutable catalog/patch snapshot; search never performs I/O. */
public record RecommendationCatalog(GameVersion version, Map<UUID, Racer> racers,
    Map<UUID, Machine> machines, Map<UUID, MachinePart> parts, Map<UUID, Gadget> gadgets,
    Map<UUID, BaseStats> racerStats, Map<UUID, BaseStats> partStats) {
  public RecommendationCatalog {
    Objects.requireNonNull(version);
    racers = Map.copyOf(racers);
    machines = Map.copyOf(machines);
    parts = Map.copyOf(parts);
    gadgets = Map.copyOf(gadgets);
    racerStats = Map.copyOf(racerStats);
    partStats = Map.copyOf(partStats);
  }
}
