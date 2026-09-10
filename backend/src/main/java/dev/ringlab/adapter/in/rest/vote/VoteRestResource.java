package dev.ringlab.adapter.in.rest.vote;

import lombok.RequiredArgsConstructor;

import dev.ringlab.adapter.in.rest.auth.CurrentUser;
import dev.ringlab.adapter.in.rest.vote.request.VoteRequest;
import dev.ringlab.adapter.in.rest.vote.response.VoteResponse;
import dev.ringlab.application.vote.VoteService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/api/builds/{id}/vote")
@RolesAllowed("user")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class VoteRestResource {

  private final VoteService service;
  private final CurrentUser actor;

  @GET
  public VoteResponse get(@PathParam("id") UUID id) {
    return VoteResponse.from(service.get(actor.id(), id));
  }

  @PUT
  public VoteResponse put(@PathParam("id") UUID id, @Valid @NotNull VoteRequest r) {
    return VoteResponse.from(service.put(actor.id(), id, r.value()));
  }

  @DELETE
  public VoteResponse remove(@PathParam("id") UUID id) {
    return VoteResponse.from(service.remove(actor.id(), id));
  }
}
