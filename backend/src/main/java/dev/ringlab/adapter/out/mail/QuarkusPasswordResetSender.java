package dev.ringlab.adapter.out.mail;

import dev.ringlab.port.out.PasswordResetSender;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class QuarkusPasswordResetSender implements PasswordResetSender {
  private final Mailer mailer;
  private final String from;
  public QuarkusPasswordResetSender(Mailer mailer, @ConfigProperty(name = "ringlab.mail.from") String from) {
    this.mailer = mailer; this.from = from;
  }
  public void sendReset(String email, String resetUrl, Instant expiresAt) {
    mailer.send(Mail.withText(email, "Reset your RingLab password",
        "A password reset was requested for your RingLab account.\n\n" + resetUrl
        + "\n\nThis single-use link expires at " + expiresAt + " (UTC)."
        + "\n\nIf you did not request this, ignore this email. Your password has not changed.").setFrom(from));
  }
}
