package dev.ringlab.adapter.in.rest.collection;

import dev.ringlab.adapter.in.rest.auth.CurrentUser;
import dev.ringlab.application.collection.CollectionService;
import dev.ringlab.domain.collection.CollectionCategory;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.util.UUID;
import java.util.Set;
import lombok.RequiredArgsConstructor;

@Path("/api/collection")
@RolesAllowed("user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class CollectionRestResource {
  private final CurrentUser actor;
  private final CollectionService collection;
  public record OwnershipRequest(@NotNull Boolean owned) {}
  public record CollectionResponse(Set<UUID> racers, Set<UUID> machines, Set<UUID> gadgets) {}

  @GET
  public Response load() {
    var exclusions = collection.load(actor.id());
    return privateResponse(Response.ok(new CollectionResponse(exclusions.racers(), exclusions.machines(), exclusions.gadgets())));
  }

  @PUT
  @Path("/{category}/{id}")
  public Response update(@PathParam("category") CollectionCategory category, @PathParam("id") UUID id,
      @NotNull @Valid OwnershipRequest request) {
    collection.setOwned(actor.id(), category, id, request.owned());
    return privateResponse(Response.noContent());
  }

  private static Response privateResponse(Response.ResponseBuilder response) {
    return response.header("Cache-Control", "private, no-store").header("Vary", "Authorization").build();
  }
}
