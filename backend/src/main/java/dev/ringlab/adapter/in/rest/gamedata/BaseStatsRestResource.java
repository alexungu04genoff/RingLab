package dev.ringlab.adapter.in.rest.gamedata;

import dev.ringlab.application.gamedata.BaseStatsService;
import dev.ringlab.adapter.in.rest.gamedata.response.StatsResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.BuildStatsResponse;
import dev.ringlab.domain.gamedata.BaseStats;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@Path("/api/stats")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class BaseStatsRestResource {
  private final BaseStatsService stats;

  public record CatalogResponse(UUID gameVersionId, Map<UUID, StatsResponse> racers,
                                Map<UUID, StatsResponse> machineParts, Map<UUID, StatsResponse> machines) {}

  @GET
  @Path("catalog")
  public CatalogResponse catalog(@QueryParam("gameVersionId") UUID version) {
    var result = stats.catalog(version);
    return new CatalogResponse(version, responses(result.racers()), responses(result.machineParts()), responses(result.machines()));
  }

  @GET
  @Path("build")
  public BuildStatsResponse build(@QueryParam("gameVersionId") UUID version,
      @QueryParam("racerId") UUID racer, @QueryParam("frontPartId") UUID front,
      @QueryParam("rearPartId") UUID rear, @QueryParam("tirePartId") UUID tire) {
    return BuildStatsResponse.from(stats.buildBreakdown(version, racer, front, rear, tire));
  }

  private Map<UUID, StatsResponse> responses(Map<UUID, BaseStats> values) {
    return values.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> StatsResponse.from(e.getValue())));
  }
}
