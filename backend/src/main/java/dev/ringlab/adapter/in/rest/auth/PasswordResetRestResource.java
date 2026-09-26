package dev.ringlab.adapter.in.rest.auth;

import dev.ringlab.adapter.in.rest.auth.response.MessageResponse;
import dev.ringlab.application.ExternalServiceUnavailableException;
import dev.ringlab.application.auth.PasswordResetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.jbosslog.JBossLog;

@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@JBossLog
public class PasswordResetRestResource {
  private final PasswordResetService service;
  public record ForgotPasswordRequest(String email) {}
  public record ResetPasswordRequest(String token, String password, String confirmPassword) {}

  @POST @Path("forgot-password")
  public MessageResponse forgot(@Valid @NotNull ForgotPasswordRequest request) {
    try { service.request(request.email()); }
    catch (ExternalServiceUnavailableException failure) {
      log.warn("Password reset email delivery failed; token change rolled back");
    }
    return new MessageResponse(PasswordResetService.CONFIRMATION);
  }

  @POST @Path("reset-password")
  public MessageResponse reset(@Valid @NotNull ResetPasswordRequest request) {
    service.reset(request.token(), request.password(), request.confirmPassword());
    return new MessageResponse("Your password has been reset. Please log in.");
  }
}
