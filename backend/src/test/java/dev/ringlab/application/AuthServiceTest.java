package dev.ringlab.application;

import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.application.auth.AuthService;
import dev.ringlab.domain.auth.User;
import dev.ringlab.port.out.UserRepository;
import io.quarkus.elytron.security.common.BcryptUtil;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {
  private InMemoryUserRepository users;
  private AuthService service;

  @BeforeEach
  void setUp() {
    users = new InMemoryUserRepository();
    service = new AuthService(users);
  }

  @Test
  void registrationNormalizesAccountAndHashesPassword() {
    User registered =
        service.register("Mixed_CASE", "Person@Example.COM", "password-123");

    assertSame(registered, users.lastCreated);
    assertEquals("mixed_case", registered.username());
    assertEquals("person@example.com", registered.email());
    assertNotEquals("password-123", registered.passwordHash());
    assertTrue(BcryptUtil.matches("password-123", registered.passwordHash()));
    assertNotNull(registered.id());
    assertNotNull(registered.createdAt());
  }

  @Test
  void duplicateUsernameOrEmailIsRejectedAfterNormalization() {
    users.duplicate = true;

    AppException error =
        assertThrows(
            AppException.class,
            () -> service.register("Existing_USER", "Existing@Example.COM", "password-123"));

    assertEquals(409, error.status);
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

    AppException error =
        assertThrows(AppException.class, () -> service.login("account", "wrong-password"));

    assertEquals(401, error.status);
    assertEquals("Invalid username or password", error.getMessage());
  }

  @Test
  void missingUserLoginIsRejected() {
    AppException error =
        assertThrows(AppException.class, () -> service.login("missing", "password-123"));

    assertEquals(401, error.status);
    assertEquals("Invalid username or password", error.getMessage());
  }

  @Test
  void rejectsPasswordOverBcryptUtf8ByteLimit() {
    String seventyFiveUtf8Bytes = "€".repeat(25);

    AppException error =
        assertThrows(
            AppException.class,
            () -> service.register("account", "account@example.com", seventyFiveUtf8Bytes));

    assertEquals(400, error.status);
    assertEquals("Password must be at most 72 UTF-8 bytes", error.getMessage());
    assertNull(users.checkedUsername);
    assertNull(users.lastCreated);
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
  }
}
