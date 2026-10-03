package dev.ringlab.adapter.in.rest.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ringlab.adapter.in.rest.auth.CurrentUser;
import dev.ringlab.adapter.in.rest.build.request.BuildRecommendationRequest;
import dev.ringlab.application.build.*;
import dev.ringlab.application.collection.CollectionService;
import dev.ringlab.domain.build.recommendation.*;
import dev.ringlab.domain.collection.CollectionExclusions;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.importing.RuleFixtures;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact synthetic facts travel through JSON, the REST adapter, application and domain. */
class BuildRecommendationContractTest {
  private final ObjectMapper json = new ObjectMapper();
  private static final UUID VERSION = id(1), ACTOR = id(2), MACHINE = id(3), FRONT = id(4), REAR = id(5);

  @Test void exactThresholdBoundarySurvivesTheWholeTransportMapping() throws Exception {
    var loader = new RecommendationCatalogLoader(null, null, null) {
      @Override public RecommendationCatalog load(UUID version) { assertEquals(VERSION, version); return catalog(); }
    };
    var collection = new CollectionService(null, null) {
      @Override public CollectionExclusions load(UUID actor) { assertEquals(ACTOR, actor); return CollectionExclusions.NONE; }
    };
    var resource = new BuildRecommendationRestResource(actor(), new BuildRecommendationService(loader, collection));
    try (var response = resource.recommend(request())) {
      var body = json.readTree(json.writeValueAsString(response.getEntity()));
      assertEquals(200, response.getStatus());
      assertEquals("ESTABLISHED", body.get("outcome").asText());
      assertTrue(body.at("/balanced/proven").asBoolean());
      assertEquals(id(11).toString(), body.at("/selection/racerId").asText());
      assertTrue(body.get("currentStats").isNull());
      assertTrue(body.at("/selection/tirePartId").isNull());
      assertEquals(0, body.at("/balanced/stages/0/threshold").decimalValue().compareTo(new BigDecimal("95")));
      assertEquals(3, body.at("/balanced/stages/0/candidatesBefore").asLong());
      assertEquals(2, body.at("/balanced/stages/0/candidatesAfter").asLong());
      assertEquals(2, body.at("/balanced/stages/1/candidatesBefore").asLong());
      assertEquals(1, body.at("/balanced/stages/1/candidatesAfter").asLong());
      assertEquals(0, body.at("/recommendedStats/boost").decimalValue().compareTo(new BigDecimal("95")));
      // C's 94.99 Boost cannot enter the second stage, despite its higher Speed.
      assertEquals(20, body.at("/balanced/stages/1/best").intValue());
    }
  }

  @Test void truncatedSearchStaysBestFoundThroughTheRestResponse() throws Exception {
    RecommendationResult truncated = null;
    for (int work = 1; work <= 100; work++) {
      var candidate = new BuildRecommendationSolver(catalog(), request().toDomain(),
          new BuildRecommendationSolver.Budget(work, Duration.ofSeconds(2))).solve();
      if (candidate.outcome() == RecommendationResult.Outcome.BEST_FOUND) { truncated = candidate; break; }
    }
    assertNotNull(truncated, "The fixture must interrupt after finding a usable candidate and before proof");
    var found = truncated;
    var resource = new BuildRecommendationRestResource(actor(), (actor, request) -> found);
    try (var response = resource.recommend(request())) {
      var body = json.readTree(json.writeValueAsString(response.getEntity()));
      assertEquals("BEST_FOUND", body.get("outcome").asText());
      assertFalse(body.at("/balanced/proven").asBoolean());
      assertEquals(0, body.at("/balanced/stages").size());
      assertFalse(body.get("selection").isNull());
    }
  }

  private BuildRecommendationRequest request() throws Exception {
    return json.readValue("""
        {"gameVersionId":"%s","machineType":"BOOST","mode":"BALANCED",
         "priorities":["BOOST","SPEED","ACCELERATION","HANDLING","POWER"],
         "current":{"gadgetIds":[]},"locked":{"gadgetIds":[]},"gadgetScope":"KEEP_CURRENT",
         "balanced":{"maximumLossPercent":{"BOOST":5.00,"SPEED":0},"ignored":["ACCELERATION","HANDLING","POWER"]}}
        """.formatted(VERSION), BuildRecommendationRequest.class);
  }

  private static CurrentUser actor() { return new CurrentUser(null, null) { @Override public UUID id() { return ACTOR; } }; }
  private static UUID id(int value) { return new UUID(0, value); }
  private static BaseStats stats(String speed, String boost) {
    return new BaseStats(new BigDecimal(speed), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal(boost));
  }
  private static RecommendationCatalog catalog() {
    return new RecommendationCatalog(new GameVersion(VERSION, "1.4.1", LocalDate.EPOCH),
        Map.of(id(10), new Racer(id(10), "A", RacingType.BOOST, null),
            id(11), new Racer(id(11), "B", RacingType.BOOST, null), id(12), new Racer(id(12), "C", RacingType.BOOST, null)),
        Map.of(MACHINE, new Machine(MACHINE, "Synthetic board", RacingType.BOOST, null)),
        Map.of(FRONT, new MachinePart(FRONT, MACHINE, MachinePartType.FRONT), REAR, new MachinePart(REAR, MACHINE, MachinePartType.REAR)),
        Map.of(), Map.of(id(10), stats("1", "100"), id(11), stats("20", "95.00"), id(12), stats("99", "94.99")),
        Map.of(FRONT, BaseStats.ZERO, REAR, BaseStats.ZERO), RuleFixtures.snapshot());
  }
}
