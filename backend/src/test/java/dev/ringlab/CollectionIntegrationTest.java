package dev.ringlab;

import dev.ringlab.domain.gamedata.PassiveGadgetRules;
import dev.ringlab.port.out.GameDataRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/** Run only against a disposable database; each test creates disposable verified accounts. */
@QuarkusTest
class CollectionIntegrationTest {
  @Inject EntityManager em;
  @Inject GameDataRepository catalog;

  @Test void privateTypedIdempotentExclusionsNeverChangePublicBrowseResults() {
    var demo = VerifiedUserFixture.createToken(em);
    var other = VerifiedUserFixture.createToken(em);
    var racer = catalog.listRacers().getFirst().id();
    var machine = catalog.listMachines().getFirst().id();
    var gadget = catalog.listGadgets().getFirst().id();
    String before = given().get("/api/builds").then().statusCode(200).extract().asString();
    given().auth().oauth2(demo).get("/api/collection").then().statusCode(200)
        .header("Cache-Control", containsString("no-store")).header("Vary", "Authorization")
        .body("racers", empty()).body("machines", empty()).body("gadgets", empty());
    var items = Map.of("RACER", racer, "MACHINE", machine, "GADGET", gadget);
    items.forEach((category, id) -> { setOwned(demo, category, id, false); setOwned(demo, category, id, false); });
    given().auth().oauth2(demo).get("/api/collection").then().statusCode(200)
        .body("racers", contains(racer.toString())).body("machines", contains(machine.toString())).body("gadgets", contains(gadget.toString()));
    given().auth().oauth2(other).get("/api/collection").then().statusCode(200)
        .body("racers", empty()).body("machines", empty()).body("gadgets", empty());
    given().auth().oauth2(demo).get("/api/builds").then().statusCode(200).body(equalTo(before));
    given().auth().oauth2(demo).contentType("application/json").body(Map.of("owned", false))
        .put("/api/collection/RACER/" + machine).then().statusCode(400);
    given().get("/api/collection").then().statusCode(401);
    given().contentType("application/json").body(Map.of("owned", false)).put("/api/collection/RACER/" + racer).then().statusCode(401);
    items.forEach((category, id) -> { setOwned(demo, category, id, true); setOwned(demo, category, id, true); });
    given().auth().oauth2(demo).get("/api/collection").then().body("racers", empty()).body("machines", empty()).body("gadgets", empty());
  }

  @Test void authenticatedRecommendationsLoadExclusionsAndNameUnavailableLocksInBothModes() {
    var racer = catalog.listRacers().stream().filter(r -> r.name().equals("Sonic the Hedgehog")).findFirst().orElseThrow();
    var machine = catalog.listMachines().stream().filter(m -> m.name().equals("Speedster Lightning")).findFirst().orElseThrow();
    var gadget = PassiveGadgetRules.id(45);
    var current = new HashMap<String, Object>();
    current.put("racerId", racer.id()); current.put("gadgetIds", List.of(gadget));
    catalog.listMachineParts().stream().filter(p -> p.sourceMachineId().equals(machine.id())).forEach(p ->
        current.put(p.type().name().toLowerCase() + "PartId", p.id()));
    var version = catalog.listGameVersions().stream().filter(v -> v.version().equals("1.4.1")).findFirst().orElseThrow();
    for (var mode : List.of("STRICT", "BALANCED")) {
      var demo = VerifiedUserFixture.createToken(em);
      setOwned(demo, "RACER", racer.id(), false);
      setOwned(demo, "MACHINE", machine.id(), false);
      setOwned(demo, "GADGET", gadget, false);
      var request = new HashMap<String, Object>();
      request.put("gameVersionId", version.id()); request.put("machineType", "SPEED");
      request.put("mode", mode); request.put("priorities", List.of("SPEED", "ACCELERATION", "HANDLING", "POWER", "BOOST"));
      request.put("current", current); request.put("locked", current);
      if (mode.equals("BALANCED")) request.put("balanced", Map.of("maximumLossPercent",
          Map.of("SPEED", 100, "ACCELERATION", 100, "HANDLING", 100, "POWER", 100, "BOOST", 100), "secondary", List.of()));
      given().auth().oauth2(demo).contentType("application/json").body(request).post("/api/build-recommendations")
          .then().statusCode(400).body("message", containsString(racer.name())).body("message", containsString(machine.name()));
      request.put("locked", Map.of("gadgetIds", List.of()));
      var response = given().auth().oauth2(demo).contentType("application/json").body(request).post("/api/build-recommendations")
          .then().statusCode(200).body("currentStats", notNullValue()).extract().jsonPath();
      // A bounded Balanced search may legitimately exhaust its budget; any returned candidate must be owned.
      if (response.get("selection") != null) {
        org.junit.jupiter.api.Assertions.assertNotEquals(racer.id().toString(), response.getString("selection.racerId"));
        org.junit.jupiter.api.Assertions.assertFalse(response.getList("selection.gadgetIds", String.class).contains(gadget.toString()));
        for (var slot : List.of("frontPartId", "rearPartId", "tirePartId")) {
          var part = catalog.findMachinePart(UUID.fromString(response.getString("selection." + slot))).orElseThrow();
          org.junit.jupiter.api.Assertions.assertNotEquals(machine.id(), part.sourceMachineId());
        }
      }
    }
  }

  private void setOwned(String token, String category, UUID id, boolean owned) {
    given().auth().oauth2(token).contentType("application/json").body(Map.of("owned", owned))
        .put("/api/collection/" + category + "/" + id).then().statusCode(204);
  }
}
