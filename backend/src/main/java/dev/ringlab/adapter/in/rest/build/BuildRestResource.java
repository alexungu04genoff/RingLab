package dev.ringlab.adapter.in.rest.build;

import lombok.RequiredArgsConstructor;

import dev.ringlab.application.build.BuildService;
import dev.ringlab.application.community.CommunitySnapshotCache;
import dev.ringlab.domain.build.ranking.BuildSort;
import dev.ringlab.adapter.in.rest.build.request.BuildRequest;
import dev.ringlab.adapter.in.rest.build.response.BuildPageResponse;
import dev.ringlab.adapter.in.rest.build.response.BuildResponse;
import dev.ringlab.port.out.BuildRepository;
import dev.ringlab.adapter.in.rest.auth.CurrentUser;
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
  private final BuildResponseAssembler responses;
  private final CurrentUser actor;
  private final CommunitySnapshotCache communitySnapshots;

  @GET
  public BuildPageResponse list(
      @QueryParam("search") @Size(max = 120) String search,
      @QueryParam("racerId") UUID racer,
      @QueryParam("machineId") UUID machine,
      @QueryParam("authorId") UUID author,
      @QueryParam("gameVersionId") UUID gameVersion,
      @QueryParam("excludeTop") @DefaultValue("false") boolean excludeTop,
      @QueryParam("sort") @DefaultValue("rated") @Pattern(regexp = "newest|score|rated") String sort,
      @QueryParam("page") @DefaultValue("0") @Min(0) @Max(100000) int page,
      @QueryParam("size") @DefaultValue("12") @Min(1) @Max(50) int size) {
    BuildSort buildSort = switch (sort) {
      case "score" -> BuildSort.SCORE;
      case "rated" -> BuildSort.BEST_RATED;
      default -> BuildSort.NEWEST;
    };
    var excludedIds = excludeTop
        ? communitySnapshots.get().items().stream()
            .map(item -> item.build().id())
            .collect(java.util.stream.Collectors.toSet())
        : Set.<UUID>of();
    var result = builds.list(new BuildService.Query(
        new BuildRepository.Filter(search, racer, machine, author, gameVersion, excludedIds),
        buildSort, page, size));
    return new BuildPageResponse(
        result.items().stream().map(build -> responses.assemble(build, result.summary(build.id()))).toList(),
        result.total(), page, size);
  }

  @GET
  @Path("{id}")
  public BuildResponse get(@PathParam("id") UUID id) {
    return responses.assemble(builds.get(id));
  }

  @POST
  @RolesAllowed("user")
  public BuildResponse create(@Valid @NotNull BuildRequest r) {
    return responses.assemble(builds.create(actor.id(), r.draft()));
  }

  @PUT
  @Path("{id}")
  @RolesAllowed("user")
  public BuildResponse edit(@PathParam("id") UUID id, @Valid @NotNull BuildRequest r) {
    return responses.assemble(builds.edit(id, actor.id(), r.draft()));
  }

  @DELETE
  @Path("{id}")
  @RolesAllowed("user")
  public void delete(@PathParam("id") UUID id) {
    builds.delete(id, actor.id());
  }
}
