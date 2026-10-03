package dev.ringlab;

import dev.ringlab.domain.gamedata.*;
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
class BuildRecommendationIntegrationTest {
  @Inject GameDataRepository game;
  @Inject EntityManager em;

  @Test void bothModesCompleteEmptyAndPartiallyLockedDraftsAndKeepGadgetIdentities() {
    for (var mode : List.of("STRICT", "BALANCED")) {
      for (var type : List.of("BOOST", "SPEED")) {
        var token = VerifiedUserFixture.createToken(em);
        var body = configured(mode, type, empty(), empty());
        Map<String,Object> selected = post(token, body).statusCode(200).body("outcome", equalTo("ESTABLISHED"))
            .body("currentStats", nullValue()).body("selection.gadgetIds", hasSize(0))
            .body("selection.racerId", notNullValue()).body("selection.frontPartId", notNullValue())
            .body("selection.rearPartId", notNullValue()).extract().jsonPath().getMap("selection");
        assertEquals(type.equals("BOOST"), selected.get("tirePartId") == null);
        for (var slot : List.of("racerId", "frontPartId")) {
          var partial = empty(); partial.put(slot, selected.get(slot));
          body = configured(mode, type, partial, partial);
          post(token, body).statusCode(200).body("outcome", equalTo("ESTABLISHED"))
              .body("currentStats", nullValue()).body("selection." + slot, equalTo(selected.get(slot)))
              .body("selection.rearPartId", notNullValue());
        }
        body = configured(mode, type, new HashMap<>(selected), new HashMap<>(selected));
        post(token, body).statusCode(200).body("alreadyBest", equalTo(true)).body("currentStats", notNullValue());
      }
      var token = VerifiedUserFixture.createToken(em);
      var plate = List.of(PassiveGadgetRules.id(52), PassiveGadgetRules.id(22)); // passive + utility
      var current = empty(); current.put("gadgetIds", plate);
      var body = configured(mode, "SPEED", current, empty());
      Map<String,Object> found = post(token, body).statusCode(200).body("outcome", equalTo("ESTABLISHED"))
          .body("selection.gadgetIds", equalTo(plate.stream().map(UUID::toString).toList())).extract().jsonPath().getMap("selection");
      var locks = new HashMap<String,Object>(found); locks.put("gadgetIds", List.of(PassiveGadgetRules.id(22)));
      body = configured(mode, "SPEED", new HashMap<>(found), locks);
      body.put("gadgetScope", "OPTIMIZE_UNLOCKED");
      post(token, body).statusCode(200).body("outcome", is(oneOf("ESTABLISHED", "BEST_FOUND")))
          .body("selection.gadgetIds", hasItem(PassiveGadgetRules.id(22).toString()));
    }
  }

  @Test void ownershipExcludesTheNominalWinnerInBothModes() {
    for (var mode : List.of("STRICT", "BALANCED")) {
      var token = VerifiedUserFixture.createToken(em);
      var body = configured(mode, "BOOST", empty(), empty());
      var winner = post(token, body).statusCode(200).body("outcome", equalTo("ESTABLISHED"))
          .extract().jsonPath().getString("selection.racerId");
      given().auth().oauth2(token).contentType("application/json").body(Map.of("owned", false))
          .put("/api/collection/RACER/" + winner).then().statusCode(204);
      post(token, body).statusCode(200).body("outcome", equalTo("ESTABLISHED"))
          .body("selection.racerId", not(equalTo(winner)));
    }
  }

  private Map<String,Object> configured(String mode, String type, Map<String,Object> current, Map<String,Object> locks) {
    var body = request(current, locks); body.put("machineType", type); body.put("mode", mode);
    body.put("gadgetScope", "KEEP_CURRENT");
    if (mode.equals("BALANCED")) body.put("balanced", Map.of("maximumLossPercent",
        Map.of("ACCELERATION", 0, "SPEED", 0, "HANDLING", 0, "BOOST", 0, "POWER", 0), "ignored", List.of()));
    return body;
  }

  @Test void recommendationsExcludeScenarioBonusesAndDiagnostics() {
    var token = VerifiedUserFixture.createToken(em); var before = communityCounts();
    var current = selection(); current.put("gadgetIds",List.of(PassiveGadgetRules.id(45),PassiveGadgetRules.id(15)));
    var body = request(current,current);
    for (var mode : List.of("STRICT", "BALANCED")) {
      body.put("mode", mode);
      if (mode.equals("BALANCED")) {
        body.put("priorities",List.of("SPEED"));
        body.put("balanced",Map.of("maximumLossPercent",Map.of("SPEED",0),"secondary",List.of("ACCELERATION","HANDLING","POWER","BOOST")));
      }
      var result = post(token,body).statusCode(200).body("outcome",equalTo("ESTABLISHED"))
          .body("currentStats.speed",equalTo(80))
          .body("selection.gadgetIds",hasItems(PassiveGadgetRules.id(45).toString(),PassiveGadgetRules.id(15).toString()))
          .extract().jsonPath();
      var proposed = new HashMap<String,Object>(result.getMap("selection"));
      proposed.remove("gadgetIds");
      List<String> gadgets = result.getList("selection.gadgetIds");
      proposed.put("gameVersionId",body.get("gameVersionId"));
      var passive = given().queryParams(proposed).queryParam("gadgetId",gadgets)
          .get("/api/stats/passive-build").then().statusCode(200)
          .body("passive.coverage",equalTo("CALCULATED")).extract().jsonPath();
      assertEquals(passive.getMap("passive.adjusted"),result.getMap("recommendedStats"));
      assertFalse(result.getMap("$").containsKey("basis")); assertFalse(result.getMap("$").containsKey("scenario"));
      var scenario = new HashMap<String,Object>(current); scenario.put("gameVersionId", body.get("gameVersionId"));
      scenario.put("scenario", Map.of("lap", 1, "vehicleForm", "NORMAL"));
      given().contentType("application/json").body(scenario).post("/api/stats/scenario-build").then().statusCode(200)
          .body("adjustments.speed", equalTo(20)).body("total.speed", equalTo(100));
      scenario.put("scenario", Map.of("lap", 2, "vehicleForm", "NORMAL"));
      given().contentType("application/json").body(scenario).post("/api/stats/scenario-build").then().statusCode(200)
          .body("adjustments.speed", equalTo(0)).body("total.speed", equalTo(80));
      var repeated = post(token, body).statusCode(200).extract().jsonPath();
      assertEquals(result.getMap("recommendedStats"), repeated.getMap("recommendedStats"));
      assertEquals(result.getMap("selection"), repeated.getMap("selection"));
    }
    assertEquals(before,communityCounts());
  }

  @Test void removedObjectiveFieldsAreRejectedRatherThanSilentlyIgnored() {
    var token = VerifiedUserFixture.createToken(em);
    for (var value : List.of("CURRENT_SCENARIO", "PASSIVE", "RACE_SIMULATION")) {
      var body = request(selection(), empty()); body.put("basis", value);
      post(token, body).statusCode(400);
    }
    for (var value : Arrays.asList(Map.of(), Map.of("lap",1,"vehicleForm","WATER"), null)) {
      var body = request(selection(), empty()); body.put("scenario", value);
      post(token, body).statusCode(400);
    }
  }

  private Map<String, Object> selection() {
    var machine = game.listMachines().stream().filter(m -> m.name().equals("Speedster Lightning")).findFirst().orElseThrow();
    var racer = game.listRacers().stream().filter(r -> r.name().equals("Sonic the Hedgehog")).findFirst().orElseThrow();
    var selection = new HashMap<String, Object>(); selection.put("racerId", racer.id()); selection.put("gadgetIds", List.of());
    for (var part : game.listMachineParts()) if (part.sourceMachineId().equals(machine.id()))
      selection.put(part.type().name().toLowerCase() + "PartId", part.id());
    return selection;
  }
  private Map<String, Object> request(Map<String, Object> current, Map<String, Object> locked) {
    var request = new HashMap<String, Object>();
    request.put("gameVersionId", game.listGameVersions().stream().filter(v -> v.version().equals("1.4.1")).findFirst().orElseThrow().id());
    request.put("machineType", "SPEED"); request.put("priorities", List.of("ACCELERATION", "SPEED", "HANDLING", "BOOST", "POWER"));
    request.put("current", current); request.put("locked", locked); return request;
  }
  private Map<String, Object> empty() { return new HashMap<>(Map.of("gadgetIds", List.of())); }

  @Test void authenticatedProposalIsReadOnlyAndIgnoresClientScoringFacts() {
    var token = VerifiedUserFixture.createToken(em);
    var countsBefore = communityCounts();
    var current = selection(); current.put("gadgetIds", List.of(PassiveGadgetRules.id(52), PassiveGadgetRules.id(53)));
    var locks = new HashMap<>(current); locks.remove("frontPartId"); locks.remove("rearPartId");
    var request = request(current, locks);
    current.put("stats", Map.of("acceleration", 999999)); current.put("slotCost", 0);
    var result = given().auth().oauth2(token).contentType("application/json").body(request)
        .post("/api/build-recommendations").then().statusCode(200)
        .header("Cache-Control", containsString("no-store")).body("outcome", equalTo("ESTABLISHED"))
        .body("selection.racerId", equalTo(current.get("racerId").toString()))
        .body("selection.tirePartId", equalTo(current.get("tirePartId").toString()))
        .body("selection.gadgetIds", hasItems(PassiveGadgetRules.id(52).toString(), PassiveGadgetRules.id(53).toString()))
        .extract().jsonPath();
    assertTrue(result.getDouble("recommendedStats.acceleration") < 999999);
    assertEquals(countsBefore, communityCounts());
    System.out.printf("Auto-builder full catalog: outcome=%s, work=%d, solver=%d ms%n",
        result.getString("outcome"), result.getLong("work"), result.getLong("elapsedMillis"));
    given().contentType("application/json").body(request).post("/api/build-recommendations").then().statusCode(401);
  }

  @Test void backendResolvesMachineTypeSlotsIdsAndDuplicateGadgets() {
    var token = VerifiedUserFixture.createToken(em);
    var current = selection(); var locks = empty(); locks.put("tirePartId", current.get("tirePartId"));
    var request = request(current, locks); request.put("machineType", "BOOST");
    post(token, request).statusCode(400).body("message", containsString("Unlock the Tire"));
    request.put("machineType", "POWER"); post(token, request).statusCode(400).body("message", containsString("Locked TIRE"));
    request = request(selection(), empty()); current = (Map<String, Object>)request.get("current");
    current.put("frontPartId", current.get("rearPartId")); post(token, request).statusCode(400).body("message", containsString("FRONT slot"));
    request = request(empty(), empty()); current = (Map<String, Object>)request.get("current");
    current.put("racerId", UUID.randomUUID()); post(token, request).statusCode(400).body("message", containsString("Unknown racer"));
    current.remove("racerId"); current.put("gadgetIds", List.of(PassiveGadgetRules.id(52), PassiveGadgetRules.id(52)));
    post(token, request).statusCode(400).body("message", containsString("distinct gadget"));
    request.put("priorities", List.of("SPEED", "SPEED", "HANDLING", "BOOST", "POWER"));
    post(token, request).statusCode(400).body("message", containsString("every stat"));
  }

  @Test void incompleteDraftAndBoostAreSupportedButOlderPatchIsUnavailable() {
    var token = VerifiedUserFixture.createToken(em);
    var request = request(empty(), empty()); request.put("machineType", "BOOST");
    post(token, request).statusCode(200).body("outcome", equalTo("ESTABLISHED"))
        .body("selection.tirePartId", nullValue()).body("selection.frontPartId", notNullValue())
        .body("selection.rearPartId", notNullValue()).body("currentStats", nullValue());
    request.put("gameVersionId", game.listGameVersions().stream().filter(v -> v.version().equals("1.3.1")).findFirst().orElseThrow().id());
    post(token, request).statusCode(200).body("outcome", equalTo("UNAVAILABLE")).body("selection", nullValue());
    request.put("gameVersionId", UUID.randomUUID()); post(token, request).statusCode(400).body("message", containsString("Unknown game version"));
    request.remove("gameVersionId"); post(token, request).statusCode(400);
    request = request(empty(), empty()); request.put("machineType", "FLYING"); post(token, request).statusCode(400);
  }

  @Test void recommendationRateLimitIsDedicatedAndPerAuthenticatedUser() {
    var token = VerifiedUserFixture.createToken(em);
    var request = request(empty(), empty());
    request.put("gameVersionId", game.listGameVersions().stream().filter(v -> v.version().equals("1.3.1")).findFirst().orElseThrow().id());
    for (int i = 0; i < 6; i++) post(token, request).statusCode(200);
    post(token, request).statusCode(429).header("Retry-After", notNullValue());
    post(VerifiedUserFixture.createToken(em), request).statusCode(200);
    given().auth().oauth2(token).get("/api/saved-builds").then().statusCode(200);
  }

  @Test void balancedUsesSurvivorThresholdsAndAcceptsEmptyDraftWithoutMutation() {
    var token = VerifiedUserFixture.createToken(em); var before = communityCounts();
    var current = selection(); current.put("stats", Map.of("boost", 999999));
    var body = request(current, empty());
    body.put("mode", "BALANCED"); body.put("priorities", List.of("BOOST", "SPEED", "ACCELERATION", "HANDLING"));
    body.put("balanced", Map.of("maximumLossPercent", Map.of("BOOST", 5, "SPEED", 10, "ACCELERATION", 25, "HANDLING", 50), "secondary", List.of("POWER")));
    var result = post(token, body).statusCode(200).body("selection", notNullValue())
        .body("outcome", is(oneOf("ESTABLISHED", "BEST_FOUND"))).extract().jsonPath();
    if (result.getBoolean("balanced.proven")) {
      var stats=List.of("BOOST","SPEED","ACCELERATION","HANDLING");
      for (int i=0;i<stats.size();i++)
        assertTrue(result.getDouble("recommendedStats."+stats.get(i).toLowerCase()) >= result.getDouble("balanced.stages["+i+"].threshold"));
    } else assertTrue(result.getList("balanced.stages").isEmpty());
    assertTrue(result.getDouble("currentStats.boost") < 999999);
    assertEquals(before, communityCounts());
    body.put("current", empty());
    post(token, body).statusCode(200).body("outcome", is(oneOf("ESTABLISHED","BEST_FOUND")))
        .body("selection",notNullValue()).body("currentStats",nullValue());
    body.put("priorities",List.of("BOOST","SPEED","ACCELERATION","HANDLING","POWER"));
    body.put("balanced",Map.of("maximumLossPercent",Map.of(),"ignored",List.of("BOOST","SPEED","ACCELERATION","HANDLING","POWER")));
    post(token,body).statusCode(200).body("outcome",equalTo("ESTABLISHED"))
        .body("balanced.proven",equalTo(true)).body("balanced.stages",hasSize(0));
    body.put("balanced",Map.of("maximumLossPercent",Map.of("BOOST",100,"SPEED",100,"ACCELERATION",100,"HANDLING",100,"POWER",100),"secondary",List.of()));
    post(token,body).statusCode(200).body("outcome",equalTo("ESTABLISHED"))
        .body("balanced.proven",equalTo(true)).body("balanced.stages",hasSize(0));
  }

  @Test void balancedRejectsMalformedPartitionsPercentagesAndNames() {
    var token = VerifiedUserFixture.createToken(em); var body = request(selection(), empty());
    body.put("mode", "BALANCED"); body.put("priorities", List.of("BOOST"));
    for (Object invalid : List.of(-1, 101, "NaN", "Infinity")) {
      body.put("balanced", Map.of("maximumLossPercent", Map.of("BOOST", invalid), "secondary", List.of("SPEED", "ACCELERATION", "HANDLING", "POWER")));
      post(token, body).statusCode(400);
    }
    body.put("balanced", Map.of("maximumLossPercent", Map.of("BOOST", 0), "secondary", List.of("SPEED", "SPEED", "HANDLING", "POWER")));
    post(token, body).statusCode(400);
    body.put("priorities", List.of("MAGIC")); post(token, body).statusCode(400);
    token = VerifiedUserFixture.createToken(em); body.put("priorities", List.of("BOOST"));
    body.put("balanced", Map.of("maximumLossPercent", Map.of("BOOST", 0), "secondary", List.of("SPEED", "HANDLING", "POWER")));
    post(token, body).statusCode(400);
    body.put("balanced", Map.of("maximumLossPercent", Map.of("BOOST", 0), "secondary", List.of("SPEED", "ACCELERATION", "HANDLING", "POWER")));
    body.remove("mode"); post(token, body).statusCode(400).body("message", containsString("every stat"));
  }

  private io.restassured.response.ValidatableResponse post(String token, Map<String, Object> body) {
    return given().auth().oauth2(token).contentType("application/json").body(body).post("/api/build-recommendations").then();
  }
  private List<?> communityCounts() {
    return em.createNativeQuery("select (select count(*) from builds), (select count(*) from votes), (select count(*) from comments), (select count(*) from saved_builds)").getResultList()
        .stream().map(row -> Arrays.asList((Object[])row)).toList();
  }
}
