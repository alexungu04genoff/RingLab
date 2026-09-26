package dev.ringlab.application.auth;

import dev.ringlab.application.ValidationException;
import dev.ringlab.domain.auth.EmailVerificationToken;
import dev.ringlab.domain.auth.User;
import dev.ringlab.port.out.EmailVerificationTokenRepository;
import dev.ringlab.port.out.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class EmailVerificationService {
  private static final SecureRandom TOKEN_RANDOM = new SecureRandom();
  private static final Duration TOKEN_LIFETIME = Duration.ofHours(24);

  private final UserRepository users;
  private final EmailVerificationTokenRepository tokens;

  public record VerificationEmail(String email, String token) {}

  @Transactional
  public void verifyEmail(String rawToken) {
    if (rawToken == null || rawToken.isBlank() || rawToken.length() > 512)
      throw invalidVerificationToken();
    var token = tokens.byTokenHashForUpdate(hash(rawToken))
        .orElseThrow(this::invalidVerificationToken);
    if (!token.expiresAt().isAfter(Instant.now())) {
      tokens.delete(token.userId());
      throw invalidVerificationToken();
    }
    users.markEmailVerified(token.userId(), Instant.now());
    tokens.delete(token.userId());
  }

  @Transactional
  public Optional<VerificationEmail> resendVerification(String email) {
    if (email == null || email.isBlank() || email.length() > 254) return Optional.empty();
    return users.byEmail(email.toLowerCase(Locale.ROOT))
        .filter(user -> user.passwordHash() != null && user.emailVerifiedAt() == null)
        .map(user -> issueVerification(user, Instant.now()));
  }

  /** Registration calls this inside its transaction so account and token are saved together. */
  VerificationEmail issueVerification(User user, Instant now) {
    String rawToken = newToken();
    tokens.replace(new EmailVerificationToken(
        user.id(), hash(rawToken), now.plus(TOKEN_LIFETIME), now));
    return new VerificationEmail(user.email(), rawToken);
  }

  private static String newToken() {
    byte[] bytes = new byte[32];
    TOKEN_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hash(String token) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private ValidationException invalidVerificationToken() {
    return new ValidationException("Verification link is invalid or expired");
  }
}
