package dev.ringlab.comment.adapter.in.rest;

import dev.ringlab.auth.application.AuthService;
import dev.ringlab.comment.application.CommentService;
import dev.ringlab.comment.domain.Comment;
import dev.ringlab.shared.adapter.in.rest.CurrentUser;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.*;

@Path("/api/builds/{id}/comments")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CommentResource {
  public record CommentRequest(@NotBlank @Size(max = 2000) String text) {}

  public record CommentResponse(
      UUID id, UUID buildId, UUID authorId, String author, String text, Instant createdAt) {}

  private final CommentService service;
  private final AuthService users;
  private final CurrentUser actor;

  public CommentResource(CommentService service, AuthService users, CurrentUser actor) {
    this.service = service;
    this.users = users;
    this.actor = actor;
  }

  private CommentResponse response(Comment c) {
    return new CommentResponse(
        c.id(),
        c.buildId(),
        c.authorId(),
        users.current(c.authorId()).username(),
        c.text(),
        c.createdAt());
  }

  @GET
  public List<CommentResponse> list(
      @PathParam("id") UUID id,
      @QueryParam("page") @DefaultValue("0") @Min(0) @Max(100000) int page,
      @QueryParam("size") @DefaultValue("20") @Min(1) @Max(50) int size) {
    return service.list(id, page, size).stream().map(this::response).toList();
  }

  @POST
  @RolesAllowed("user")
  public CommentResponse create(@PathParam("id") UUID id, @Valid @NotNull CommentRequest r) {
    return response(service.create(id, actor.id(), r.text()));
  }
}
