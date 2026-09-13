package dev.ringlab.application.auth;

import lombok.RequiredArgsConstructor;

import dev.ringlab.domain.auth.User;
import dev.ringlab.application.AuthenticationException;
import dev.ringlab.application.AlreadyExistsException;
import dev.ringlab.application.NotFoundException;
import dev.ringlab.application.ValidationException;
import dev.ringlab.application.validation.ProfanityPolicy;
import dev.ringlab.port.out.UserRepository;
import dev.ringlab.port.out.EmailVerificationTokenRepository;
import dev.ringlab.domain.auth.EmailVerificationToken;
import dev.ringlab.application.ForbiddenException;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.Duration;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.*;

@ApplicationScoped
@RequiredArgsConstructor
public class AuthService {
  private final UserRepository users;
  private final EmailVerificationTokenRepository verificationTokens;
  private final Validator validator;
  private final ProfanityPolicy profanity;

  private record RegistrationInput(
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,30}") String username,
      @NotBlank @Email @Size(max = 254) String email,
      @NotNull @Size(min = 8, max = 72) String password) {}

  private record LoginInput(
      @NotBlank @Size(max = 30) String username,
      @NotNull @Size(min = 1, max = 72) String password) {}

  private void validateInput(Object input) {
    validator.validate(input).stream()
        .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
        .findFirst().ifPresent(violation -> {
          throw new ValidationException(violation.getPropertyPath() + ": " + violation.getMessage());
        });
  }
  private final String dummyHash = BcryptUtil.bcryptHash("ringlab-dummy-password");
  private static final SecureRandom TOKEN_RANDOM = new SecureRandom();
  private static final Duration TOKEN_LIFETIME = Duration.ofHours(24);
  public record VerificationEmail(String email, String token) {}

  @Transactional
  public VerificationEmail register(String username, String email, String password) {
    validateInput(new RegistrationInput(username, email, password));
    username = username.toLowerCase(Locale.ROOT);
    email = email.toLowerCase(Locale.ROOT);
    profanity.requireClean(username);
    if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new ValidationException("Password must be at most 72 UTF-8 bytes");
    if (users.exists(username, email))
      throw new AlreadyExistsException("Username or email already registered");
    Instant now = Instant.now();
    User user = new User(UUID.randomUUID(), username, email, BcryptUtil.bcryptHash(password), null, now);
    users.create(user);
    return issueVerification(user, now);
  }

  public User login(String username, String password) {
    validateInput(new LoginInput(username, password));
    var user = users.byUsername(username.toLowerCase(Locale.ROOT));
    boolean valid = BcryptUtil.matches(password, user.map(User::passwordHash).orElse(dummyHash));
    if (user.isEmpty() || user.get().passwordHash() == null || !valid)
      throw new AuthenticationException("Invalid username or password");
    if (user.get().emailVerifiedAt() == null)
      throw new ForbiddenException("Please verify your email before signing in.");
    return user.get();
  }

  @Transactional
  public void verifyEmail(String rawToken) {
    if (rawToken == null || rawToken.isBlank() || rawToken.length() > 512)
      throw invalidVerificationToken();
    var token = verificationTokens.byTokenHashForUpdate(hash(rawToken))
        .orElseThrow(this::invalidVerificationToken);
    if (!token.expiresAt().isAfter(Instant.now())) {
      verificationTokens.delete(token.userId());
      throw invalidVerificationToken();
    }
    users.markEmailVerified(token.userId(), Instant.now());
    verificationTokens.delete(token.userId());
  }

  @Transactional
  public Optional<VerificationEmail> resendVerification(String email) {
    if (email == null || email.isBlank() || email.length() > 254) return Optional.empty();
    return users.byEmail(email.toLowerCase(Locale.ROOT))
        .filter(user -> user.passwordHash() != null && user.emailVerifiedAt() == null)
        .map(user -> issueVerification(user, Instant.now()));
  }

  private VerificationEmail issueVerification(User user, Instant now) {
    String rawToken = newToken();
    verificationTokens.replace(new EmailVerificationToken(user.id(), hash(rawToken), now.plus(TOKEN_LIFETIME), now));
    return new VerificationEmail(user.email(), rawToken);
  }

  private static String newToken() {
    byte[] bytes = new byte[32];
    TOKEN_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hash(String token) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private ValidationException invalidVerificationToken() {
    return new ValidationException("Verification link is invalid or expired");
  }

  public User current(UUID id) {
    return users.byId(id).orElseThrow(() -> NotFoundException.missing("User"));
  }
}
