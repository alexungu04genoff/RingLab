package dev.ringlab.port.in;

import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.domain.vote.VoteSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Detached community facts; calculation and caching belong to the application. */
public record CommunitySnapshot(UUID revision, Instant snapshotAt, List<Entry> items) {
  public CommunitySnapshot { items = List.copyOf(items); }
  public record Part(MachinePart part, Machine source) {}
  public record Entry(Build build, String authorName, Racer racer, Part front, Part rear,
                      Part tire, GameVersion patch, List<Gadget> gadgets, VoteSummary votes,
                      BaseStatsBreakdown stats, Build remixSource, List<RaceMap> recommendedMaps) {
    public Entry { gadgets = List.copyOf(gadgets); recommendedMaps = List.copyOf(recommendedMaps); }
  }
}
