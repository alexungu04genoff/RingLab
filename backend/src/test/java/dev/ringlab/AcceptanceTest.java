package dev.ringlab;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@QuarkusTestResource(value = VerificationMailResource.class, restrictToAnnotatedClass = true)
public class AcceptanceTest extends ApiContract {
  @Inject EntityManager em;

  @Test
  void expiredVerificationIsRejectedWithoutActivatingAccount() {
    String name = "expiry_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    String email = name + "@example.test";
    given().contentType("application/json")
        .body(Map.of("username", name, "email", email, "password", "password-123"))
        .post("/api/auth/register").then().statusCode(200);
    String token = VerificationMailResource.tokenFor(email);
    QuarkusTransaction.requiringNew().run(() ->
        em.createNativeQuery("update email_verification_tokens set expires_at = now() - interval '1 second' "
            + "where user_id = (select id from users where email = :email)")
            .setParameter("email", email).executeUpdate());
    given().contentType("application/json")
        .body(Map.of("token", token)).post("/api/auth/verify-email")
        .then().statusCode(400).body("message", equalTo("Verification link is invalid or expired"));
    given().contentType("application/json")
        .body(Map.of("username", name, "password", "password-123"))
        .post("/api/auth/login").then().statusCode(403);
  }
}
