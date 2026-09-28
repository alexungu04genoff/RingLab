package dev.ringlab.adapter.in.rest.build;

import dev.ringlab.adapter.in.rest.auth.CurrentUser;
import dev.ringlab.adapter.in.rest.build.request.BuildRecommendationRequest;
import dev.ringlab.adapter.in.rest.build.response.BuildRecommendationResponse;
import dev.ringlab.application.ValidationException;
import dev.ringlab.application.build.BuildRecommendationService;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import lombok.RequiredArgsConstructor;

@Path("/api/build-recommendations")
@RolesAllowed("user")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class BuildRecommendationRestResource {
  private final CurrentUser actor;
  private final BuildRecommendationService recommendations;

  @POST
  @Blocking
  public Response recommend(@NotNull @Valid BuildRecommendationRequest request) {
    try {
      return Response.ok(BuildRecommendationResponse.from(recommendations.recommend(actor.id(), request.toDomain())))
          .header("Cache-Control", "private, no-store").header("Vary", "Authorization").build();
    } catch (IllegalArgumentException invalid) {
      throw new ValidationException(invalid.getMessage());
    }
  }
}
