package dev.ringlab.adapter.in.rest.build;

import dev.ringlab.application.auth.AuthService;
import dev.ringlab.application.build.BuildService;
import dev.ringlab.application.vote.VoteService;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.vote.VoteSummary;
import dev.ringlab.adapter.in.rest.build.response.AuthorResponse;
import dev.ringlab.adapter.in.rest.build.response.BuildResponse;
import dev.ringlab.adapter.in.rest.build.response.RemixSourceResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.GadgetResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.GameVersionResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.MachinePartResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.RacerResponse;
import dev.ringlab.port.out.GameDataRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/** Enriches persisted builds with the public author, catalog, provenance and vote response. */
@ApplicationScoped
@RequiredArgsConstructor
class BuildResponseAssembler {
  private final BuildService builds;
  private final AuthService users;
  private final GameDataRepository game;
  private final VoteService votes;

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
        build.createdAt(),
        build.updatedAt(),
        summary.score(), summary.upvotes(), summary.downvotes());
  }
}
