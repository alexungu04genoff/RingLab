package dev.ringlab.adapter.in.rest.build;

import lombok.RequiredArgsConstructor;

import dev.ringlab.application.auth.AuthService;
import dev.ringlab.application.build.BuildService;
import dev.ringlab.domain.build.Build;
import dev.ringlab.adapter.in.rest.build.request.BuildRequest;
import dev.ringlab.adapter.in.rest.build.response.AuthorResponse;
import dev.ringlab.adapter.in.rest.build.response.BuildPageResponse;
import dev.ringlab.adapter.in.rest.build.response.BuildResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.GadgetResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.GameVersionResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.MachinePartResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.RacerResponse;
import dev.ringlab.port.out.BuildRepository;
import dev.ringlab.port.out.GameDataRepository;
import dev.ringlab.adapter.in.rest.auth.CurrentUser;
import dev.ringlab.application.vote.VoteService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.*;

@Path("/api/builds")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class BuildRestResource {

  private final BuildService builds;
  private final AuthService users;
  private final GameDataRepository game;
  private final VoteService votes;
  private final CurrentUser actor;

  private RacerResponse racerItem(UUID id) {
    return RacerResponse.from(game.findRacer(id).orElseThrow());
  }

  private MachinePartResponse partItem(UUID id) {
    var part = game.findMachinePart(id).orElseThrow();
    return MachinePartResponse.from(part, game.findMachine(part.sourceMachineId()).orElseThrow());
  }

  private GadgetResponse gadgetItem(UUID id) {
    return GadgetResponse.from(game.findGadget(id).orElseThrow());
  }

  private BuildResponse response(Build b) {
    var author = users.current(b.authorId());
    return new BuildResponse(
        b.id(),
        b.title(),
        b.description(),
        new AuthorResponse(author.id(), author.username()),
        racerItem(b.racerId()),
        partItem(b.frontPartId()),
        partItem(b.rearPartId()),
        partItem(b.tirePartId()),
        b.gameVersionId() == null ? null : GameVersionResponse.from(game.findGameVersion(b.gameVersionId()).orElseThrow()),
        b.gadgetIds().stream().map(this::gadgetItem).toList(),
        b.createdAt(),
        b.updatedAt(),
        votes.score(b.id()));
  }

  @GET
  public BuildPageResponse list(
      @QueryParam("search") @Size(max = 120) String search,
      @QueryParam("racerId") UUID racer,
      @QueryParam("machineId") UUID machine,
      @QueryParam("authorId") UUID author,
      @QueryParam("gameVersionId") UUID gameVersion,
      @QueryParam("sort") @DefaultValue("newest") @Pattern(regexp = "newest|score|rated") String sort,
      @QueryParam("page") @DefaultValue("0") @Min(0) @Max(100000) int page,
      @QueryParam("size") @DefaultValue("12") @Min(1) @Max(50) int size) {
    var result =
        builds.list(new BuildRepository.Filter(search, racer, machine, author, gameVersion, sort, page, size));
    return new BuildPageResponse(
        result.items().stream().map(this::response).toList(), result.total(), page, size);
  }

  @GET
  @Path("{id}")
  public BuildResponse get(@PathParam("id") UUID id) {
    return response(builds.get(id));
  }

  @POST
  @RolesAllowed("user")
  public BuildResponse create(@Valid @NotNull BuildRequest r) {
    return response(builds.create(actor.id(), r.draft()));
  }

  @PUT
  @Path("{id}")
  @RolesAllowed("user")
  public BuildResponse edit(@PathParam("id") UUID id, @Valid @NotNull BuildRequest r) {
    return response(builds.edit(id, actor.id(), r.draft()));
  }

  @DELETE
  @Path("{id}")
  @RolesAllowed("user")
  public void delete(@PathParam("id") UUID id) {
    builds.delete(id, actor.id());
  }
}
