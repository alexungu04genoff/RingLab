package dev.ringlab.application;

import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.application.auth.AuthService;
import dev.ringlab.application.auth.EmailVerificationService;
import dev.ringlab.domain.auth.User;
import dev.ringlab.port.out.UserRepository;
import dev.ringlab.port.out.EmailVerificationTokenRepository;
import dev.ringlab.domain.auth.EmailVerificationToken;
import io.quarkus.elytron.security.common.BcryptUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
  private static final jakarta.validation.ValidatorFactory VALIDATORS =
      jakarta.validation.Validation.byDefaultProvider().configure()
          .messageInterpolator(new org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator())
          .buildValidatorFactory();

  @org.junit.jupiter.api.AfterAll
  static void closeValidators() {
    VALIDATORS.close();
  }
  private InMemoryUserRepository users;
  private AuthService service;
  private EmailVerificationService verification;
  private InMemoryVerificationTokens verificationTokens;
  private StubProfanityPolicy profanity;

  @BeforeEach
  void setUp() {
    users = new InMemoryUserRepository();
    verificationTokens = new InMemoryVerificationTokens();
    profanity = new StubProfanityPolicy();
    verification = new EmailVerificationService(users, verificationTokens);
    service = new AuthService(users, verification, VALIDATORS.getValidator(), profanity);
  }

  @Test
  void registrationNormalizesAccountAndHashesPassword() throws Exception {
    var verification =
        service.register("Mixed_CASE", "Person@Example.COM", "password-123");
    User registered = users.lastCreated;

    assertSame(registered, users.lastCreated);
    assertEquals("mixed_case", registered.username());
    assertEquals("person@example.com", registered.email());
    assertNotEquals("password-123", registered.passwordHash());
    assertTrue(BcryptUtil.matches("password-123", registered.passwordHash()));
    assertNotNull(registered.id());
    assertNotNull(registered.createdAt());
    assertNull(registered.emailVerifiedAt());
    assertNotEquals(verification.token(), verificationTokens.lastStored.tokenHash());
    assertEquals(32, Base64.getUrlDecoder().decode(verification.token()).length);
    assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(verification.token().getBytes(StandardCharsets.UTF_8))), verificationTokens.lastStored.tokenHash());
    assertEquals(registered.id(), verificationTokens.lastStored.userId());
    assertEquals(registered.createdAt(), verificationTokens.lastStored.createdAt());
    assertEquals(registered.createdAt().plus(Duration.ofHours(24)), verificationTokens.lastStored.expiresAt());
  }

  @Test
  void registrationRejectsProfaneUsernameBeforePersistence() {
    profanity.blocked.add("crap");

    var error = assertThrows(ValidationException.class,
        () -> service.register("CRAP", "person@example.com", "password-123"));

    assertEquals("Text contains inappropriate language", error.getMessage());
    assertNull(users.lastCreated);
    assertNull(users.checkedUsername);
    assertEquals(List.of(new StubProfanityPolicy.Check("crap", null)), profanity.checks);
  }

  @Test
  void duplicateUsernameOrEmailIsRejectedAfterNormalization() {
    users.duplicate = true;

    AlreadyExistsException error =
        assertThrows(
            AlreadyExistsException.class,
            () -> service.register("Existing_USER", "Existing@Example.COM", "password-123"));

    assertEquals("Username or email already registered", error.getMessage());
    assertEquals("existing_user", users.checkedUsername);
    assertEquals("existing@example.com", users.checkedEmail);
    assertNull(users.lastCreated);
  }

  @Test
  void loginIsCaseInsensitiveForUsernameAndChecksPassword() {
    User user = user("account", "correct-password");
    users.usersByUsername.put(user.username(), user);

    assertEquals(user, service.login("ACCOUNT", "correct-password"));
  }

  @Test
  void wrongPasswordIsRejected() {
    User user = user("account", "correct-password");
    users.usersByUsername.put(user.username(), user);

    AuthenticationException error =
        assertThrows(AuthenticationException.class, () -> service.login("account", "wrong-password"));

    assertEquals("Invalid username or password", error.getMessage());
  }

  @Test
  void unverifiedAccountWithCorrectPasswordCannotLogInUntilItsTokenIsUsed() {
    var delivery = service.register("account", "account@example.com", "correct-password");

    var rejected = assertThrows(ForbiddenException.class, () -> service.login("account", "correct-password"));
    assertEquals("Please verify your email before signing in.", rejected.getMessage());

    verification.verifyEmail(delivery.token());
    assertNotNull(users.byUsername("account").orElseThrow().emailVerifiedAt());
    assertEquals(users.byUsername("account").orElseThrow(), service.login("account", "correct-password"));
    assertThrows(ValidationException.class, () -> verification.verifyEmail(delivery.token()));
  }

  @Test
  void wrongPasswordDoesNotRevealUnverifiedStatus() {
    service.register("account", "account@example.com", "correct-password");
    var error = assertThrows(AuthenticationException.class, () -> service.login("account", "wrong-password"));
    assertEquals("Invalid username or password", error.getMessage());
  }

  @Test
  void resendReplacesTheOldToken() {
    var first = service.register("account", "account@example.com", "correct-password");
    var second = verification.resendVerification("account@example.com").orElseThrow();
    assertThrows(ValidationException.class, () -> verification.verifyEmail(first.token()));
    verification.verifyEmail(second.token());
  }

  @Test
  void resendDoesNotRevealMissingVerifiedOrExternalAccounts() {
    User verified = user("verified", "password-123");
    users.create(verified);
    users.create(new User(UUID.randomUUID(), "external", "external@example.test", null, Instant.now()));

    assertTrue(verification.resendVerification("missing@example.test").isEmpty());
    assertTrue(verification.resendVerification("verified@example.com").isEmpty());
    assertTrue(verification.resendVerification("external@example.test").isEmpty());
    for (String invalid : Arrays.asList(null, "", " ", "x".repeat(255))) {
      assertTrue(verification.resendVerification(invalid).isEmpty());
    }
    assertNull(verificationTokens.lastStored);
  }

  @Test
  void resendNormalizesEmailAndIssuesANewTokenForAnUnverifiedLocalAccount() {
    service.register("account", "account@example.com", "password-123");

    var delivery = verification.resendVerification("ACCOUNT@EXAMPLE.COM").orElseThrow();

    assertEquals("account@example.com", delivery.email());
    assertNotEquals(delivery.token(), verificationTokens.lastStored.tokenHash());
  }

  @Test
  void expiredTokenCannotVerifyAccount() {
    var delivery = service.register("account", "account@example.com", "correct-password");
    var stored = verificationTokens.lastStored;
    verificationTokens.replace(new EmailVerificationToken(stored.userId(), stored.tokenHash(),
        java.time.Instant.now().minusSeconds(1), stored.createdAt()));
    assertThrows(ValidationException.class, () -> verification.verifyEmail(delivery.token()));
    assertNull(users.byUsername("account").orElseThrow().emailVerifiedAt());
    assertThrows(ForbiddenException.class, () -> service.login("account", "correct-password"));
  }

  @Test
  void missingUserLoginIsRejected() {
    AuthenticationException error =
        assertThrows(AuthenticationException.class, () -> service.login("missing", "password-123"));

    assertEquals("Invalid username or password", error.getMessage());
  }

  @Test
  void malformedVerificationTokensAreRejectedBeforeRepositoryLookup() {
    for (String token : Arrays.asList(null, "", " ", "x".repeat(513))) {
      var error = assertThrows(ValidationException.class, () -> verification.verifyEmail(token));
      assertEquals("Verification link is invalid or expired", error.getMessage());
    }
    assertNull(verificationTokens.lastLookupHash);
  }

  @Test
  void currentReturnsTheAccountOrReportsThatItNoLongerExists() {
    User existing = user("account", "password-123");
    users.create(existing);

    assertEquals(existing, service.current(existing.id()));
    assertThrows(NotFoundException.class, () -> service.current(UUID.randomUUID()));
  }

  @Test
  void rejectsPasswordOverBcryptUtf8ByteLimit() {
    String seventyFiveUtf8Bytes = "€".repeat(25);

    ValidationException error =
        assertThrows(
            ValidationException.class,
            () -> service.register("account", "account@example.com", seventyFiveUtf8Bytes));

    assertEquals("Password must be at most 72 UTF-8 bytes", error.getMessage());
    assertNull(users.checkedUsername);
    assertNull(users.lastCreated);
  }

  @Test
  void acceptedPasswordsCanLogInWithoutTrimming() {
    for (String password : List.of("        ", " password ", "x".repeat(72), "€".repeat(24))) {
      var delivery = service.register("account", "account@example.com", password);
      verification.verifyEmail(delivery.token());
      User registered = users.lastCreated;
      assertEquals(registered.id(), service.login("ACCOUNT", password).id());
      assertTrue(BcryptUtil.matches(password, registered.passwordHash()));
    }
  }

  @Test
  void rejectsInvalidRegistrationInputsBeforePersistence() {
    for (String username : Arrays.asList(null, "", "ab", "bad-name", "a".repeat(31))) {
      assertThrows(ValidationException.class,
          () -> service.register(username, "account@example.com", "password-123"));
    }
    for (String email : Arrays.asList(null, "", " ", "not-an-email", "a".repeat(255))) {
      assertThrows(ValidationException.class,
          () -> service.register("account", email, "password-123"));
    }
    for (String password : Arrays.asList(null, "", "short", "x".repeat(73))) {
      assertThrows(ValidationException.class,
          () -> service.register("account", "account@example.com", password));
    }
    assertNull(users.lastCreated);
    assertNull(users.checkedUsername);
  }

  @Test
  void loginValidatesNullAndLengthWithoutApplyingNewRegistrationMinimums() {
    for (String username : Arrays.asList(null, "", " ", "x".repeat(31))) {
      assertThrows(ValidationException.class, () -> service.login(username, "password-123"));
    }
    for (String password : Arrays.asList(null, "", "x".repeat(73))) {
      assertThrows(ValidationException.class, () -> service.login("account", password));
    }
    User legacy = user("account", "short");
    users.create(legacy);
    assertEquals(legacy, service.login("account", "short"));
  }

  @Test
  void externalAccountCannotLoginWithAnyPasswordIncludingDummyPassword() {
    users.create(new User(UUID.randomUUID(), "external", "external@example.test", null, Instant.now()));
    for (String password : List.of("password-123", "ringlab-dummy-password")) {
      var error = assertThrows(AuthenticationException.class, () -> service.login("external", password));
      assertEquals("Invalid username or password", error.getMessage());
    }
  }

  private static User user(String username, String password) {
    return new User(
        UUID.randomUUID(),
        username,
        username + "@example.com",
        BcryptUtil.bcryptHash(password),
        Instant.parse("2026-01-01T00:00:00Z"));
  }

  private static final class InMemoryUserRepository implements UserRepository {
    private final Map<UUID, User> usersById = new HashMap<>();
    private final Map<String, User> usersByUsername = new HashMap<>();
    private boolean duplicate;
    private String checkedUsername;
    private String checkedEmail;
    private User lastCreated;

    @Override
    public Optional<User> byId(UUID id) {
      return Optional.ofNullable(usersById.get(id));
    }

    @Override
    public Optional<User> byUsername(String username) {
      return Optional.ofNullable(usersByUsername.get(username));
    }

    @Override
    public Optional<User> byEmail(String email) {
      return usersByUsername.values().stream().filter(user -> user.email().equals(email)).findFirst();
    }

    @Override
    public boolean exists(String username, String email) {
      checkedUsername = username;
      checkedEmail = email;
      return duplicate;
    }

    @Override
    public void create(User user) {
      lastCreated = user;
      usersById.put(user.id(), user);
      usersByUsername.put(user.username(), user);
    }

    @Override
    public void markEmailVerified(UUID userId, Instant verifiedAt) {
      User old = usersById.get(userId);
      User updated = new User(old.id(), old.username(), old.email(), old.passwordHash(), verifiedAt, old.createdAt());
      usersById.put(userId, updated);
      usersByUsername.put(updated.username(), updated);
    }
  }

  private static final class InMemoryVerificationTokens implements EmailVerificationTokenRepository {
    private final Map<String, EmailVerificationToken> byHash = new HashMap<>();
    private EmailVerificationToken lastStored;
    private String lastLookupHash;

    @Override public void replace(EmailVerificationToken token) {
      byHash.values().removeIf(existing -> existing.userId().equals(token.userId()));
      byHash.put(token.tokenHash(), token);
      lastStored = token;
    }
    @Override public Optional<EmailVerificationToken> byTokenHashForUpdate(String hash) {
      lastLookupHash = hash;
      return Optional.ofNullable(byHash.get(hash));
    }
    @Override public void delete(UUID userId) {
      byHash.values().removeIf(token -> token.userId().equals(userId));
    }
  }
}
