package dev.ringlab;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.application.gamedata.BaseStatsService;
import dev.ringlab.domain.gamedata.BaseStats;
import dev.ringlab.port.out.BaseStatsRepository;
import dev.ringlab.port.out.GameDataRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.junit.jupiter.api.Test;

@QuarkusTest
class BaseStatsIntegrationTest {
  private static final UUID LATEST = UUID.fromString("50000000-0000-0000-0000-000000000001");
  private static final UUID BASELINE = UUID.fromString("50000000-0000-0000-0000-000000000002");
  @Inject GameDataRepository game;
  @Inject BaseStatsRepository stats;
  @Inject BaseStatsService service;
  @Inject EntityManager em;

  @Test
  void latestVersionIncludesSourcedRacerAndPerPartStats() {
    var amy = game.listRacers().stream().filter(r -> r.name().equals("Amy Rose")).findFirst().orElseThrow();
    var speedster = game.listMachines().stream()
        .filter(m -> m.name().equals("Speedster Lightning")).findFirst().orElseThrow();
    var parts = game.listMachineParts().stream()
        .filter(p -> p.sourceMachineId().equals(speedster.id())).toList();
    var front = parts.stream().filter(p -> p.type().name().equals("FRONT")).findFirst().orElseThrow();
    var rear = parts.stream().filter(p -> p.type().name().equals("REAR")).findFirst().orElseThrow();
    var tire = parts.stream().filter(p -> p.type().name().equals("TIRE")).findFirst().orElseThrow();

    given().queryParam("gameVersionId", LATEST).queryParam("racerId", amy.id())
        .queryParam("frontPartId", front.id()).queryParam("rearPartId", rear.id())
        .queryParam("tirePartId", tire.id()).get("/api/stats/build").then().statusCode(200)
        .body("speed", equalTo(65)).body("acceleration", equalTo(30))
        .body("handling", equalTo(59)).body("power", equalTo(52)).body("boost", equalTo(34))
        .body("character.speed", notNullValue()).body("machine.speed", notNullValue());
    assertEquals(37, stats.racerStats(LATEST).size());
    assertEquals(159, stats.machinePartStats(LATEST).size());

    var whisper = game.listRacers().stream().filter(r -> r.name().equals("Whisper")).findFirst().orElseThrow();
    var hyperScorpion = game.listMachines().stream()
        .filter(m -> m.name().equals("Hyper Scorpion")).findFirst().orElseThrow();
    var hyperParts = game.listMachineParts().stream()
        .filter(p -> p.sourceMachineId().equals(hyperScorpion.id())).toList();
    given().queryParam("gameVersionId", LATEST).queryParam("racerId", whisper.id())
        .queryParam("frontPartId", hyperParts.stream().filter(p -> p.type().name().equals("FRONT")).findFirst().orElseThrow().id())
        .queryParam("rearPartId", hyperParts.stream().filter(p -> p.type().name().equals("REAR")).findFirst().orElseThrow().id())
        .queryParam("tirePartId", hyperParts.stream().filter(p -> p.type().name().equals("TIRE")).findFirst().orElseThrow().id())
        .get("/api/stats/build").then().statusCode(200)
        .body("speed", equalTo(49)).body("acceleration", equalTo(70))
        .body("handling", equalTo(43)).body("power", equalTo(38)).body("boost", equalTo(40));
  }

  @Test
  void knownVersionsUseIndependentCopiesOfTheCurrentStats() {
    var amy = game.listRacers().stream().filter(r -> r.name().equals("Amy Rose")).findFirst().orElseThrow();
    var speedster = game.listMachines().stream()
        .filter(m -> m.name().equals("Speedster Lightning")).findFirst().orElseThrow();
    var parts = game.listMachineParts().stream()
        .filter(p -> p.sourceMachineId().equals(speedster.id())).toList();
    for (var version : game.listGameVersions()) {
      assertEquals(37, stats.racerStats(version.id()).size());
      assertEquals(159, stats.machinePartStats(version.id()).size());
      given().queryParam("gameVersionId", version.id()).queryParam("racerId", amy.id())
          .queryParam("frontPartId", parts.stream().filter(p -> p.type().name().equals("FRONT")).findFirst().orElseThrow().id())
          .queryParam("rearPartId", parts.stream().filter(p -> p.type().name().equals("REAR")).findFirst().orElseThrow().id())
          .queryParam("tirePartId", parts.stream().filter(p -> p.type().name().equals("TIRE")).findFirst().orElseThrow().id())
          .get("/api/stats/build").then().statusCode(200)
          .body("speed", equalTo(65)).body("acceleration", equalTo(30))
          .body("handling", equalTo(59)).body("power", equalTo(52)).body("boost", equalTo(34));
    }
  }

  @Test
  void versionlessBuildsSerializeExplicitNulls() {
    given().get("/api/stats/build").then().statusCode(200)
        .body("keySet()", containsInAnyOrder("speed", "acceleration", "handling", "power", "boost", "character", "machine"))
        .body("speed", nullValue()).body("handling", nullValue());
    given().get("/api/stats/catalog").then().statusCode(400);
    given().queryParam("gameVersionId", UUID.randomUUID()).get("/api/stats/catalog").then().statusCode(404);
    given().queryParam("gameVersionId", UUID.randomUUID()).get("/api/stats/build").then().statusCode(404);
    given().queryParam("gameVersionId", BASELINE).queryParam("racerId", UUID.randomUUID())
        .get("/api/stats/build").then().statusCode(404);
    given().queryParam("gameVersionId", BASELINE).queryParam("frontPartId", UUID.randomUUID())
        .get("/api/stats/build").then().statusCode(404);
    var rear = game.listMachineParts().stream().filter(p -> p.type().name().equals("REAR")).findFirst().orElseThrow();
    given().queryParam("gameVersionId", BASELINE).queryParam("frontPartId", rear.id())
        .get("/api/stats/build").then().statusCode(400);
  }

  @Test
  void isolatedSyntheticSnapshotRoundTripsDecimalsPartialNullsAndVersionKeys() {
    UUID version = UUID.randomUUID();
    UUID racer = game.listRacers().getFirst().id();
    UUID machine = game.listMachines().getFirst().id();
    var parts = game.listMachineParts().stream().filter(p -> p.sourceMachineId().equals(machine)).toList();
    var front = parts.stream().filter(p -> p.type().name().equals("FRONT")).findFirst().orElseThrow().id();
    var rear = parts.stream().filter(p -> p.type().name().equals("REAR")).findFirst().orElseThrow().id();
    var tire = parts.stream().filter(p -> p.type().name().equals("TIRE")).findFirst().orElseThrow().id();
    try {
      QuarkusTransaction.requiringNew().run(() -> {
        em.createNativeQuery("INSERT INTO game_versions VALUES (:id, :name, '2000-01-01')")
            .setParameter("id", version).setParameter("name", "test-" + version.toString().substring(0, 20)).executeUpdate();
        em.createNativeQuery("INSERT INTO racer_stats VALUES (:racer, :version, 10, 0, 8, 5, 6)")
            .setParameter("racer", racer).setParameter("version", version).executeUpdate();
        for (var part : parts) em.createNativeQuery("INSERT INTO machine_part_stats VALUES (:part, :version, 2.5, 0, 1, 1, 1)")
            .setParameter("part", part.id()).setParameter("version", version).executeUpdate();
      });
      given().queryParam("gameVersionId", version).queryParam("racerId", racer)
          .queryParam("frontPartId", front).queryParam("rearPartId", rear).queryParam("tirePartId", tire)
          .get("/api/stats/build").then().statusCode(200)
          .body("speed", equalTo(17.5f)).body("acceleration", equalTo(0)).body("handling", equalTo(11));
      assertEquals(0, service.catalog(version).machines().get(machine).speed().compareTo(new java.math.BigDecimal("7.5")));
      given().queryParam("gameVersionId", version).get("/api/stats/catalog").then().statusCode(200)
          .body("racers.size()", equalTo(1)).body("machineParts.size()", equalTo(3));
      QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery(
          "UPDATE machine_part_stats SET handling = NULL WHERE machine_part_id = :part AND game_version_id = :version")
          .setParameter("part", front).setParameter("version", version).executeUpdate());
      given().queryParam("gameVersionId", version).queryParam("racerId", racer)
          .queryParam("frontPartId", front).queryParam("rearPartId", rear).queryParam("tirePartId", tire)
          .get("/api/stats/build").then().statusCode(200).body("speed", equalTo(17.5f)).body("handling", nullValue());
      assertNull(stats.machinePartStats(version).get(front).handling());
      assertEquals(BaseStats.UNKNOWN, service.build(null, racer, front, rear, tire));
      assertThrows(RuntimeException.class, () -> QuarkusTransaction.requiringNew().run(() ->
          em.createNativeQuery("INSERT INTO racer_stats (racer_id, game_version_id) VALUES (:racer, :version)")
              .setParameter("racer", racer).setParameter("version", version).executeUpdate()));
    } finally {
      QuarkusTransaction.requiringNew().run(() -> {
        em.createNativeQuery("DELETE FROM machine_part_stats WHERE game_version_id = :version").setParameter("version", version).executeUpdate();
        em.createNativeQuery("DELETE FROM racer_stats WHERE game_version_id = :version").setParameter("version", version).executeUpdate();
        em.createNativeQuery("DELETE FROM game_versions WHERE id = :version").setParameter("version", version).executeUpdate();
      });
    }
  }

  @Test
  void missingHistoricalStatsDoNotBlockCreateEditOrReadingExistingBuilds() {
    String token = VerifiedUserFixture.createToken(em);
    Map<String, Object> draft = new HashMap<>();
    draft.put("title", "Unknown historical stats");
    draft.put("description", "");
    draft.put("racerId", game.listRacers().stream().filter(r -> r.name().equals("Red")).findFirst().orElseThrow().id());
    draft.put("gadgetIds", List.of());
    for (String slot : List.of("FRONT", "REAR", "TIRE")) draft.put(slot.toLowerCase() + "PartId",
        game.listMachineParts().stream().filter(p -> p.type().name().equals(slot)).findFirst().orElseThrow().id());
    String id = given().auth().oauth2(token).contentType("application/json").body(draft)
        .post("/api/builds").then().statusCode(200).body("gameVersion", nullValue()).extract().path("id");
    try {
      draft.put("gameVersionId", BASELINE);
      given().auth().oauth2(token).contentType("application/json").body(draft).put("/api/builds/" + id)
          .then().statusCode(200).body("gameVersion.version", equalTo("1.3.1"));
      given().get("/api/builds/" + id).then().statusCode(200).body("racer.name", equalTo("Red"));
      draft.put("gameVersionId", null);
      given().auth().oauth2(token).contentType("application/json").body(draft).put("/api/builds/" + id)
          .then().statusCode(200).body("gameVersion", nullValue());
    } finally {
      given().auth().oauth2(token).delete("/api/builds/" + id).then().statusCode(204);
    }
  }
}
