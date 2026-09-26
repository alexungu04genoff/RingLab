package dev.ringlab.adapter.in.rest.auth;

import lombok.RequiredArgsConstructor;

import dev.ringlab.adapter.in.rest.auth.request.LoginRequest;
import dev.ringlab.adapter.in.rest.auth.request.RegistrationRequest;
import dev.ringlab.adapter.in.rest.auth.response.SessionResponse;
import dev.ringlab.adapter.in.rest.auth.response.UserResponse;
import dev.ringlab.adapter.in.rest.auth.response.MessageResponse;
import dev.ringlab.adapter.in.rest.auth.request.EmailVerificationRequest;
import dev.ringlab.adapter.in.rest.auth.request.ResendVerificationRequest;
import dev.ringlab.application.auth.AuthService;
import dev.ringlab.application.auth.EmailVerificationService;
import dev.ringlab.application.auth.EmailVerificationService.VerificationEmail;
import dev.ringlab.application.auth.ExternalAuthService;
import dev.ringlab.adapter.in.rest.auth.request.GoogleSignInRequest;
import dev.ringlab.domain.auth.User;
import dev.ringlab.adapter.in.rest.auth.CurrentUser;
import dev.ringlab.port.out.EmailVerificationSender;
import dev.ringlab.application.ExternalServiceUnavailableException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
  private final ExternalAuthService externalAuth;
  private final EmailVerificationService verification;
  private final EmailVerificationSender verificationSender;
  @ConfigProperty(name = "ringlab.public-base-url") String publicBaseUrl;

  @POST
  @Path("google")
  public SessionResponse google(@Valid @NotNull GoogleSignInRequest r) {
    return session(externalAuth.login(r.credential()));
  }

  @POST
  @Path("google/link")
  @RolesAllowed("user")
  public MessageResponse linkGoogle(@Valid @NotNull GoogleSignInRequest request) {
    externalAuth.link(actor.id(), request.credential());
    return new MessageResponse("Google sign-in is now linked to your account.");
  }

  private SessionResponse session(User u) {
    return new SessionResponse(
        Jwt.subject(u.id().toString()).upn(u.username()).groups("user").sign(),
        UserResponse.from(u));
  }

  @POST
  @Path("register")
  public MessageResponse register(@Valid @NotNull RegistrationRequest r) {
    sendVerification(service.register(r.username(), r.email(), r.password()));
    return checkEmailMessage();
  }

  @POST
  @Path("verify-email")
  public MessageResponse verifyEmail(@Valid @NotNull EmailVerificationRequest request) {
    verification.verifyEmail(request.token());
    return new MessageResponse("Your email has been verified. You can now sign in.");
  }

  @POST
  @Path("resend-verification")
  public MessageResponse resendVerification(@Valid @NotNull ResendVerificationRequest request) {
    verification.resendVerification(request.email()).ifPresent(this::sendVerification);
    return checkEmailMessage();
  }

  private MessageResponse checkEmailMessage() {
    return new MessageResponse("Check your email to verify your account.");
  }

  private void sendVerification(VerificationEmail email) {
    try {
      verificationSender.sendVerification(email.email(), publicBaseUrl + "/verify-email?token=" + email.token());
    } catch (RuntimeException exception) {
      throw new ExternalServiceUnavailableException("We could not send the verification email. Please try resend verification later.");
    }
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
    actor.id();
  }
}
