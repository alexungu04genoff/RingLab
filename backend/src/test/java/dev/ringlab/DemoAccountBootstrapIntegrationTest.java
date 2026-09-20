package dev.ringlab;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DemoAccountBootstrapIntegrationTest {
  @Test
  void normalRegistrationStillReturnsVerificationMessageRatherThanSession() {
    given().contentType(ContentType.JSON)
        .body(Map.of(
            "username", "normal_registration_user",
            "email", "normal_registration_user@example.test",
            "password", "NormalUser!2026"))
        .when().post("/api/auth/register")
        .then().statusCode(200)
        .body("message", notNullValue())
        .body("token", org.hamcrest.Matchers.nullValue());

    given().contentType(ContentType.JSON)
        .body(Map.of("username", "normal_registration_user", "password", "NormalUser!2026"))
        .when().post("/api/auth/login")
        .then().statusCode(403);
  }

  @Test
  void loopbackFixtureBootstrapCreatesVerifiedAccountAndIsIdempotent() {
    String username = "ringlab_demo_integration";
    String email = "ringlab_demo_integration@example.test";
    String password = "RingLabDemo!2026";
    var request = Map.of(
        "password", password,
        "accounts", new Object[] {Map.of("key", "integration", "username", username, "email", email)});

    String userId = given()
        .contentType(ContentType.JSON)
        .body(request)
        .when().post("/api/dev-fixtures/demo-accounts")
        .then().statusCode(200)
        .body("[0].key", equalTo("integration"))
        .body("[0].session.token", notNullValue())
        .extract().path("[0].session.user.id");

    given().contentType(ContentType.JSON).body(request)
        .when().post("/api/dev-fixtures/demo-accounts")
        .then().statusCode(200)
        .body("[0].session.user.id", equalTo(userId));

    given().contentType(ContentType.JSON)
        .body(Map.of("username", username, "password", password))
        .when().post("/api/auth/login")
        .then().statusCode(200)
        .body("user.id", equalTo(userId));
  }

  @Test
  void fixtureBootstrapRejectsNonReservedIdentity() {
    given().contentType(ContentType.JSON)
        .body(Map.of("password", "RingLabDemo!2026", "accounts", new Object[] {
            Map.of("key", "unsafe", "username", "ordinary_user", "email", "ordinary@example.test")
        }))
        .when().post("/api/dev-fixtures/demo-accounts")
        .then().statusCode(400);
  }
}
