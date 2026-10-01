package dev.ringlab.adapter.in.rest.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.ringlab.adapter.in.rest.auth.request.RegistrationRequest;
import dev.ringlab.adapter.in.rest.auth.request.ResendVerificationRequest;
import dev.ringlab.application.ExternalServiceUnavailableException;
import dev.ringlab.application.auth.AuthService;
import dev.ringlab.application.auth.AccountRegistrationService;
import dev.ringlab.application.auth.EmailVerificationService;
import dev.ringlab.application.auth.EmailVerificationService.VerificationEmail;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AuthRestResourceTest {
  @Test
  void registrationReportsMailDeliveryFailureWithoutExposingTheCause() {
    var service = new AuthService(null, null, null, null) {
      @Override
      public VerificationEmail register(String username, String email, String password) {
        return new VerificationEmail(email, "secret-token");
      }
    };
    var registration = new AccountRegistrationService(service, null,
        (email, verificationUrl) -> { throw new IllegalStateException("SMTP details"); }, "https://ringlab.example");
    var resource = new AuthRestResource(service, null, null, registration);

    var error = assertThrows(ExternalServiceUnavailableException.class,
        () -> resource.register(new RegistrationRequest(
            "account", "account@example.test", "password-123")));

    assertEquals("We could not send the verification email. Please try resend verification later.",
        error.getMessage());
  }

  @Test
  void resendKeepsTheSameGenericResponseWhenNoAccountIsEligible() {
    var verification = new EmailVerificationService(null, null) {
      @Override
      public Optional<VerificationEmail> resendVerification(String email) {
        return Optional.empty();
      }
    };
    var registration = new AccountRegistrationService(null, verification,
        (email, verificationUrl) -> { throw new AssertionError("No email should be sent"); }, "https://ringlab.example");
    var resource = new AuthRestResource(null, null, null, registration);

    var response = resource.resendVerification(new ResendVerificationRequest("missing@example.test"));

    assertEquals("Check your email to verify your account.", response.message());
  }
}
