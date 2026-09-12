package dev.ringlab;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.port.out.GameDataRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

@QuarkusTest
class GadgetCatalogIntegrationTest {
  @Inject GameDataRepository game;

  @Test
  void expandedCatalogPreservesSeedIdsAndMapsVerifiedMetadataThroughRest() throws Exception {
    var gadgets = game.listGadgets();
    // This is the single assertion of the researched catalog size, not the game's total roster.
    assertEquals(70, gadgets.size());
    assertEquals(70, gadgets.stream().filter(g -> g.imagePath() != null).count());
    assertEquals(27, game.listMachines().stream().filter(m -> m.imagePath() != null).count());
    assertEquals(53, game.listRacers().stream().filter(r -> r.imagePath() != null).count());
    assertEquals(gadgets.size(), gadgets.stream().map(g -> g.id()).distinct().count());
    assertEquals(gadgets.size(), gadgets.stream().map(g -> g.name()).distinct().count());

    try (var seed = getClass().getResourceAsStream("/db/migration/V2__game_data.sql")) {
      assertNotNull(seed);
      var ids = Pattern.compile("INSERT INTO gadgets\\(id,name\\) VALUES \\('([^']+)'")
          .matcher(new String(seed.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
      int resolved = 0;
      while (ids.find()) {
        assertTrue(game.findGadget(UUID.fromString(ids.group(1))).isPresent(), ids.group(1));
        resolved++;
      }
      assertEquals(20, resolved);
    }

    List<Map<String, Object>> responses = given().get("/api/gadgets").then().statusCode(200)
        .extract().jsonPath().getList("$");
    assertEquals(gadgets.size(), responses.size());
    for (var gadget : gadgets) {
      assertEquals(gadget, game.findGadget(gadget.id()).orElseThrow());
      var response = responses.stream().filter(r -> gadget.id().toString().equals(r.get("id")))
          .findFirst().orElseThrow();
      assertEquals(gadget.name(), response.get("name"));
      assertTrue(response.containsKey("description"));
      assertTrue(response.containsKey("slotCost"));
      assertEquals(gadget.description(), response.get("description"));
      assertEquals(gadget.slotCost(), response.get("slotCost"));
      assertEquals(gadget.imagePath(), response.get("imagePath"));
      if (gadget.slotCost() != null) {
        // Observed first-party range: 1 (current Crash Pads), 2 (old Crash Pads), 3 (kit screenshot).
        assertTrue(gadget.slotCost() >= 1 && gadget.slotCost() <= 3, gadget.name());
      }
      if (gadget.imagePath() != null) {
        assertTrue(gadget.imagePath().matches("/assets/gadgets/[a-z0-9-]+\\.png"));
        assertTrue(Files.isRegularFile(Path.of("../frontend/public" + gadget.imagePath())), gadget.name());
      }
    }
    var kit = game.findGadget(UUID.fromString("70000000-0000-4000-8000-000000000013")).orElseThrow();
    assertEquals("Handling Character Kit", kit.name());
    assertEquals(3, kit.slotCost());
    assertEquals("Collide with racers and steal Rings; Handling characters receive stat adjustments.", kit.description());
    assertEquals(1, game.findGadget(UUID.fromString("70000000-0000-4000-8000-000000000040"))
        .orElseThrow().slotCost());
    var unknown = game.findGadget(UUID.fromString("82005d0f-575b-5350-934f-fb108d306a21")).orElseThrow();
    assertEquals("Attack items appear more often.", unknown.description());
    assertEquals(1, unknown.slotCost());
    assertEquals("/assets/gadgets/attack-item-chance-up.png", unknown.imagePath());
  }

  @Test
  void createReadAndEditPreserveMixedOldAndNewGadgetOrderWithoutCapacityRules() {
    String name = "gadgets_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    String token = given().contentType("application/json")
        .body(Map.of("username", name, "email", name + "@example.test", "password", "test-password"))
        .post("/api/auth/register").then().statusCode(200).extract().path("token");
    var order = List.of("70000000-0000-4000-8000-000000000013", "174ea0a3-43bb-5001-bbd4-8b598482fe59",
        "70000000-0000-4000-8000-000000000011", "70000000-0000-4000-8000-000000000012");
    Map<String, Object> draft = new HashMap<>();
    draft.put("title", "Ordered gadgets");
    draft.put("description", "");
    draft.put("racerId", game.listRacers().getFirst().id());
    draft.put("gameVersionId", game.listGameVersions().getFirst().id());
    for (String slot : List.of("FRONT", "REAR", "TIRE")) {
      draft.put(slot.toLowerCase() + "PartId", game.listMachineParts().stream()
          .filter(p -> p.type().name().equals(slot)).findFirst().orElseThrow().id());
    }
    // Nine known slots plus an unknown cost must remain saveable in this catalog-only task.
    draft.put("gadgetIds", order);
    String id = given().auth().oauth2(token).contentType("application/json").body(draft)
        .post("/api/builds").then().statusCode(200).body("gadgets.id", equalTo(order))
        .extract().path("id");
    try {
      given().get("/api/builds/" + id).then().statusCode(200).body("gadgets.id", equalTo(order));
      var reversed = new ArrayList<>(order);
      Collections.reverse(reversed);
      draft.put("gadgetIds", reversed);
      given().auth().oauth2(token).contentType("application/json").body(draft)
          .put("/api/builds/" + id).then().statusCode(200).body("gadgets.id", equalTo(reversed));
      given().get("/api/builds/" + id).then().statusCode(200).body("gadgets.id", equalTo(reversed));
    } finally {
      given().auth().oauth2(token).delete("/api/builds/" + id).then().statusCode(204);
    }
  }
}
