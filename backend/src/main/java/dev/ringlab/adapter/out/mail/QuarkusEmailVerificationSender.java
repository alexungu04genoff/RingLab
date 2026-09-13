package dev.ringlab.adapter.out.mail;

import dev.ringlab.port.out.EmailVerificationSender;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class QuarkusEmailVerificationSender implements EmailVerificationSender {
  private final Mailer mailer;
  private final String from;

  public QuarkusEmailVerificationSender(Mailer mailer, @ConfigProperty(name = "ringlab.mail.from") String from) {
    this.mailer = mailer;
    this.from = from;
  }

  @Override
  public void sendVerification(String email, String verificationUrl) {
    String body = "Welcome to RingLab.\n\nVerify your email address to activate your account:\n\n"
        + verificationUrl + "\n\nThis link expires in 24 hours.\n\nIf you did not create this account, you can ignore this email.";
    mailer.send(Mail.withText(email, "Verify your RingLab account", body).setFrom(from));
  }
}
