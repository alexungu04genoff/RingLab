package dev.ringlab.adapter.in.rest.build;

import dev.ringlab.adapter.in.rest.auth.CurrentUser;
import dev.ringlab.adapter.in.rest.build.response.BuildResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.BuildStatsResponse;
import dev.ringlab.application.build.SavedBuildService;
import dev.ringlab.application.gamedata.BaseStatsService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;
import java.time.Instant;
import java.util.*;

@Path("/api/saved-builds")
@RolesAllowed("user")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@JBossLog
public class SavedBuildRestResource {
  private final SavedBuildService saved;
  private final CurrentUser actor;
  private final BuildResponseAssembler responses;
  private final BaseStatsService stats;
  public record SavedItem(BuildResponse build, Instant savedAt) {}
  public record SavedPage(List<SavedItem> items, long total, int page, int size,
      Map<UUID, BuildStatsResponse> statsByBuildId, String statsError) {}
  public record SavedStatus(Set<UUID> savedIds) {}
  public record SavedResult(UUID buildId, Instant savedAt) {}

  private Response privateResponse(Object value) {
    return Response.ok(value).header("Cache-Control", "private, no-store").header("Vary", "Authorization").build();
  }

  @GET
  public Response list(@QueryParam("search") @Size(max = 120) String search,
      @QueryParam("gameVersionId") UUID version,
      @QueryParam("page") @DefaultValue("0") @Min(0) @Max(100000) int page,
      @QueryParam("size") @DefaultValue("12") @Min(1) @Max(50) int size) {
    var result = saved.list(actor.id(), search, version, page, size);
    var items = result.items().stream().map(item -> new SavedItem(responses.assemble(item.build()), item.savedAt())).toList();
    Map<UUID, BuildStatsResponse> pageStats = new HashMap<>();
    String error = null;
    try {
      stats.buildPage(result.items().stream().map(SavedBuildService.Item::build).toList())
          .forEach((id, value) -> pageStats.put(id, BuildStatsResponse.from(value)));
    } catch (RuntimeException failure) {
      log.warn("Could not load optional saved-build page stats", failure);
      error = "Could not load base stats. Please try again.";
      pageStats.clear();
    }
    return privateResponse(new SavedPage(items, result.total(), page, size, error == null ? Map.copyOf(pageStats) : null, error));
  }

  @GET @Path("status")
  public Response status(@QueryParam("buildId") Set<UUID> ids) {
    if (ids.size() > 50) throw new BadRequestException("At most 50 distinct build IDs are allowed.");
    return privateResponse(new SavedStatus(saved.status(actor.id(), ids)));
  }

  @PUT @Path("{id}")
  public Response save(@PathParam("id") UUID id) { return privateResponse(new SavedResult(id, saved.save(actor.id(), id))); }

  @DELETE @Path("{id}")
  public Response remove(@PathParam("id") UUID id) {
    saved.remove(actor.id(), id);
    return Response.noContent().header("Cache-Control", "private, no-store").build();
  }
}
