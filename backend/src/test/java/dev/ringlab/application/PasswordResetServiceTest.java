package dev.ringlab.application;

import dev.ringlab.application.auth.PasswordPolicy;
import dev.ringlab.application.auth.PasswordResetService;
import dev.ringlab.domain.auth.PasswordResetToken;
import dev.ringlab.domain.auth.User;
import dev.ringlab.port.out.*;
import io.quarkus.elytron.security.common.BcryptUtil;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordResetServiceTest {
  private User account = new User(UUID.randomUUID(), "recover", "recover@example.test", BcryptUtil.bcryptHash("old-password"), Instant.EPOCH);
  private final MemoryTokens tokens = new MemoryTokens();
  private String delivered;
  private int deliveries;
  private final UserRepository users = (UserRepository) Proxy.newProxyInstance(UserRepository.class.getClassLoader(),
      new Class<?>[]{UserRepository.class}, (proxy, method, args) -> {
        if (method.getName().equals("byEmail")) return Optional.ofNullable(account.email().equals(args[0]) ? account : null);
        throw new UnsupportedOperationException(method.getName());
      });

  private PasswordResetService service(Duration cooldown) {
    return new PasswordResetService(users, tokens, (email, url, expires) -> {
      assertEquals(account.email(), email);
      assertTrue(url.startsWith("http://localhost:5173/reset-password?token="));
      delivered = url.substring(url.indexOf("token=") + 6); deliveries++;
    }, Duration.ofMinutes(30), cooldown, "http://localhost:5173/");
  }

  @Test void eligibleRequestNormalizesEmailStoresOnlyDigestAndDoesNotChangeSessions() throws Exception {
    service(Duration.ZERO).request("RECOVER@EXAMPLE.TEST");
    assertEquals(32, Base64.getUrlDecoder().decode(delivered).length);
    assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(delivered.getBytes(StandardCharsets.UTF_8))), tokens.token.tokenHash());
    assertNotEquals(delivered, tokens.token.tokenHash());
    assertEquals(Duration.ofMinutes(30), Duration.between(tokens.token.createdAt(), tokens.token.expiresAt()));
    assertEquals(0, account.authVersion());
  }

  @Test void ineligibleRequestsDoNotCreateTokensOrMail() {
    var service = service(Duration.ZERO);
    for (String email : Arrays.asList(null, "", " ", "x".repeat(255), "missing@example.test")) service.request(email);
    account = new User(account.id(), account.username(), account.email(), null, Instant.EPOCH);
    service.request(account.email());
    account = new User(account.id(), account.username(), account.email(), "hash", null, Instant.EPOCH);
    service.request(account.email());
    assertNull(tokens.token); assertEquals(0, deliveries);
  }

  @Test void replacementAndSuccessfulConsumptionInvalidateEarlierLinks() {
    var service = service(Duration.ZERO);
    service.request(account.email()); String old = delivered;
    service.request(account.email()); String current = delivered;
    assertNotEquals(old, current);
    invalid(service, old);
    User before = account;
    service.reset(current, "new-password", "new-password");
    assertTrue(BcryptUtil.matches("new-password", account.passwordHash()));
    assertFalse(BcryptUtil.matches("old-password", account.passwordHash()));
    assertEquals(before.id(), account.id()); assertEquals(before.emailVerifiedAt(), account.emailVerifiedAt());
    assertEquals(1, account.authVersion()); assertNull(tokens.token);
    invalid(service, current);
  }

  @Test void cooldownBoundsPerAccountEmailAndReplacement() {
    var service = service(Duration.ofMinutes(1));
    service.request(account.email()); String first = delivered;
    service.request(account.email()); assertEquals(1, deliveries); assertEquals(first, delivered);
    tokens.token = new PasswordResetToken(account.id(), tokens.token.tokenHash(), Instant.now().minusSeconds(61), Instant.now().plusSeconds(600));
    service.request(account.email()); assertEquals(2, deliveries); assertNotEquals(first, delivered);
  }

  @Test void expiredMalformedMissingAndNoLongerEligibleLinksAreSafe() {
    var service = service(Duration.ZERO);
    for (String token : Arrays.asList(null, "", "abc", "!".repeat(43), "x".repeat(1000), "a".repeat(43))) invalid(service, token);
    service.request(account.email());
    tokens.token = new PasswordResetToken(account.id(), tokens.token.tokenHash(), Instant.EPOCH, Instant.now().minusSeconds(1));
    invalid(service, delivered);
    tokens.token = new PasswordResetToken(account.id(), tokens.token.tokenHash(), Instant.now(), Instant.now().plusSeconds(100));
    account = new User(account.id(), account.username(), account.email(), null, Instant.EPOCH);
    invalid(service, delivered);
  }

  @Test void passwordPolicyAndConfirmationLeaveTokenUsable() {
    var service = service(Duration.ZERO); service.request(account.email());
    assertEquals("Passwords do not match", assertThrows(ValidationException.class,
        () -> service.reset(delivered, "new-password", "different")).getMessage());
    for (String bad : Arrays.asList(null, "short", "a".repeat(73), "€".repeat(25))) {
      assertThrows(ValidationException.class, () -> service.reset(delivered, bad, bad));
      assertNotNull(tokens.token); assertEquals(0, account.authVersion());
    }
    for (String good : List.of("        ", "a".repeat(72), "€".repeat(24))) assertDoesNotThrow(() -> PasswordPolicy.validate(good));
    service.reset(delivered, "€".repeat(24), "€".repeat(24));
    assertTrue(BcryptUtil.matches("€".repeat(24), account.passwordHash()));
  }

  @Test void deliveryErrorsContainNoProviderDetails() {
    var service = new PasswordResetService(users, tokens, (email, url, expiry) -> {
      throw new IllegalStateException("secret SMTP details");
    }, Duration.ofMinutes(30), Duration.ZERO, "http://localhost:5173");
    var failure = assertThrows(ExternalServiceUnavailableException.class, () -> service.request(account.email()));
    assertEquals("Password reset email delivery failed", failure.getMessage());
    assertNull(failure.getCause()); // Actual rollback is covered against PostgreSQL.
  }

  @Test void unsafeLifetimesFailConfiguration() {
    for (Duration ttl : List.of(Duration.ZERO, Duration.ofSeconds(-1), Duration.ofDays(2)))
      assertThrows(IllegalArgumentException.class, () -> new PasswordResetService(users, tokens, null, ttl, Duration.ZERO, "http://localhost"));
    assertThrows(IllegalArgumentException.class, () -> service(Duration.ofHours(1)));
    assertThrows(IllegalArgumentException.class, () -> service(Duration.ofSeconds(-1)));
  }

  private void invalid(PasswordResetService service, String token) {
    assertEquals(PasswordResetService.INVALID_LINK, assertThrows(ValidationException.class,
        () -> service.reset(token, "new-password", "new-password")).getMessage());
  }

  private class MemoryTokens implements PasswordResetRepository {
    PasswordResetToken token;
    public Optional<User> lockUser(UUID id) { return Optional.of(account); }
    public Optional<PasswordResetToken> byHash(String hash) { return Optional.ofNullable(token).filter(t -> t.tokenHash().equals(hash)); }
    public Optional<PasswordResetToken> byUser(UUID id) { return Optional.ofNullable(token); }
    public void replace(PasswordResetToken replacement) { token = replacement; }
    public void changePasswordAndVersion(UUID id, String hash) {
      account = new User(account.id(), account.username(), account.email(), hash, account.emailVerifiedAt(), account.createdAt(), account.authVersion() + 1);
    }
    public void delete(UUID id) { token = null; }
  }
}
