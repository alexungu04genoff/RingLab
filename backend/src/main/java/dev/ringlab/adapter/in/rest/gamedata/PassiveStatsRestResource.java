package dev.ringlab.adapter.in.rest.gamedata;

import dev.ringlab.adapter.in.rest.gamedata.response.BuildStatsResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.StatsResponse;
import dev.ringlab.port.in.PassiveStatsUseCase;
import dev.ringlab.port.in.BuildUseCase;
import dev.ringlab.domain.gamedata.*;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Path("/api/stats")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class PassiveStatsRestResource {
  private final PassiveStatsUseCase stats;
  private final BuildUseCase builds;
  private final dev.ringlab.adapter.in.rest.auth.CurrentUser actor;

  @GET @Path("persisted/{id}")
  public BuildStatsResponse persisted(@PathParam("id") UUID id) {
    return BuildStatsResponse.withPassive(stats.buildPage(List.of(builds.get(id, actor.optionalId()))).get(id));
  }

  @GET @Path("passive-build")
  public BuildStatsResponse draft(@QueryParam("gameVersionId") UUID version,
      @QueryParam("racerId") UUID racer, @QueryParam("frontPartId") UUID front,
      @QueryParam("rearPartId") UUID rear, @QueryParam("tirePartId") UUID tire,
      @QueryParam("gadgetId") @Size(max=6) List<UUID> gadgetIds) {
    return BuildStatsResponse.withPassive(stats.draft(version,racer,front,rear,tire,gadgetIds));
  }

  public record Rule(String effectId, String label, GadgetEffectRule.Kind kind,
      GadgetEffectRule.Subject subject, RacingType requiredType, StatsResponse matching,
      StatsResponse nonMatching, String explanation, List<String> sources, String stackingGroup) {}
  public record GadgetRules(UUID gadgetId, List<Rule> effects) {}
  public record Catalog(String ruleset, String supportedVersion, String note, List<GadgetRules> gadgets) {}

  @GET @Path("gadget-rules")
  public Catalog rules() {
    var rules = stats.rules();
    return new Catalog(rules.ruleset(), rules.supportedVersion(), rules.note(),
        rules.gadgets().stream().map(g -> new GadgetRules(g.gadgetId(),g.effects().stream()
            .map(r -> new Rule(r.effectId(),r.label(),r.kind(),r.subject(),r.requiredType(),
                StatsResponse.from(r.matching()),StatsResponse.from(r.nonMatching()),r.explanation(),r.sources(),r.stackingGroup())).toList())).toList());
  }
}
