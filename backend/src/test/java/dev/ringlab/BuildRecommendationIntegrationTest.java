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

  @Test void scenarioObjectiveDiagnosticsAndLegacyPassiveSerializationAreExplicit() {
    var token = VerifiedUserFixture.createToken(em); var before = communityCounts();
    var current = selection(); current.put("gadgetIds",List.of(PassiveGadgetRules.id(45),PassiveGadgetRules.id(15)));
    var body = request(current,current);
    var legacy = post(token,body).statusCode(200).extract().jsonPath().getMap("$");
    assertFalse(legacy.containsKey("basis")); assertFalse(legacy.containsKey("scenario"));
    body.put("basis","PASSIVE");
    var explicit = post(token,body).statusCode(200).extract().jsonPath().getMap("$");
    legacy.remove("elapsedMillis"); explicit.remove("elapsedMillis"); assertEquals(legacy,explicit);
    body.put("basis","CURRENT_SCENARIO"); body.put("scenario",Map.of("lap",1,"vehicleForm","WATER"));
    var result = post(token,body).statusCode(200).body("basis",equalTo("CURRENT_SCENARIO"))
        .body("scenario.context.lap",equalTo(1)).body("scenario.context.ringsHeld",nullValue())
        .body("scenario.recommended.coverage",equalTo("CALCULATED"))
        .body("scenario.recommended.assumptions",contains("QUICK_STARTER_SEA_DOG_ASSUMED_ADDITIVE"))
        .body("scenario.current.knownSubtotal",nullValue()).body("scenario.recommended.knownSubtotal",nullValue())
        .extract().jsonPath();
    assertEquals(40d,result.getDouble("recommendedStats.speed") - result.getDouble("scenario.recommended.passive.adjusted.speed"));
    body.put("scenario",Map.of());
    post(token,body).statusCode(200).body("outcome",equalTo("UNAVAILABLE"))
        .body("currentStats",nullValue()).body("recommendedStats",nullValue())
        .body("scenario.current.coverage",equalTo("PARTIAL"));
    body.put("mode","BALANCED"); body.put("priorities",List.of("SPEED"));
    body.put("balanced",Map.of("maximumLossPercent",Map.of("SPEED",0),"secondary",List.of("ACCELERATION","HANDLING","POWER","BOOST")));
    post(token,body).statusCode(200).body("outcome",equalTo("UNAVAILABLE"))
        .body("reason",containsString("scenario-adjusted")).body("currentStats",nullValue());
    assertEquals(before,communityCounts());
  }

  @Test void scenarioContextValidationIsSharedWithPreviewAndDoesNotCoerceUnknowns() {
    for (var invalid : List.of(Map.of("lap",0),Map.of("lap",4),Map.of("lap",1.5),Map.of("ringsHeld",-1),
        Map.of("ringsHeld",1000),Map.of("ringsHeld",2.5),Map.of("distanceToFinish",-1),Map.of("distanceToFinish",50001),
        Map.of("distanceToFinish",3.5),Map.of("vehicleForm","SPACE"),Map.of("landingBoostActive","not-a-boolean"))) {
      var token = VerifiedUserFixture.createToken(em);
      var body = request(selection(),empty()); body.put("basis","CURRENT_SCENARIO"); body.put("scenario",invalid);
      post(token,body).statusCode(400);
    }
    var token = VerifiedUserFixture.createToken(em); var current = selection(); var body = request(current,current);
    body.put("scenario",Map.of()); post(token,body).statusCode(400).body("message",containsString("PASSIVE"));
    body.put("basis","CURRENT_SCENARIO"); body.remove("scenario");
    post(token,body).statusCode(400).body("message",containsString("requires a scenario"));
    body.put("scenario",null); post(token,body).statusCode(400);
    body.put("scenario",Map.of()); post(token,body).statusCode(200).body("scenario.current.coverage",equalTo("CALCULATED"));
    body.put("basis",null); body.remove("scenario");
    post(token,body).statusCode(200).body("basis",nullValue()).body("scenario",nullValue());
    body.put("basis","RACE_SIMULATION"); post(token,body).statusCode(400);
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

  @Test void balancedUsesServerReferenceAndExactFloorsWithoutMutation() {
    var token = VerifiedUserFixture.createToken(em); var before = communityCounts();
    var current = selection(); current.put("stats", Map.of("boost", 999999));
    var body = request(current, empty());
    body.put("mode", "BALANCED"); body.put("priorities", List.of("BOOST", "SPEED", "ACCELERATION", "HANDLING"));
    body.put("balanced", Map.of("maximumLossPercent", Map.of("BOOST", 5, "SPEED", 10, "ACCELERATION", 25, "HANDLING", 50), "secondary", List.of("POWER")));
    var result = post(token, body).statusCode(200).body("selection", notNullValue())
        .body("outcome", is(oneOf("ESTABLISHED", "BEST_FOUND"))).extract().jsonPath();
    for (var stat : List.of("BOOST", "SPEED", "ACCELERATION", "HANDLING"))
      assertTrue(result.getDouble("recommendedStats." + stat.toLowerCase()) >= result.getDouble("balanced.minimum." + stat));
    assertTrue(result.getDouble("currentStats.boost") < 999999);
    assertEquals(before, communityCounts());
    body.put("current", empty());
    post(token, body).statusCode(200).body("outcome", equalTo("UNAVAILABLE")).body("reason", containsString("frozen reference"));
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
