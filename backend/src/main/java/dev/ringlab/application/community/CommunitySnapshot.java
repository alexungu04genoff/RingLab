package dev.ringlab.application.community;

import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.domain.vote.VoteSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CommunitySnapshot(UUID revision, Instant snapshotAt, List<Entry> items) {
  public CommunitySnapshot { items = List.copyOf(items); }

  public record Part(MachinePart part, Machine source) {}
  public record Entry(Build build, String authorName, Racer racer, Part front, Part rear,
                      Part tire, GameVersion patch, List<Gadget> gadgets, VoteSummary votes,
                      BaseStatsBreakdown stats, Build remixSource) {
    public Entry { gadgets = List.copyOf(gadgets); }
  }
}
