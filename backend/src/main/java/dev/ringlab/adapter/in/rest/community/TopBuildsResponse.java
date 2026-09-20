package dev.ringlab.adapter.in.rest.community;

import dev.ringlab.adapter.in.rest.build.response.*;
import dev.ringlab.adapter.in.rest.gamedata.response.*;
import dev.ringlab.application.community.CommunitySnapshot;
import dev.ringlab.domain.gamedata.RacingType;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

public record TopBuildsResponse(int schemaVersion, String ranking, String scope, String patches,
                               Instant snapshotAt, List<Item> items) {
  public record Item(int rank, BuildResponse build, RacingType machineType,
                     BuildStatsResponse stats, String buildUrl, String artworkUrl) {}

  public static TopBuildsResponse from(CommunitySnapshot snapshot, CommunityPublicUrls urls) {
    return new TopBuildsResponse(1, "best-rated", "overall", "all", snapshot.snapshotAt(),
        IntStream.range(0, snapshot.items().size()).mapToObj(index -> {
          var entry = snapshot.items().get(index);
          var b = entry.build();
          var votes = entry.votes();
          var build = new BuildResponse(b.id(), b.title(), b.description(),
              new AuthorResponse(b.authorId(), entry.authorName()), RacerResponse.from(entry.racer()),
              part(entry.front()), part(entry.rear()), part(entry.tire()),
              entry.patch() == null ? null : GameVersionResponse.from(entry.patch()),
              entry.remixSource() == null ? null : new RemixSourceResponse(entry.remixSource().id(), entry.remixSource().title()),
              entry.gadgets().stream().map(GadgetResponse::from).toList(), b.createdAt(), b.updatedAt(),
              votes.score(), votes.upvotes(), votes.downvotes());
          return new Item(index + 1, build, entry.front().source().racingType(),
              BuildStatsResponse.from(entry.stats()), urls.build(b.id()), urls.artwork(entry.racer().imagePath()));
        }).toList());
  }

  private static MachinePartResponse part(CommunitySnapshot.Part part) {
    return part == null ? null : MachinePartResponse.from(part.part(), part.source());
  }
}
