package dev.ringlab.adapter.in.rest.auth;

import lombok.RequiredArgsConstructor;

import dev.ringlab.adapter.in.rest.auth.request.LoginRequest;
import dev.ringlab.adapter.in.rest.auth.request.RegistrationRequest;
import dev.ringlab.adapter.in.rest.auth.response.SessionResponse;
import dev.ringlab.adapter.in.rest.auth.response.UserResponse;
import dev.ringlab.application.auth.AuthService;
import dev.ringlab.domain.auth.User;
import dev.ringlab.adapter.in.rest.auth.CurrentUser;
import io.smallrye.jwt.build.Jwt;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class AuthRestResource {
  private final AuthService service;
  private final CurrentUser actor;

  private SessionResponse session(User u) {
    return new SessionResponse(
        Jwt.subject(u.id().toString()).upn(u.username()).groups("user").sign(),
        UserResponse.from(u));
  }

  @POST
  @Path("register")
  public SessionResponse register(@Valid @NotNull RegistrationRequest r) {
    return session(service.register(r.username(), r.email(), r.password()));
  }

  @POST
  @Path("login")
  public SessionResponse login(@Valid @NotNull LoginRequest r) {
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
