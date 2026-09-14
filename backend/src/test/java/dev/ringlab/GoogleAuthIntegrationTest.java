package dev.ringlab;

import dev.ringlab.application.AuthenticationException;
import dev.ringlab.domain.auth.*;
import dev.ringlab.port.out.*;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(GoogleAuthIntegrationTest.Profile.class)
class GoogleAuthIntegrationTest {
  public static class Profile implements QuarkusTestProfile {
    public Set<Class<?>> getEnabledAlternatives() { return Set.of(OfflineVerifier.class); }
  }

  @Alternative
  @ApplicationScoped
  public static class OfflineVerifier implements ExternalIdentityVerifier {
    public VerifiedExternalIdentity verify(String credential) {
      if (!credential.startsWith("test-")) throw new AuthenticationException("Invalid Google credential");
      return new VerifiedExternalIdentity("GOOGLE", credential, credential + "@example.test", "Test user");
    }
  }

  @Inject EntityManager em;
  @Inject UserRepository users;
  @Inject ExternalIdentityRepository identities;

  @Test
  void googleResponseUsesNormalRinglabJwtAndRepeatedSignInKeepsOneAccount() {
    String credential = "test-" + UUID.randomUUID();
    var response = given().contentType("application/json").body(Map.of("credential", credential))
        .post("/api/auth/google").then().statusCode(200)
        .body("token", not(emptyOrNullString())).body("user.passwordHash", nullValue())
        .body("user.providerSubject", nullValue()).extract();
    String userId = response.path("user.id");
    String username = response.path("user.username");
    String token = response.path("token");
    try {
      assertNotEquals(credential, token);
      given().auth().oauth2(token).get("/api/auth/me").then().statusCode(200).body("id", equalTo(userId));
      given().contentType("application/json").body(Map.of("credential", credential))
          .post("/api/auth/google").then().statusCode(200).body("user.id", equalTo(userId));
      given().contentType("application/json").body(Map.of("username", username, "password", "ringlab-dummy-password"))
          .post("/api/auth/login").then().statusCode(401);
      QuarkusTransaction.requiringNew().run(() -> {
        assertNull(users.byId(UUID.fromString(userId)).orElseThrow().passwordHash());
        assertEquals(UUID.fromString(userId), identities.find("GOOGLE", credential).orElseThrow().userId());
        assertEquals(1L, em.createQuery("select count(u) from UserDbEntity u where u.email = :email", Long.class)
            .setParameter("email", credential + "@example.test").getSingleResult());
        em.createQuery("delete UserDbEntity where id = :id").setParameter("id", UUID.fromString(userId)).executeUpdate();
        em.flush(); em.clear();
        assertTrue(identities.find("GOOGLE", credential).isEmpty());
      });
    } finally {
      QuarkusTransaction.requiringNew().run(() -> em.createQuery("delete UserDbEntity where id = :id")
          .setParameter("id", UUID.fromString(userId)).executeUpdate());
    }
  }

  @Test
  @TestTransaction
  void migrationEnforcesUniqueProviderSubjectAndForeignKey() {
    var id = UUID.randomUUID();
    users.create(new User(id, "identity_test", "identity@example.test", null, Instant.now()));
    identities.create(new ExternalIdentity(id, "GOOGLE", "subject", Instant.now()));
    em.clear();
    assertThrows(PersistenceException.class, () -> em.createNativeQuery(
        "insert into external_identities(user_id, provider, provider_subject, created_at) values (:id, 'GOOGLE', 'subject', current_timestamp)")
        .setParameter("id", id).executeUpdate());
  }

  @Test
  @TestTransaction
  void adapterTranslatesDuplicateExternalIdentityToAStableApplicationError() {
    var userId = UUID.randomUUID();
    users.create(new User(userId, "identity_adapter", "identity-adapter@example.test", null, Instant.now()));
    var original = new ExternalIdentity(userId, "GOOGLE", "adapter-subject", Instant.now());
    identities.create(original);
    em.clear();

    var error = assertThrows(dev.ringlab.application.AlreadyExistsException.class,
        () -> identities.create(original));

    assertEquals("Sign-in was completed concurrently. Please try again.", error.getMessage());
  }

  @Test
  @TestTransaction
  void identityCannotReferenceMissingUser() {
    assertThrows(PersistenceException.class, () -> em.createNativeQuery(
        "insert into external_identities(user_id, provider, provider_subject, created_at) values (:id, 'GOOGLE', 'missing-user', current_timestamp)")
        .setParameter("id", UUID.randomUUID()).executeUpdate());
  }

  @Test
  void invalidCredentialUsesSemanticAuthenticationError() {
    given().contentType("application/json").body(Map.of("credential", "invalid"))
        .post("/api/auth/google").then().statusCode(401).body("message", equalTo("Invalid Google credential"));
  }
}
