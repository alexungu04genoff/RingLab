package dev.ringlab.adapter.in.rest.build;

import dev.ringlab.port.in.AuthUseCase;
import dev.ringlab.port.in.BuildUseCase;
import dev.ringlab.port.in.VoteUseCase;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.vote.VoteSummary;
import dev.ringlab.adapter.in.rest.build.response.AuthorResponse;
import dev.ringlab.adapter.in.rest.build.response.BuildResponse;
import dev.ringlab.adapter.in.rest.build.response.RemixSourceResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.GadgetResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.GameVersionResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.MachinePartResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.RacerResponse;
import dev.ringlab.port.in.GameDataQueryUseCase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import dev.ringlab.domain.gamedata.RaceMap;
import dev.ringlab.adapter.in.rest.build.response.MapRecommendationsResponse;
import lombok.RequiredArgsConstructor;

/** Enriches persisted builds with the public author, catalog, provenance and vote response. */
@ApplicationScoped
@RequiredArgsConstructor
class BuildResponseAssembler {
  private final BuildUseCase builds;
  private final AuthUseCase users;
  private final GameDataQueryUseCase game;
  private final VoteUseCase votes;

  private RacerResponse racerItem(UUID id) {
    return RacerResponse.from(game.findRacer(id).orElseThrow());
  }

  private MachinePartResponse partItem(UUID id) {
    if (id == null) return null;
    var part = game.findMachinePart(id).orElseThrow();
    return MachinePartResponse.from(part, game.findMachine(part.sourceMachineId()).orElseThrow());
  }

  private GadgetResponse gadgetItem(UUID id) {
    return GadgetResponse.from(game.findGadget(id).orElseThrow());
  }

  BuildResponse assemble(Build build) {
    return assemble(build, votes.summary(build.id()));
  }

  BuildResponse assemble(Build build, VoteSummary summary) {
    return assemble(build, summary, build.recommendedMapIds().isEmpty() ? List.of() : game.listRaceMaps());
  }

  /** Load the bounded map catalog once per response page, never once per map/card. */
  List<BuildResponse> assembleAll(List<Build> items, Map<UUID, VoteSummary> summaries) {
    var maps = items.stream().anyMatch(build -> !build.recommendedMapIds().isEmpty())
        ? game.listRaceMaps() : List.<RaceMap>of();
    return items.stream().map(build -> assemble(build,
        summaries.getOrDefault(build.id(), new VoteSummary(0, 0)), maps)).toList();
  }

  private BuildResponse assemble(Build build, VoteSummary summary, List<RaceMap> maps) {
    var author = users.current(build.authorId());
    var remixSource = builds.remixSource(build).orElse(null);
    return new BuildResponse(
        build.id(),
        build.title(),
        build.description(),
        new AuthorResponse(author.id(), author.username()),
        racerItem(build.racerId()),
        partItem(build.frontPartId()),
        partItem(build.rearPartId()),
        partItem(build.tirePartId()),
        build.gameVersionId() == null ? null : GameVersionResponse.from(game.findGameVersion(build.gameVersionId()).orElseThrow()),
        remixSource == null ? null : new RemixSourceResponse(remixSource.id(), remixSource.title()),
        build.gadgetIds().stream().map(this::gadgetItem).toList(),
        MapRecommendationsResponse.from(build.recommendedMapIds(), maps),
        build.createdAt(),
        build.updatedAt(),
        summary.score(), summary.upvotes(), summary.downvotes());
  }
}
