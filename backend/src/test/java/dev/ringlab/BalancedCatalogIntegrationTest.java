package dev.ringlab;

import dev.ringlab.application.build.*;
import dev.ringlab.domain.build.recommendation.*;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.port.out.GameDataRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static dev.ringlab.domain.build.recommendation.StatPriority.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class BalancedCatalogIntegrationTest {
  @Inject GameDataRepository game;
  @Inject RecommendationCatalogLoader loader;
  @Inject ObjectMapper json;
  static final List<StatPriority> ACTIVE = List.of(BOOST, SPEED, ACCELERATION, HANDLING);
  static final BalancedConfiguration CONFIG = new BalancedConfiguration(Map.of(BOOST, BigDecimal.valueOf(5),
      SPEED, BigDecimal.TEN, ACCELERATION, BigDecimal.valueOf(25), HANDLING, BigDecimal.valueOf(50)), List.of(POWER));

  @Test void boundedRealCatalogEvaluationAndBenchmark() {
    var version = game.listGameVersions().stream().filter(v -> v.version().equals("1.4.1")).findFirst().orElseThrow();
    var catalog = loader.load(version.id());
    var miku = catalog.racers().values().stream().filter(r -> r.name().equals("Hatsune Miku")).findFirst().orElseThrow();
    // Bounded discovery uses actual stock BOOST pairs, empty/Boost Tuner 1/2 plates, at most 30 references.
    // It never writes a build or mutates catalog facts. The seed definition is frozen separately.
    int evaluated = 0;
    var boards = catalog.machines().values().stream().filter(m -> m.racingType() == RacingType.BOOST)
        .sorted(Comparator.comparing(Machine::name)).limit(10).toList();
    for (var machine : boards) {
      var parts = catalog.parts().values().stream().filter(p -> p.sourceMachineId().equals(machine.id())).toList();
      var front = parts.stream().filter(p -> p.type() == MachinePartType.FRONT).findFirst().orElseThrow();
      var rear = parts.stream().filter(p -> p.type() == MachinePartType.REAR).findFirst().orElseThrow();
      for (var gadgets : List.of(List.<UUID>of(), List.of(PassiveGadgetRules.id(60)), List.of(PassiveGadgetRules.id(60), PassiveGadgetRules.id(61)))) {
        var current = new BuildSelection(miku.id(), front.id(), rear.id(), null, gadgets);
        var locks = new BuildSelection(miku.id(), null, null, null, List.of());
        var request = new RecommendationRequest(version.id(), RacingType.BOOST, ACTIVE, current, locks, RecommendationMode.BALANCED, CONFIG);
        var result = new BuildRecommendationSolver(catalog, request, BuildRecommendationService.BUDGET).solve(); evaluated++;
        assertNotNull(result.selection(), result.reason());
        assertTrue(new BalancedObjective(result.currentStats(), ACTIVE, CONFIG).feasible(result.recommendedStats()));
        System.out.printf("BALANCED CATALOG %s gadgets=%s outcome=%s work=%d ms=%d reference=%s minimum=%s recommended=%s selection=%s%n",
            machine.name(), gadgets, result.outcome(), result.work(), result.elapsedMillis(), result.currentStats(), result.balanced().minimum(), result.recommendedStats(), result.selection());
      }
    }
    assertTrue(evaluated > 0 && evaluated <= 30);
    for (var type : RacingType.values()) {
      var machine = catalog.machines().values().stream().filter(m -> m.racingType() == type)
          .sorted(Comparator.comparing(Machine::name)).findFirst().orElseThrow();
      var parts = catalog.parts().values().stream().filter(p -> p.sourceMachineId().equals(machine.id())).toList();
      var current = new BuildSelection(miku.id(), parts.stream().filter(p -> p.type() == MachinePartType.FRONT).findFirst().orElseThrow().id(),
          parts.stream().filter(p -> p.type() == MachinePartType.REAR).findFirst().orElseThrow().id(),
          parts.stream().filter(p -> p.type() == MachinePartType.TIRE).map(MachinePart::id).findFirst().orElse(null), List.of());
      var request = new RecommendationRequest(version.id(), type, ACTIVE, current,
          new BuildSelection(null, null, null, null, List.of()), RecommendationMode.BALANCED, CONFIG);
      var result = new BuildRecommendationSolver(catalog, request, BuildRecommendationService.BUDGET).solve();
      assertNotNull(result.selection(), result.reason());
      assertTrue(new BalancedObjective(result.currentStats(), ACTIVE, CONFIG).feasible(result.recommendedStats()));
      System.out.printf("BALANCED UNLOCKED type=%s outcome=%s work=%d ms=%d%n", type, result.outcome(), result.work(), result.elapsedMillis());
    }
  }

  @Test void frozenDemoPassesNormalBuildRulesAndShowsTheRealTradeInIsolation() throws Exception {
    var definition = json.readValue(Path.of("scripts/optimizer-miku-balanced-v1.json").toFile(), Map.class);
    var account = Map.of("key", "optimizer", "username", "ringlab_demo_optimizer", "email", "ringlab_demo_optimizer@example.test");
    var bootstrap = Map.of("accounts", List.of(account), "password", "RingLabDemo!2026");
    var session = given().contentType("application/json").body(bootstrap).post("/api/dev-fixtures/demo-accounts")
        .then().statusCode(200).extract().jsonPath();
    String token = session.getString("[0].session.token");
    String author = session.getString("[0].session.user.id");
    given().contentType("application/json").body(bootstrap).post("/api/dev-fixtures/demo-accounts")
        .then().statusCode(200).body("[0].session.user.id", equalTo(author));
    var created = given().auth().oauth2(token).contentType("application/json").body(definition).post("/api/builds")
        .then().statusCode(200).extract().jsonPath();
    String id = created.getString("id");
    try {
      var current = new HashMap<String, Object>();
      for (String key : List.of("racerId", "frontPartId", "rearPartId", "tirePartId", "gadgetIds")) current.put(key, definition.get(key));
      var body = Map.of("gameVersionId", definition.get("gameVersionId"), "machineType", "BOOST", "mode", "BALANCED",
          "priorities", ACTIVE, "balanced", CONFIG, "current", current,
          "locked", Map.of("racerId", definition.get("racerId"), "gadgetIds", List.of()));
      var result = given().auth().oauth2(token).contentType("application/json").body(body).post("/api/build-recommendations")
          .then().statusCode(200).body("outcome", equalTo("ESTABLISHED")).body("selection.tirePartId", nullValue()).extract().jsonPath();
      assertEquals(116, result.getDouble("currentStats.boost"));
      assertEquals(110.2, result.getDouble("balanced.minimum.BOOST"));
      var referenceStats = json.convertValue(result.getMap("currentStats"), BaseStats.class);
      var recommendedStats = json.convertValue(result.getMap("recommendedStats"), BaseStats.class);
      var objective = new BalancedObjective(referenceStats, ACTIVE, CONFIG);
      // Expanded tuner combinations may improve the old winner; preserve every floor
      // and require at least the original demonstration's weighted result.
      assertTrue(objective.feasible(recommendedStats));
      assertTrue(objective.compare(recommendedStats, referenceStats) >= 0);
      assertTrue(objective.compare(recommendedStats, PassiveGadgetRules.points(31,47,61,24,112)) >= 0);
      given().get("/api/builds/" + id).then().statusCode(200).body("frontPart.id", equalTo(definition.get("frontPartId")))
          .body("rearPart.id", equalTo(definition.get("rearPartId"))).body("title", equalTo(definition.get("title")));
      System.out.printf("BALANCED FROZEN FIXTURE isolatedId=%s outcome=%s work=%d ms=%d%n", id, result.getString("outcome"), result.getLong("work"), result.getLong("elapsedMillis"));
    } finally {
      given().auth().oauth2(token).delete("/api/builds/" + id).then().statusCode(204);
    }
  }
}
