package dev.ringlab.comment.adapter.in.rest;

import dev.ringlab.comment.application.CommentService;
import dev.ringlab.shared.adapter.in.rest.CurrentUser;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import java.util.UUID;

@Path("/api/comments/{id}")
@RolesAllowed("user")
public class CommentDeletionResource {
  private final CommentService service;
  private final CurrentUser actor;

  public CommentDeletionResource(CommentService service, CurrentUser actor) {
    this.service = service;
    this.actor = actor;
  }

  @DELETE
  public void delete(@PathParam("id") UUID id) {
    service.delete(id, actor.id());
  }
}
