package dev.ringlab;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.ringlab.port.out.GameDataRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HttpRobustnessIntegrationTest {
  @Inject EntityManager em;
  @Inject GameDataRepository game;

  @Test
  void malformedParametersKeepSanitizedClientStatus() {
    for (String path : List.of("/api/builds/not-a-uuid", "/api/builds?racerId=nope",
        "/api/builds?page=2147483648")) {
      given().get(path).then().statusCode(400).contentType("application/json")
          .body("message", equalTo("Bad Request"));
    }
  }

  @Test
  void malformedJsonAndUnsupportedMediaTypeKeepClientStatus() {
    given().contentType("application/json").body("{").post("/api/auth/google")
        .then().statusCode(400).contentType("application/json")
        .body("message", equalTo("Bad Request"));
    given().contentType("text/plain").body("{}").post("/api/auth/google")
        .then().statusCode(415).contentType("application/json")
        .body("message", equalTo("Unsupported Media Type"));
  }

  @Test
  void missingAccountIsUnauthorizedAndCannotWrite() {
    UUID missing = UUID.randomUUID();
    String token = Jwt.subject(missing.toString()).upn("missing-account").groups("user").sign();
    given().auth().oauth2(token).get("/api/auth/me").then().statusCode(401)
        .body("message", equalTo("Account unavailable"));
    var parts = game.listMachineParts();
    var body = Map.of("title", "Must not be saved", "description", "",
        "racerId", game.listRacers().getFirst().id(),
        "frontPartId", parts.stream().filter(p -> p.type().name().equals("FRONT")).findFirst().orElseThrow().id(),
        "rearPartId", parts.stream().filter(p -> p.type().name().equals("REAR")).findFirst().orElseThrow().id(),
        "tirePartId", parts.stream().filter(p -> p.type().name().equals("TIRE")).findFirst().orElseThrow().id(),
        "gadgetIds", List.of());
    given().auth().oauth2(token).contentType("application/json").body(body)
        .post("/api/builds").then().statusCode(401).body("message", equalTo("Account unavailable"));
    given().auth().oauth2(token).contentType("application/json").body(Map.of("value", 1))
        .put("/api/builds/" + UUID.randomUUID() + "/vote").then().statusCode(401);
    given().auth().oauth2(token).contentType("application/json")
        .post("/api/auth/logout").then().statusCode(401);
    assertEquals(0L, em.createQuery("select count(b) from BuildDbEntity b where b.authorId=:id", Long.class)
        .setParameter("id", missing).getSingleResult());
    assertEquals(0L, em.createQuery("select count(v) from VoteDbEntity v where v.userId=:id", Long.class)
        .setParameter("id", missing).getSingleResult());
  }
}
