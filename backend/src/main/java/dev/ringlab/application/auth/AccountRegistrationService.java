package dev.ringlab.application.auth;

import dev.ringlab.application.ExternalServiceUnavailableException;
import dev.ringlab.application.auth.EmailVerificationService.VerificationEmail;
import dev.ringlab.port.in.AccountRegistrationUseCase;
import dev.ringlab.port.out.EmailVerificationSender;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Keeps verification delivery outside the registration/resend database transactions. */
@ApplicationScoped
public class AccountRegistrationService implements AccountRegistrationUseCase {
  private final AuthService accounts;
  private final EmailVerificationService verification;
  private final EmailVerificationSender sender;
  private final String publicBaseUrl;

  public AccountRegistrationService(AuthService accounts, EmailVerificationService verification,
      EmailVerificationSender sender, @ConfigProperty(name = "ringlab.public-base-url") String publicBaseUrl) {
    this.accounts = accounts;
    this.verification = verification;
    this.sender = sender;
    this.publicBaseUrl = publicBaseUrl;
  }

  @Transactional(Transactional.TxType.NOT_SUPPORTED)
  public void register(String username, String email, String password) {
    sendVerification(accounts.register(username, email, password));
  }

  public void verifyEmail(String token) {
    verification.verifyEmail(token);
  }

  @Transactional(Transactional.TxType.NOT_SUPPORTED)
  public void resendVerification(String email) {
    verification.resendVerification(email).ifPresent(this::sendVerification);
  }

  private void sendVerification(VerificationEmail email) {
    try {
      sender.sendVerification(email.email(), publicBaseUrl + "/verify-email?token=" + email.token());
    } catch (RuntimeException exception) {
      throw new ExternalServiceUnavailableException("We could not send the verification email. Please try resend verification later.");
    }
  }
}
