package dev.ringlab.application.auth;

import dev.ringlab.application.ExternalServiceUnavailableException;
import dev.ringlab.application.ValidationException;
import dev.ringlab.domain.auth.PasswordResetToken;
import dev.ringlab.domain.auth.User;
import dev.ringlab.port.out.PasswordResetRepository;
import dev.ringlab.port.out.PasswordResetSender;
import dev.ringlab.port.out.UserRepository;
import io.quarkus.elytron.security.common.BcryptUtil;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class PasswordResetService {
  public static final String CONFIRMATION = "If an eligible account exists for that email, a password reset link has been sent.";
  public static final String INVALID_LINK = "This password reset link is invalid or has expired.";
  private static final SecureRandom RANDOM = new SecureRandom();
  private final UserRepository users;
  private final PasswordResetRepository tokens;
  private final PasswordResetSender sender;
  private final Duration ttl;
  private final Duration cooldown;
  private final String publicBaseUrl;

  public PasswordResetService(UserRepository users, PasswordResetRepository tokens, PasswordResetSender sender,
      @ConfigProperty(name = "ringlab.password-reset.ttl") Duration ttl,
      @ConfigProperty(name = "ringlab.password-reset.cooldown") Duration cooldown,
      @ConfigProperty(name = "ringlab.public-base-url") String publicBaseUrl) {
    if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofHours(24)) > 0
        || cooldown.isNegative() || cooldown.compareTo(ttl) >= 0)
      throw new IllegalArgumentException("Password reset requires 0 < TTL <= 24 hours and 0 <= cooldown < TTL");
    this.users = users; this.tokens = tokens; this.sender = sender;
    this.ttl = ttl; this.cooldown = cooldown; this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
  }

  @Transactional
  public void request(String email) {
    if (email == null || email.isBlank() || email.length() > 254) return;
    var found = users.byEmail(email.toLowerCase(Locale.ROOT));
    if (found.isEmpty()) return;
    var user = tokens.lockUser(found.get().id());
    if (user.isEmpty() || !eligible(user.get())) return;
    Instant now = Instant.now();
    var previous = tokens.byUser(user.get().id());
    if (previous.isPresent() && previous.get().createdAt().plus(cooldown).isAfter(now)) return;
    byte[] entropy = new byte[32];
    RANDOM.nextBytes(entropy);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
    Instant expiresAt = now.plus(ttl);
    tokens.replace(new PasswordResetToken(user.get().id(), hash(raw), now, expiresAt));
    try {
      sender.sendReset(user.get().email(), publicBaseUrl + "/reset-password?token=" + raw, expiresAt);
    } catch (RuntimeException failure) {
      // Roll back replacement; the REST boundary keeps the public response generic.
      throw new ExternalServiceUnavailableException("Password reset email delivery failed");
    }
  }

  @Transactional
  public void reset(String rawToken, String password, String confirmation) {
    if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) throw invalidLink();
    PasswordPolicy.validate(password);
    if (!password.equals(confirmation)) throw new ValidationException("Passwords do not match");
    String digest = hash(rawToken);
    var initial = tokens.byHash(digest).orElseThrow(PasswordResetService::invalidLink);
    var user = tokens.lockUser(initial.userId()).orElseThrow(PasswordResetService::invalidLink);
    var current = tokens.byHash(digest).orElseThrow(PasswordResetService::invalidLink);
    if (!eligible(user) || !current.expiresAt().isAfter(Instant.now())) throw invalidLink();
    tokens.changePasswordAndVersion(user.id(), BcryptUtil.bcryptHash(password));
    tokens.delete(user.id());
  }

  private static boolean eligible(User user) { return user.passwordHash() != null && user.emailVerifiedAt() != null; }
  private static ValidationException invalidLink() { return new ValidationException(INVALID_LINK); }
  private static String hash(String token) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 is unavailable", failure); }
  }
}
