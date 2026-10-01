package dev.ringlab;

import dev.ringlab.port.in.AccountRegistrationUseCase;
import dev.ringlab.port.out.EmailVerificationSender;
import dev.ringlab.port.out.UserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionSynchronizationRegistry;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(AccountRegistrationIntegrationTest.Profile.class)
class AccountRegistrationIntegrationTest {
  public static class Profile implements QuarkusTestProfile {
    public Map<String, String> getConfigOverrides() {
      return Map.of("ringlab.rate-limit.resend-verification", "1000/PT1M");
    }
    public Set<Class<?>> getEnabledAlternatives() { return Set.of(TestSender.class); }
  }

  @Alternative @ApplicationScoped
  public static class TestSender implements EmailVerificationSender {
    @Inject TransactionSynchronizationRegistry transactions;
    @Inject EntityManager em;
    volatile boolean fail;
    volatile int transactionStatus;
    volatile long committedAccountsWithToken;
    volatile String token;
    volatile int calls;

    public void setFail(boolean value) { fail = value; }
    public void reset() { fail = false; calls = 0; }
    public int transactionStatus() { return transactionStatus; }
    public long committedAccountsWithToken() { return committedAccountsWithToken; }
    public String token() { return token; }
    public int calls() { return calls; }

    public void sendVerification(String email, String url) {
      calls++;
      transactionStatus = transactions.getTransactionStatus();
      // A fresh transaction must see both rows before the sender is invoked.
      committedAccountsWithToken = QuarkusTransaction.requiringNew().call(() -> ((Number)
          em.createNativeQuery("select count(*) from users u join email_verification_tokens t on t.user_id=u.id where u.email=:email")
              .setParameter("email", email).getSingleResult()).longValue());
      token = url.substring(url.indexOf("?token=") + 7);
      if (fail) throw new IllegalStateException("Simulated unavailable SMTP");
    }
  }

  @Inject AccountRegistrationUseCase registration;
  @Inject UserRepository users;
  @Inject EntityManager em;
  @Inject TestSender sender;
  private final String username = "mail_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
  private final String email = username + "@example.test";

  @AfterEach void cleanup() {
    sender.reset();
    QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("delete from users where email=:email")
        .setParameter("email", email).executeUpdate());
  }

  @Test void failedRegistrationMailLeavesCommittedAccountAndTokenAndResendCanRecover() {
    sender.setFail(true);
    register().statusCode(503);
    assertCommittedBeforeDelivery();
    assertNull(users.byEmail(email).orElseThrow().emailVerifiedAt());
    String original = sender.token();
    register().statusCode(409);
    assertEquals(1, sender.calls(), "Duplicate registration must not send another email");

    sender.setFail(false);
    resend().statusCode(200);
    assertCommittedBeforeDelivery();
    verify(original).statusCode(400);
    verify(sender.token()).statusCode(200);
    assertLoginWorks();
    int before = sender.calls();
    registration.resendVerification(email);
    registration.resendVerification(UUID.randomUUID() + "@example.test");
    assertEquals(before, sender.calls(), "Verified and unknown accounts must not trigger mail");
  }

  @Test void failedResendCommitsReplacementBeforeDeliveryAndInvalidatesOldLink() {
    register().statusCode(200);
    assertCommittedBeforeDelivery();
    String original = sender.token();
    sender.setFail(true);
    resend().statusCode(503);
    assertCommittedBeforeDelivery();
    verify(original).statusCode(400);
    // Delivery failed, but the newly committed link remains valid if the user receives it.
    verify(sender.token()).statusCode(200);
    assertLoginWorks();
  }

  private void assertCommittedBeforeDelivery() {
    assertEquals(Status.STATUS_NO_TRANSACTION, sender.transactionStatus());
    assertEquals(1, sender.committedAccountsWithToken());
  }
  private io.restassured.response.ValidatableResponse register() {
    return given().contentType("application/json")
        .body(Map.of("username", username, "email", email, "password", "correct-password"))
        .post("/api/auth/register").then();
  }
  private io.restassured.response.ValidatableResponse resend() {
    return given().contentType("application/json").body(Map.of("email", email))
        .post("/api/auth/resend-verification").then();
  }
  private io.restassured.response.ValidatableResponse verify(String token) {
    return given().contentType("application/json").body(Map.of("token", token))
        .post("/api/auth/verify-email").then();
  }
  private void assertLoginWorks() {
    given().contentType("application/json").body(Map.of("username", username, "password", "correct-password"))
        .post("/api/auth/login").then().statusCode(200);
  }
}
