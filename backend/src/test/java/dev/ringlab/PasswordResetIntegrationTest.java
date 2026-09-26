package dev.ringlab;

import dev.ringlab.adapter.out.mail.QuarkusPasswordResetSender;
import dev.ringlab.application.ValidationException;
import dev.ringlab.application.auth.PasswordResetService;
import dev.ringlab.domain.auth.*;
import dev.ringlab.port.out.*;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.*;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(PasswordResetIntegrationTest.Profile.class)
class PasswordResetIntegrationTest {
  public static class Profile implements QuarkusTestProfile {
    public Map<String, String> getConfigOverrides() {
      return Map.of("quarkus.mailer.mock", "true", "ringlab.password-reset.cooldown", "PT0S",
          "ringlab.rate-limit.forgot-password", "1000/PT1H", "ringlab.rate-limit.reset-password", "1000/PT1M");
    }
    public Set<Class<?>> getEnabledAlternatives() { return Set.of(TestSender.class, GoogleAuthIntegrationTest.OfflineVerifier.class); }
  }

  @Alternative @ApplicationScoped
  public static class TestSender implements PasswordResetSender {
    @Inject QuarkusPasswordResetSender delegate;
    volatile boolean fail;
    public void setFail(boolean fail) { this.fail = fail; }
    public void sendReset(String email, String url, Instant expiresAt) {
      if (fail) throw new IllegalStateException("Simulated unavailable SMTP");
      delegate.sendReset(email, url, expiresAt);
    }
  }

  @Inject UserRepository users;
  @Inject PasswordResetRepository tokens;
  @Inject PasswordResetService service;
  @Inject ExternalIdentityRepository identities;
  @Inject MockMailbox mailbox;
  @Inject TestSender sender;
  @Inject EntityManager em;
  private final List<UUID> created = new ArrayList<>();

  @AfterEach void cleanup() {
    sender.setFail(false);
    QuarkusTransaction.requiringNew().run(() -> {
      for (UUID id : created) {
        em.createNativeQuery("delete from builds where author_id=:id").setParameter("id", id).executeUpdate();
        em.createNativeQuery("delete from users where id=:id").setParameter("id", id).executeUpdate();
      }
    });
    mailbox.clear();
  }

  private User account(boolean local, boolean verified) {
    UUID id = UUID.randomUUID();
    User user = new User(id, "reset_" + id.toString().substring(0, 20).replace("-", ""), id + "@example.test",
        local ? BcryptUtil.bcryptHash("old-password") : null, verified ? Instant.EPOCH : null, Instant.EPOCH);
    QuarkusTransaction.requiringNew().run(() -> users.create(user)); created.add(id); return user;
  }

  private String forgot(String email) {
    return given().contentType("application/json").body(Map.of("email", email)).post("/api/auth/forgot-password")
        .then().statusCode(200).extract().asString();
  }
  private String resetToken(User user) {
    var mail = mailbox.getMailsSentTo(user.email()).getLast();
    assertEquals("Reset your RingLab password", mail.getSubject());
    var matcher = Pattern.compile("/reset-password\\?token=([A-Za-z0-9_-]{43})").matcher(mail.getText());
    assertTrue(matcher.find(), "Expected a reset link in the mocked message"); return matcher.group(1);
  }
  private io.restassured.response.ValidatableResponse reset(String token) {
    return given().contentType("application/json").body(Map.of("token", token, "password", "new-password", "confirmPassword", "new-password"))
        .post("/api/auth/reset-password").then();
  }
  private String login(User user, String password) {
    return given().contentType("application/json").body(Map.of("username", user.username(), "password", password))
        .post("/api/auth/login").then().statusCode(200).extract().path("token");
  }

  @Test void resetInvalidatesLocalGoogleAndLegacySessionsWhileKeepingAllAccountData() {
    User user = account(true, true);
    String credential = "test-" + UUID.randomUUID();
    UUID build = UUID.randomUUID();
    QuarkusTransaction.requiringNew().run(() -> {
      identities.create(new ExternalIdentity(user.id(), "GOOGLE", credential, Instant.EPOCH));
      em.createNativeQuery("insert into builds(id,title,description,author_id,racer_id,front_part_id,rear_part_id,tire_part_id,created_at,updated_at) "
          + "select :id,'Recovery fixture','',:author,(select id from racers limit 1),"
          + "(select id from machine_parts where part_type='FRONT' limit 1),(select id from machine_parts where part_type='REAR' limit 1),"
          + "(select id from machine_parts where part_type='TIRE' limit 1),now(),now()")
          .setParameter("id", build).setParameter("author", user.id()).executeUpdate();
      em.createNativeQuery("insert into votes(id,user_id,build_id,value) values (:id,:user,:build,1)")
          .setParameter("id", UUID.randomUUID()).setParameter("user", user.id()).setParameter("build", build).executeUpdate();
      em.createNativeQuery("insert into comments(id,author_id,build_id,text,created_at) values (:id,:user,:build,'Kept comment',now())")
          .setParameter("id", UUID.randomUUID()).setParameter("user", user.id()).setParameter("build", build).executeUpdate();
      em.createNativeQuery("insert into saved_builds(user_id,build_id,saved_at) values (:user,:build,now())")
          .setParameter("user", user.id()).setParameter("build", build).executeUpdate();
    });
    var before = preservedRows(user.id(), build);
    String oldSession = login(user, "old-password");
    String legacySession = Jwt.subject(user.id().toString()).upn(user.username()).groups("user").sign();
    String googleSession = given().contentType("application/json").body(Map.of("credential", credential))
        .post("/api/auth/google").then().statusCode(200).extract().path("token");
    forgot(user.email());
    for (String session : List.of(oldSession, legacySession, googleSession))
      given().auth().oauth2(session).get("/api/auth/me").then().statusCode(200);
    String token = resetToken(user);
    reset(token).statusCode(200);
    for (String session : List.of(oldSession, legacySession, googleSession)) {
      given().auth().oauth2(session).get("/api/auth/me").then().statusCode(401);
      given().auth().oauth2(session).get("/api/saved-builds").then().statusCode(401);
    }
    given().contentType("application/json").body(Map.of("username", user.username(), "password", "old-password"))
        .post("/api/auth/login").then().statusCode(401);
    given().auth().oauth2(login(user, "new-password")).get("/api/auth/me").then().statusCode(200).body("id", equalTo(user.id().toString()));
    String newGoogle = given().contentType("application/json").body(Map.of("credential", credential))
        .post("/api/auth/google").then().statusCode(200).body("user.id", equalTo(user.id().toString())).extract().path("token");
    given().auth().oauth2(newGoogle).get("/api/auth/me").then().statusCode(200);
    reset(token).statusCode(400).body("message", equalTo(PasswordResetService.INVALID_LINK));
    assertEquals(before, preservedRows(user.id(), build));
    QuarkusTransaction.requiringNew().run(() -> {
      User after = users.byId(user.id()).orElseThrow();
      assertEquals(user.username(), after.username()); assertEquals(user.email(), after.email());
      assertEquals(user.createdAt(), after.createdAt()); assertEquals(user.emailVerifiedAt(), after.emailVerifiedAt());
      assertEquals(1, after.authVersion()); assertTrue(tokens.byUser(user.id()).isEmpty());
    });
  }

  private List<?> preservedRows(UUID user, UUID build) {
    return QuarkusTransaction.requiringNew().call(() -> em.createNativeQuery(
        "select row_to_json(b)::text from builds b where id=:build union all "
        + "select row_to_json(v)::text from votes v where build_id=:build union all "
        + "select row_to_json(c)::text from comments c where build_id=:build union all "
        + "select row_to_json(s)::text from saved_builds s where user_id=:user union all "
        + "select row_to_json(e)::text from external_identities e where user_id=:user")
        .setParameter("user", user).setParameter("build", build).getResultList());
  }

  @Test void ineligibleResponsesMatchAndFailedDeliveryRollsBackReplacement() {
    User local = account(true, true), google = account(false, true), unverified = account(true, false);
    String response = forgot(local.email()); String first = resetToken(local);
    assertEquals(response, forgot(UUID.randomUUID() + "@example.test"));
    assertEquals(response, forgot(google.email())); assertEquals(response, forgot(unverified.email()));
    assertTrue(mailbox.getMailsSentTo(google.email()).isEmpty()); assertTrue(mailbox.getMailsSentTo(unverified.email()).isEmpty());
    sender.setFail(true);
    assertEquals(response, forgot(local.email()));
    reset(first).statusCode(200);
    assertEquals(response, forgot(local.email()));
    QuarkusTransaction.requiringNew().run(() -> {
      for (User user : List.of(local, google, unverified)) assertTrue(tokens.byUser(user.id()).isEmpty());
      assertNull(users.byId(google.id()).orElseThrow().passwordHash());
      assertNull(users.byId(unverified.id()).orElseThrow().emailVerifiedAt());
    });
  }

  @Test void replacementExpiryAndConcurrentConsumptionAreAtomic() throws Exception {
    User user = account(true, true);
    forgot(user.email()); String previous = resetToken(user);
    forgot(user.email()); String token = resetToken(user);
    reset(previous).statusCode(400).body("message", equalTo(PasswordResetService.INVALID_LINK));
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var attempts = new ArrayList<Future<Boolean>>();
      for (int i = 0; i < 2; i++) attempts.add(pool.submit(() -> {
        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Start barrier timed out");
        try { service.reset(token, "new-password", "new-password"); return true; }
        catch (ValidationException failure) { assertEquals(PasswordResetService.INVALID_LINK, failure.getMessage()); return false; }
      }));
      start.countDown();
      int successes = 0;
      for (var attempt : attempts) if (attempt.get(15, TimeUnit.SECONDS)) successes++;
      assertEquals(1, successes);
    }
    forgot(user.email()); String expired = resetToken(user);
    QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
        "update password_reset_tokens set created_at=now()-interval '1 hour', expires_at=now()-interval '1 second' where user_id=:id")
        .setParameter("id", user.id()).executeUpdate());
    reset(expired).statusCode(400).body("message", equalTo(PasswordResetService.INVALID_LINK));
  }
}
