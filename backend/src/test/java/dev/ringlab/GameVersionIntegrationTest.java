package dev.ringlab;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.port.out.GameDataRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.*;
import org.junit.jupiter.api.Test;

@QuarkusTest
class GameVersionIntegrationTest {
  @Inject GameDataRepository game;

  @Test
  void catalogReturnsExactlyTheOfficialVersionsNewestFirst() {
    given().get("/api/game-versions").then().statusCode(200)
        .body("version", contains("1.4.1", "1.3.1", "1.2.2", "1.2.0"))
        .body("releasedAt", contains("2026-06-23", "2026-03-18", "2025-12-22", "2025-12-03"))
        .body("id", contains("50000000-0000-0000-0000-000000000001", "50000000-0000-0000-0000-000000000002",
            "50000000-0000-0000-0000-000000000003", "50000000-0000-0000-0000-000000000004"));
    for (var version : game.listGameVersions()) {
      assertEquals(version, game.findGameVersion(version.id()).orElseThrow());
    }
    assertTrue(game.findGameVersion(UUID.randomUUID()).isEmpty());
  }

  @Test
  void restCreatesReadsChangesAndClearsVersionAndRejectsUnknownIds() {
    String name = "version_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    String token = given().contentType("application/json")
        .body(Map.of("username", name, "email", name + "@example.test", "password", "test-password"))
        .post("/api/auth/register").then().statusCode(200).extract().path("token");
    Map<String, Object> draft = new HashMap<>();
    draft.put("title", "Version test");
    draft.put("description", "");
    draft.put("racerId", game.listRacers().getFirst().id());
    for (String slot : List.of("FRONT", "REAR", "TIRE")) {
      draft.put(slot.toLowerCase() + "PartId", game.listMachineParts().stream()
          .filter(p -> p.type().name().equals(slot)).findFirst().orElseThrow().id());
    }
    draft.put("gadgetIds", List.of());
    List<String> created = new ArrayList<>();
    try {
      String versionless = given().auth().oauth2(token).contentType("application/json").body(draft)
          .post("/api/builds").then().statusCode(200).body("gameVersion", nullValue()).extract().path("id");
      created.add(versionless);
      var first = game.listGameVersions().getFirst();
      var second = game.listGameVersions().get(1);
      draft.put("gameVersionId", first.id());
      String id = given().auth().oauth2(token).contentType("application/json").body(draft)
          .post("/api/builds").then().statusCode(200)
          .body("gameVersion.version", equalTo(first.version())).extract().path("id");
      created.add(id);
      given().get("/api/builds/" + id).then().statusCode(200)
          .body("gameVersion.id", equalTo(first.id().toString())).body("score", equalTo(0));
      given().queryParam("gameVersionId", first.id()).queryParam("search", "Version test")
          .get("/api/builds").then().statusCode(200).body("items.id", hasItem(id))
          .body("items.id", not(hasItem(versionless)));
      draft.put("gameVersionId", second.id());
      given().auth().oauth2(token).contentType("application/json").body(draft)
          .put("/api/builds/" + id).then().statusCode(200).body("gameVersion.version", equalTo(second.version()));
      draft.put("gameVersionId", null);
      given().auth().oauth2(token).contentType("application/json").body(draft)
          .put("/api/builds/" + id).then().statusCode(200).body("gameVersion", nullValue());
      given().get("/api/builds/" + id).then().statusCode(200).body("gameVersion", nullValue());
      draft.put("gameVersionId", UUID.randomUUID());
      given().auth().oauth2(token).contentType("application/json").body(draft)
          .post("/api/builds").then().statusCode(400).body("message", equalTo("Unknown game version ID"));
      given().auth().oauth2(token).contentType("application/json").body(draft)
          .put("/api/builds/" + id).then().statusCode(400).body("message", equalTo("Unknown game version ID"));
    } finally {
      for (String id : created) given().auth().oauth2(token).delete("/api/builds/" + id).then().statusCode(204);
    }
  }
}
