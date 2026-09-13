package dev.ringlab.port.out;

import dev.ringlab.domain.auth.EmailVerificationToken;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository {
  void replace(EmailVerificationToken token);

  Optional<EmailVerificationToken> byTokenHashForUpdate(String tokenHash);

  void delete(UUID userId);
}
