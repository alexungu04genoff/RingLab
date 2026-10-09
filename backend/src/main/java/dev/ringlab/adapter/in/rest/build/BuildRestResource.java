package dev.ringlab.adapter.in.rest.build;

import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

import dev.ringlab.port.in.BuildUseCase;
import dev.ringlab.port.in.PassiveStatsUseCase;
import dev.ringlab.adapter.in.rest.gamedata.response.BuildStatsResponse;
import dev.ringlab.port.in.CommunityUseCase;
import dev.ringlab.domain.build.ranking.BuildSort;
import dev.ringlab.domain.build.BuildVisibility;
import dev.ringlab.application.ValidationException;
import dev.ringlab.adapter.in.rest.build.request.BuildRequest;
import dev.ringlab.adapter.in.rest.build.response.BuildPageResponse;
import dev.ringlab.adapter.in.rest.build.response.BuildResponse;
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
@JBossLog
public class BuildRestResource {

  private final BuildUseCase builds;
  private final BuildResponseAssembler responses;
  private final CurrentUser actor;
  private final CommunityUseCase communitySnapshots;
  private final PassiveStatsUseCase stats;

  @GET
  public BuildPageResponse list(
      @QueryParam("search") @Size(max = 120) String search,
      @QueryParam("racerId") UUID racer,
      @QueryParam("machineId") UUID machine,
      @QueryParam("authorId") UUID author,
      @QueryParam("gameVersionId") UUID gameVersion,
      @QueryParam("mapId") UUID mapId,
      @QueryParam("includeAllMaps") @DefaultValue("true") @Pattern(regexp = "true|false") String includeAllMaps,
      @QueryParam("excludeTop") @DefaultValue("false") boolean excludeTop,
      @QueryParam("excludeId") @Size(max = 3) List<UUID> excludedBuildIds,
      @QueryParam("sort") @DefaultValue("rated") @Pattern(regexp = "newest|score|rated") String sort,
      @QueryParam("page") @DefaultValue("0") @Min(0) @Max(100000) int page,
      @QueryParam("size") @DefaultValue("12") @Min(1) @Max(50) int size,
      @QueryParam("includeStats") @DefaultValue("false") boolean includeStats,
      @QueryParam("mine") @DefaultValue("false") @Pattern(regexp = "true|false") String mine,
      @QueryParam("visibility") @DefaultValue("ALL") @Pattern(regexp = "ALL|PUBLIC|PRIVATE") String visibility) {
    boolean ownerScope = Boolean.parseBoolean(mine);
    if (ownerScope && author != null) throw new ValidationException("My Builds cannot be combined with authorId");
    if (!ownerScope && !"ALL".equals(visibility))
      throw new ValidationException("Visibility filtering requires My Builds");
    UUID owner = ownerScope ? actor.id() : null;
    BuildSort buildSort = switch (sort) {
      case "score" -> BuildSort.SCORE;
      case "rated" -> BuildSort.BEST_RATED;
      default -> BuildSort.NEWEST;
    };
    // Explicit IDs pin exclusions to the snapshot displayed by this client.
    Set<UUID> excludedIds = Set.of();
    if (excludedBuildIds != null && !excludedBuildIds.isEmpty()) {
      excludedIds = Set.copyOf(excludedBuildIds);
    } else if (excludeTop) {
      excludedIds = communitySnapshots.get().items().stream()
            .map(item -> item.build().id())
            .collect(java.util.stream.Collectors.toSet());
    }
    var query = new BuildUseCase.Query(
        new BuildUseCase.Filter(search, racer, machine, author, gameVersion, excludedIds,
            mapId, Boolean.parseBoolean(includeAllMaps)),
        buildSort, page, size);
    var result = ownerScope
        ? builds.listMine(owner, query, "ALL".equals(visibility) ? null : BuildVisibility.valueOf(visibility))
        : builds.list(query);
    var items = result.items().isEmpty() ? List.<BuildResponse>of() : responses.assembleAll(result.items(), result.summaries());
    Map<UUID, BuildStatsResponse> pageStats = null;
    String statsError = null;
    if (includeStats) {
      try {
        pageStats = new HashMap<>();
        for (var entry : stats.buildPage(result.items()).entrySet()) {
          pageStats.put(entry.getKey(), BuildStatsResponse.withPassive(entry.getValue()));
        }
        pageStats = Map.copyOf(pageStats);
      } catch (RuntimeException failure) {
        log.warn("Could not load optional build-page stats", failure);
        pageStats = null;
        statsError = "Could not load base stats. Please try again.";
      }
    }
    return new BuildPageResponse(items, result.total(), page, size, pageStats, statsError);
  }

  @GET
  @Path("{id}")
  public BuildResponse get(@PathParam("id") UUID id) {
    return responses.assemble(builds.get(id, actor.optionalId()));
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
