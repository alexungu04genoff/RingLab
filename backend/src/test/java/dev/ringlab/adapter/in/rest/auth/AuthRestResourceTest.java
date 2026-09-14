package dev.ringlab.adapter.in.rest.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.ringlab.adapter.in.rest.auth.request.RegistrationRequest;
import dev.ringlab.adapter.in.rest.auth.request.ResendVerificationRequest;
import dev.ringlab.application.ExternalServiceUnavailableException;
import dev.ringlab.application.auth.AuthService;
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
    var resource = new AuthRestResource(service, null, null,
        (email, verificationUrl) -> { throw new IllegalStateException("SMTP details"); });
    resource.publicBaseUrl = "https://ringlab.example";

    var error = assertThrows(ExternalServiceUnavailableException.class,
        () -> resource.register(new RegistrationRequest(
            "account", "account@example.test", "password-123")));

    assertEquals("We could not send the verification email. Please try resend verification later.",
        error.getMessage());
  }

  @Test
  void resendKeepsTheSameGenericResponseWhenNoAccountIsEligible() {
    var service = new AuthService(null, null, null, null) {
      @Override
      public Optional<VerificationEmail> resendVerification(String email) {
        return Optional.empty();
      }
    };
    var resource = new AuthRestResource(service, null, null,
        (email, verificationUrl) -> { throw new AssertionError("No email should be sent"); });

    var response = resource.resendVerification(new ResendVerificationRequest("missing@example.test"));

    assertEquals("Check your email to verify your account.", response.message());
  }
}
