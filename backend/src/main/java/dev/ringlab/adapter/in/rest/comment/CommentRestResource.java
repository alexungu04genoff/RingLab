package dev.ringlab.adapter.in.rest.comment;

import lombok.RequiredArgsConstructor;

import dev.ringlab.application.auth.AuthService;
import dev.ringlab.application.comment.CommentService;
import dev.ringlab.domain.comment.Comment;
import dev.ringlab.adapter.in.rest.auth.CurrentUser;
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
@RequiredArgsConstructor
public class CommentRestResource {
  public record CommentRequest(@NotBlank @Size(max = 2000) String text) {}

  public record CommentResponse(
      UUID id, UUID buildId, UUID authorId, String author, String text, Instant createdAt) {}

  private final CommentService service;
  private final AuthService users;
  private final CurrentUser actor;

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
