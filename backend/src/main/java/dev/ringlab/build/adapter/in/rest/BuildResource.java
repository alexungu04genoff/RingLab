package dev.ringlab.build.adapter.in.rest;

import dev.ringlab.auth.application.AuthService;
import dev.ringlab.build.application.BuildService;
import dev.ringlab.build.application.port.out.BuildStore;
import dev.ringlab.build.domain.Build;
import dev.ringlab.gamedata.adapter.in.rest.GameDataResource.ItemResponse;
import dev.ringlab.gamedata.application.port.out.GameDataStore;
import dev.ringlab.shared.adapter.in.rest.CurrentUser;
import dev.ringlab.vote.application.VoteService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.*;

@Path("/api/builds")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BuildResource {
  public record BuildRequest(
      @NotBlank @Size(max = 120) String title,
      @NotNull @Size(max = 10000) String description,
      @NotNull UUID racerId,
      @NotNull UUID machineId,
      @NotNull List<@NotNull UUID> gadgetIds) {
    BuildService.Draft draft() {
      return new BuildService.Draft(title, description, racerId, machineId, gadgetIds);
    }
  }

  public record AuthorResponse(UUID id, String username) {}

  public record BuildResponse(
      UUID id,
      String title,
      String description,
      AuthorResponse author,
      ItemResponse racer,
      ItemResponse machine,
      List<ItemResponse> gadgets,
      Instant createdAt,
      Instant updatedAt,
      long score) {}

  public record PageResponse(List<BuildResponse> items, long total, int page, int size) {}

  private final BuildService builds;
  private final AuthService users;
  private final GameDataStore game;
  private final VoteService votes;
  private final CurrentUser actor;

  public BuildResource(
      BuildService builds,
      AuthService users,
      GameDataStore game,
      VoteService votes,
      CurrentUser actor) {
    this.builds = builds;
    this.users = users;
    this.game = game;
    this.votes = votes;
    this.actor = actor;
  }

  private ItemResponse item(GameDataStore.Kind kind, UUID id) {
    var i = game.find(kind, id).orElseThrow();
    return new ItemResponse(
        i.id(), i.name(), i.racingType(), i.description(), i.slotCost(), i.imagePath());
  }

  private BuildResponse response(Build b) {
    var author = users.current(b.authorId());
    return new BuildResponse(
        b.id(),
        b.title(),
        b.description(),
        new AuthorResponse(author.id(), author.username()),
        item(GameDataStore.Kind.RACER, b.racerId()),
        item(GameDataStore.Kind.MACHINE, b.machineId()),
        b.gadgetIds().stream().map(id -> item(GameDataStore.Kind.GADGET, id)).toList(),
        b.createdAt(),
        b.updatedAt(),
        votes.score(b.id()));
  }

  @GET
  public PageResponse list(
      @QueryParam("search") @Size(max = 120) String search,
      @QueryParam("racerId") UUID racer,
      @QueryParam("machineId") UUID machine,
      @QueryParam("authorId") UUID author,
      @QueryParam("sort") @DefaultValue("newest") @Pattern(regexp = "newest|score") String sort,
      @QueryParam("page") @DefaultValue("0") @Min(0) @Max(100000) int page,
      @QueryParam("size") @DefaultValue("12") @Min(1) @Max(50) int size) {
    var result =
        builds.list(new BuildStore.Filter(search, racer, machine, author, sort, page, size));
    return new PageResponse(
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
