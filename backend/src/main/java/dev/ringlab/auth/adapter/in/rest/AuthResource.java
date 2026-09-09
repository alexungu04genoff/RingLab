package dev.ringlab.auth.adapter.in.rest;

import dev.ringlab.auth.application.AuthService;
import dev.ringlab.auth.domain.User;
import dev.ringlab.shared.adapter.in.rest.CurrentUser;
import io.smallrye.jwt.build.Jwt;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.UUID;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {
  public record Registration(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,30}") String username,
      @NotBlank @Email @Size(max = 254) String email,
      @NotNull @Size(min = 8, max = 72) String password) {}

  public record Login(
      @NotBlank @Size(max = 30) String username, @NotBlank @Size(max = 72) String password) {}

  public record UserResponse(UUID id, String username, String email, Instant createdAt) {
    static UserResponse from(User u) {
      return new UserResponse(u.id(), u.username(), u.email(), u.createdAt());
    }
  }

  public record Session(String token, UserResponse user) {}

  private final AuthService service;
  private final CurrentUser actor;

  public AuthResource(AuthService service, CurrentUser actor) {
    this.service = service;
    this.actor = actor;
  }

  private Session session(User u) {
    return new Session(
        Jwt.subject(u.id().toString()).upn(u.username()).groups("user").sign(),
        UserResponse.from(u));
  }

  @POST
  @Path("register")
  public Session register(@Valid @NotNull Registration r) {
    return session(service.register(r.username(), r.email(), r.password()));
  }

  @POST
  @Path("login")
  public Session login(@Valid @NotNull Login r) {
    return session(service.login(r.username(), r.password()));
  }

  @GET
  @Path("me")
  @RolesAllowed("user")
  public UserResponse me() {
    return UserResponse.from(service.current(actor.id()));
  }

  @POST
  @Path("logout")
  @RolesAllowed("user")
  public void logout() {
    /* Client discards its short-lived bearer token. */
  }
}
