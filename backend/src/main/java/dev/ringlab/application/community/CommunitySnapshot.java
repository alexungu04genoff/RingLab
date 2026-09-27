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
                      BaseStatsBreakdown stats, Build remixSource, List<RaceMap> recommendedMaps) {
    public Entry { gadgets = List.copyOf(gadgets); recommendedMaps = List.copyOf(recommendedMaps); }
    public PassiveStatsResult passiveStats() {
      var machines = new java.util.HashMap<UUID,Machine>();
      for (var part : java.util.Arrays.asList(front,rear,tire)) {
        if (part != null && part.source() != null) machines.put(part.source().id(),part.source());
      }
      return dev.ringlab.application.gamedata.PassiveStatsService.resolved(stats,patch,racer,
          front == null ? null : front.part(),rear == null ? null : rear.part(),tire == null ? null : tire.part(),
          machines,gadgets,dev.ringlab.domain.build.GadgetPlate.canFit(gadgets.stream().map(Gadget::slotCost).toList()));
    }
  }
}
