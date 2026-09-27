package dev.ringlab.adapter.in.rest.gamedata;

import dev.ringlab.adapter.in.rest.gamedata.response.BuildStatsResponse;
import dev.ringlab.adapter.in.rest.gamedata.response.StatsResponse;
import dev.ringlab.application.gamedata.PassiveStatsService;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.port.out.GameDataRepository;
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
  private final PassiveStatsService stats;
  private final GameDataRepository game;
  private final dev.ringlab.application.build.BuildService builds;

  @GET @Path("persisted/{id}")
  public BuildStatsResponse persisted(@PathParam("id") UUID id) {
    return BuildStatsResponse.withPassive(stats.buildPage(List.of(builds.get(id))).get(id));
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
    return new Catalog(PassiveGadgetRules.RULESET,PassiveGadgetRules.VERSION,PassiveStatsCalculator.ARITHMETIC_NOTE,
        game.listGadgets().stream().map(g -> new GadgetRules(g.id(),PassiveGadgetRules.forGadget(g.id()).stream()
            .map(r -> new Rule(r.effectId(),r.label(),r.kind(),r.subject(),r.requiredType(),
                StatsResponse.from(r.matching()),StatsResponse.from(r.nonMatching()),r.explanation(),r.sources(),r.stackingGroup())).toList())).toList());
  }
}
