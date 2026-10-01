package dev.ringlab.adapter.in.rest.comment;

import lombok.RequiredArgsConstructor;

import dev.ringlab.port.in.CommentUseCase;
import dev.ringlab.adapter.in.rest.auth.CurrentUser;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.UUID;

@Path("/api/comments/{id}")
@RolesAllowed("user")
@RequiredArgsConstructor
public class CommentDeletionRestResource {
  private final CommentUseCase service;
  private final CurrentUser actor;

  @DELETE
  public void delete(@PathParam("id") UUID id) {
    service.delete(id, actor.id());
  }
}
