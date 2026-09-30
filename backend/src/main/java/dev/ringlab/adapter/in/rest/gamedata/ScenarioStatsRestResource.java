package dev.ringlab.adapter.in.rest.gamedata;

import dev.ringlab.adapter.in.rest.gamedata.request.ScenarioStatsRequest;
import dev.ringlab.adapter.in.rest.gamedata.response.ScenarioStatsResponse;
import dev.ringlab.application.gamedata.ScenarioStatsService;
import dev.ringlab.domain.gamedata.ScenarioEffectRule;
import dev.ringlab.domain.gamedata.ScenarioGadgetRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Path("/api/stats")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class ScenarioStatsRestResource {
  private final ScenarioStatsService service;

  @POST @Path("scenario-build") @Consumes(MediaType.APPLICATION_JSON)
  public ScenarioStatsResponse preview(@NotNull @Valid ScenarioStatsRequest request) {
    return ScenarioStatsResponse.from(service.preview(request.gameVersionId(), request.racerId(),
        request.frontPartId(), request.rearPartId(), request.tirePartId(), request.gadgetIds(), request.scenario().toDomain()));
  }

  public record Control(UUID gadgetId, ScenarioEffectRule.Field field, boolean statEffect) {}
  public record Controls(String supportedVersion, List<Control> controls) {}

  /** Describes relevant inputs without duplicating numerical rules in the browser. */
  @GET @Path("scenario-rules")
  public Controls controls() {
    return new Controls(ScenarioGadgetRules.VERSION, ScenarioGadgetRules.all().stream()
        .map(r -> new Control(r.gadgetId(), r.condition().field(), r.statEffect())).toList());
  }
}
