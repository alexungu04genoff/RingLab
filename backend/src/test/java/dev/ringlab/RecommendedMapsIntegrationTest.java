package dev.ringlab;

import dev.ringlab.domain.gamedata.MachinePartType;
import dev.ringlab.domain.gamedata.RacingType;
import dev.ringlab.port.out.GameDataRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class RecommendedMapsIntegrationTest {
  @Inject EntityManager em;
  @Inject GameDataRepository game;

  @Test void contractsFilteringPaginationOwnershipAndLivePrivateBookmarks() {
    var owner = VerifiedUserFixture.createToken(em);
    var visitor = VerifiedUserFixture.createToken(em);
    var maps = given().get("/api/maps").then().statusCode(200).extract().jsonPath().getList("id", String.class);
    assertFalse(maps.isEmpty());
    String first = maps.getFirst(), second = maps.get(1);
    var marker = "Maps " + UUID.randomUUID().toString().substring(0, 8);
    var draft = draft(marker);
    var all = post(owner, draft);
    var ids = new ArrayList<String>(List.of(all));
    try {
      given().get("/api/builds/" + all).then().statusCode(200)
          .body("mapRecommendations.mode", equalTo("ALL")).body("mapRecommendations.maps", empty());
      draft.put("recommendedMapIds", List.of(first));
      var one = post(owner, draft); ids.add(one);
      draft.put("recommendedMapIds", List.of(second, first));
      var several = post(owner, draft); ids.add(several);
      draft.put("recommendedMapIds", List.of(second));
      var other = post(owner, draft); ids.add(other);

      for (var sort : List.of("newest", "score", "rated")) {
        var pages = new ArrayList<String>();
        for (int page = 0; page < 3; page++) {
          var response = given().queryParam("search", marker).queryParam("mapId", first)
              .queryParam("sort", sort).queryParam("size", 1).queryParam("page", page)
              .get("/api/builds").then().statusCode(200).body("total", equalTo(3)).extract().jsonPath();
          pages.addAll(response.getList("items.id", String.class));
        }
        assertEquals(Set.of(all, one, several), Set.copyOf(pages));
        given().queryParam("search", marker).queryParam("mapId", first).queryParam("includeAllMaps", false)
            .queryParam("sort", sort).get("/api/builds").then().statusCode(200)
            .body("total", equalTo(2)).body("items.id", containsInAnyOrder(one, several));
      }
      given().queryParam("search", marker).queryParam("includeAllMaps", false).get("/api/builds")
          .then().statusCode(200).body("total", equalTo(4));
      var authorId = given().get("/api/builds/" + one).jsonPath().getString("author.id");
      given().queryParam("authorId", authorId).queryParam("racerId", draft.get("racerId"))
          .queryParam("gameVersionId", draft.get("gameVersionId")).queryParam("mapId", first)
          .queryParam("includeAllMaps", false).get("/api/builds").then().statusCode(200).body("total", equalTo(2));

      given().auth().oauth2(visitor).put("/api/saved-builds/" + one).then().statusCode(200);
      given().auth().oauth2(owner).get("/api/saved-builds").then().statusCode(200).body("total", equalTo(0));
      saved(visitor, first, false, 1);
      given().auth().oauth2(visitor).contentType("application/json").body(draft)
          .put("/api/builds/" + one).then().statusCode(403);
      draft.remove("recommendedMapIds");
      given().auth().oauth2(owner).contentType("application/json").body(draft).put("/api/builds/" + one)
          .then().statusCode(200).body("mapRecommendations.maps.id", contains(first));
      draft.put("recommendedMapIds", List.of());
      given().auth().oauth2(owner).contentType("application/json").body(draft).put("/api/builds/" + one)
          .then().statusCode(200).body("mapRecommendations.mode", equalTo("ALL"));
      saved(visitor, first, false, 0);
      saved(visitor, first, true, 1);
      given().auth().oauth2(visitor).get("/api/saved-builds").then().statusCode(200)
          .body("items[0].build.mapRecommendations.mode", equalTo("ALL"));

      for (var invalid : Arrays.asList(null, List.of(first, first), List.of(UUID.randomUUID().toString()),
          Arrays.asList((String) null), List.of("1-1-1-1-1"), "not-an-array")) {
        draft.put("recommendedMapIds", invalid);
        given().auth().oauth2(owner).contentType("application/json").body(draft).put("/api/builds/" + one)
            .then().statusCode(400);
      }
      for (var endpoint : List.of("/api/builds", "/api/saved-builds")) {
        given().auth().oauth2(visitor).queryParam("mapId", UUID.randomUUID()).get(endpoint).then().statusCode(400);
        given().auth().oauth2(visitor).queryParam("mapId", first).queryParam("includeAllMaps", "yes")
            .get(endpoint).then().statusCode(400);
      }
      var top = given().get("/api/community/top-builds").then().statusCode(200).extract().response();
      given().queryParam("mapId", first).get("/api/community/top-builds").then().statusCode(400);
      given().header("If-None-Match", top.header("ETag")).get("/api/community/top-builds").then().statusCode(304);
    } finally {
      for (var id : ids) given().auth().oauth2(owner).delete("/api/builds/" + id).then().statusCode(204);
    }
  }

  private void saved(String token, String map, boolean includeAll, int count) {
    given().auth().oauth2(token).queryParam("mapId", map).queryParam("includeAllMaps", includeAll)
        .get("/api/saved-builds").then().statusCode(200).body("total", equalTo(count))
        .header("Cache-Control", containsString("no-store"));
  }
  private String post(String token, Map<String, Object> draft) {
    return given().auth().oauth2(token).contentType("application/json").body(draft)
        .post("/api/builds").then().statusCode(200).extract().jsonPath().getString("id");
  }
  private Map<String, Object> draft(String title) {
    var machine = game.listMachines().stream().filter(m -> m.racingType() == RacingType.SPEED).findFirst().orElseThrow();
    var parts = game.listMachineParts().stream().filter(p -> p.sourceMachineId().equals(machine.id())).toList();
    var draft = new HashMap<String, Object>();
    draft.put("title", title); draft.put("description", "Isolated recommendation test");
    draft.put("racerId", game.listRacers().getFirst().id());
    draft.put("gameVersionId", game.listGameVersions().getFirst().id());
    for (var type : MachinePartType.values()) {
      String field = switch (type) { case FRONT -> "frontPartId"; case REAR -> "rearPartId"; case TIRE -> "tirePartId"; };
      draft.put(field, parts.stream().filter(p -> p.type() == type).findFirst().orElseThrow().id());
    }
    draft.put("gadgetIds", List.of());
    return draft;
  }
}
