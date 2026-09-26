package dev.ringlab.port.out;

import dev.ringlab.domain.auth.PasswordResetToken;
import dev.ringlab.domain.auth.User;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetRepository {
  /** All reset mutations first lock the user, including requests with no existing token. */
  Optional<User> lockUser(UUID userId);
  Optional<PasswordResetToken> byHash(String hash);
  Optional<PasswordResetToken> byUser(UUID userId);
  void replace(PasswordResetToken token);
  void changePasswordAndVersion(UUID userId, String passwordHash);
  void delete(UUID userId);
}
