package dev.ringlab.port.out;

import java.time.Instant;

public interface PasswordResetSender {
  void sendReset(String email, String resetUrl, Instant expiresAt);
}
