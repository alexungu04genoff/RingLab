package dev.ringlab.application;

import dev.ringlab.application.auth.ExternalAuthService;
import dev.ringlab.domain.auth.*;
import dev.ringlab.port.out.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExternalAuthServiceTest {
  private final Users users = new Users();
  private final Identities identities = new Identities();
  private final StubProfanityPolicy profanity = new StubProfanityPolicy();

  private ExternalAuthService service(String subject, String email) {
    return new ExternalAuthService(credential ->
        new VerifiedExternalIdentity("GOOGLE", subject, email, "Display name"), identities, users,
        profanity);
  }

  @Test
  void firstLoginCreatesPasswordlessAccountAndRepeatedLoginUsesSubjectEvenAfterEmailChanges() {
    var first = service("stable-subject", "Alex.Smith@example.test").login("credential");
    assertEquals("alexsmith", first.username());
    assertEquals("alex.smith@example.test", first.email());
    assertNull(first.passwordHash());
    assertEquals(first.id(), identities.find("GOOGLE", "stable-subject").orElseThrow().userId());
    assertEquals(first, service("stable-subject", "changed@example.test").login("next-credential"));
    assertEquals(1, users.values.size());
    assertEquals(1, identities.values.size());
  }

  @Test
  void existingLocalEmailCannotBeLinkedImplicitly() {
    var local = new User(UUID.randomUUID(), "local", "alex@example.test", "hash", Instant.now());
    users.create(local);
    var error = assertThrows(AlreadyExistsException.class,
        () -> service("new-subject", "Alex@example.test").login("credential"));
    assertEquals(
        "An account already exists with this email. Sign in with your username and password, "
            + "then link Google from your account page.",
        error.getMessage());
    assertEquals(List.of(local), new ArrayList<>(users.values.values()));
    assertTrue(identities.values.isEmpty());
  }

  @Test
  void signedInUserCanLinkMatchingGoogleIdentityAndUseItForLaterLogin() {
    var local = new User(UUID.randomUUID(), "local", "alex@example.test", "hash", Instant.now());
    users.create(local);

    service("stable-subject", "Alex@example.test").link(local.id(), "credential");

    assertEquals(local.id(), identities.find("GOOGLE", "stable-subject").orElseThrow().userId());
    assertEquals(local, service("stable-subject", "changed@example.test").login("credential"));
  }

  @Test
  void linkingIsIdempotentForTheSameUser() {
    var local = new User(UUID.randomUUID(), "local", "alex@example.test", "hash", Instant.now());
    users.create(local);
    var service = service("stable-subject", "alex@example.test");

    service.link(local.id(), "credential");
    service.link(local.id(), "credential");

    assertEquals(1, identities.values.size());
  }

  @Test
  void linkRejectsMismatchedEmailAndIdentityOwnedByAnotherUser() {
    var local = new User(UUID.randomUUID(), "local", "alex@example.test", "hash", Instant.now());
    var other = new User(UUID.randomUUID(), "other", "other@example.test", "hash", Instant.now());
    users.create(local);
    users.create(other);

    var mismatch = assertThrows(ForbiddenException.class,
        () -> service("new-subject", "different@example.test").link(local.id(), "credential"));
    assertEquals("Google account email must match your RingLab email.", mismatch.getMessage());
    assertTrue(identities.values.isEmpty());

    service("owned-subject", "other@example.test").link(other.id(), "credential");
    var owned = assertThrows(AlreadyExistsException.class,
        () -> service("owned-subject", "alex@example.test").link(local.id(), "credential"));
    assertEquals("This Google account is already linked to another RingLab account.", owned.getMessage());
    assertEquals(1, identities.values.size());
  }

  @Test
  void generatedUsernamesHandleCollisionsShortUnicodeAndLongLocalParts() {
    users.create(new User(UUID.randomUUID(), "alex", "other@example.test", "hash", Instant.now()));
    var collision = service("one", "alex@example.test").login("credential");
    assertTrue(collision.username().matches("alex_[a-f0-9]{8}"));
    for (String local : List.of("x", "éé", "a".repeat(64), "a.+b")) {
      var created = service(local, local + "@example.test").login("credential");
      assertTrue(created.username().matches("[a-z0-9_]{3,30}"));
      assertFalse(created.username().contains("@"));
    }
  }

  @Test
  void profaneGoogleLocalPartUsesSafeGeneratedUsername() {
    profanity.blocked.add("crap");

    var created = service("safe-subject", "crap@example.test").login("credential");

    assertEquals("user", created.username());
    assertEquals("crap@example.test", created.email());
    assertEquals(List.of(
        new StubProfanityPolicy.Check("crap", null),
        new StubProfanityPolicy.Check("user", null)), profanity.checks);
  }

  @Test
  void failedVerificationNeverWritesAccounts() {
    var service = new ExternalAuthService(credential -> {
      throw new AuthenticationException("Invalid Google credential");
    }, identities, users, profanity);
    for (String credential : Arrays.asList(null, "", "bad-token", "x".repeat(16385)))
      assertThrows(AuthenticationException.class, () -> service.login(credential));
    assertTrue(users.values.isEmpty());
    assertTrue(identities.values.isEmpty());
  }

  @Test
  void usernameCollisionLimitFailsBeforeCreatingEitherRecord() {
    int[] lookups = {0};
    var occupied = new User(UUID.randomUUID(), "occupied", "other@example.test", "hash", Instant.EPOCH);
    var unavailable = new Users() {
      @Override public Optional<User> byUsername(String username) {
        lookups[0]++;
        return Optional.of(occupied);
      }
    };
    var service = new ExternalAuthService(credential ->
        new VerifiedExternalIdentity("GOOGLE", "subject", "alex@example.test", "Alex"),
        identities, unavailable, profanity);

    var error = assertThrows(AlreadyExistsException.class, () -> service.login("credential"));

    assertEquals("Could not reserve a username. Please try again.", error.getMessage());
    assertEquals(11, lookups[0]);
    assertTrue(unavailable.values.isEmpty());
    assertTrue(identities.values.isEmpty());
  }

  private static class Users implements UserRepository {
    final Map<UUID, User> values = new LinkedHashMap<>();
    public Optional<User> byId(UUID id) { return Optional.ofNullable(values.get(id)); }
    public Optional<User> byUsername(String username) {
      return values.values().stream().filter(u -> u.username().equals(username)).findFirst();
    }
    public Optional<User> byEmail(String email) {
      return values.values().stream().filter(u -> u.email().equals(email)).findFirst();
    }
    public boolean exists(String username, String email) {
      return values.values().stream().anyMatch(u -> u.username().equals(username) || u.email().equals(email));
    }
    public void create(User user) {
      if (exists(user.username(), user.email())) throw new AlreadyExistsException("Duplicate account");
      values.put(user.id(), user);
    }
    public void markEmailVerified(UUID userId, Instant verifiedAt) {
      User user = values.get(userId);
      values.put(userId, new User(user.id(), user.username(), user.email(), user.passwordHash(), verifiedAt, user.createdAt()));
    }
  }

  private static class Identities implements ExternalIdentityRepository {
    final Map<String, ExternalIdentity> values = new HashMap<>();
    public Optional<ExternalIdentity> find(String provider, String subject) {
      return Optional.ofNullable(values.get(provider + ":" + subject));
    }
    public void create(ExternalIdentity identity) {
      if (values.putIfAbsent(identity.provider() + ":" + identity.subject(), identity) != null)
        throw new AlreadyExistsException("Duplicate identity");
    }
  }
}
