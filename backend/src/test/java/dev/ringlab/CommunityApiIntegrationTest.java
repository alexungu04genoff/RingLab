package dev.ringlab;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.List;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class CommunityApiIntegrationTest {
  @jakarta.inject.Inject jakarta.persistence.EntityManager em;
  @jakarta.inject.Inject dev.ringlab.port.out.GameDataRepository game;
  @jakarta.inject.Inject dev.ringlab.port.in.BuildUseCase builds;
  @jakarta.inject.Inject dev.ringlab.application.community.CommunitySnapshotCache cache;

  @Test void privatizingACachedWinnerRevokesJsonDiscordAndExcludeTopImmediately() {
    addIsolatedExamples();
    cache.publicationChanged(new dev.ringlab.application.build.BuildPublicationChanged());
    var snapshot = cache.get();
    var winner = snapshot.items().getFirst().build();
    String beforeTag = given().get("/api/community/top-builds").then().statusCode(200).extract().header("ETag");
    var privateDraft = new dev.ringlab.port.in.BuildUseCase.Draft(winner.title(), winner.description(),
        winner.racerId(), winner.frontPartId(), winner.rearPartId(), winner.tirePartId(),
        winner.gameVersionId(), winner.remixedFromBuildId(), winner.gadgetIds(), List.copyOf(winner.recommendedMapIds()),
        dev.ringlab.domain.build.BuildVisibility.PRIVATE);
    try {
      builds.edit(winner.id(), winner.authorId(), privateDraft);
      var after = given().header("If-None-Match", beforeTag).get("/api/community/top-builds").then().statusCode(200)
          .body("items.build.id", not(hasItem(winner.id().toString()))).extract().header("ETag");
      assertNotEquals(beforeTag, after);
      given().get("/api/community/top-builds/discord").then().statusCode(200).body(not(containsString(winner.id().toString())));
      given().queryParam("excludeTop", true).get("/api/builds").then().statusCode(200)
          .body("items.id", not(hasItem(winner.id().toString())));
    } finally {
      var publicDraft = new dev.ringlab.port.in.BuildUseCase.Draft(winner.title(), winner.description(),
          winner.racerId(), winner.frontPartId(), winner.rearPartId(), winner.tirePartId(),
          winner.gameVersionId(), winner.remixedFromBuildId(), winner.gadgetIds(), List.copyOf(winner.recommendedMapIds()),
          dev.ringlab.domain.build.BuildVisibility.PUBLIC);
      builds.edit(winner.id(), winner.authorId(), publicDraft);
    }
    assertNotEquals(snapshot.revision(), cache.get().revision());
    // Identical score and publication chronology restore this fixture's eligibility/ranking.
    assertTrue(cache.get().items().stream().anyMatch(item -> item.build().id().equals(winner.id())));
  }

  private void addIsolatedExamples() {
    io.quarkus.narayana.jta.QuarkusTransaction.requiringNew().run(() -> {
      var author = java.util.UUID.randomUUID();
      var name = "top_" + author.toString().replace("-", "").substring(0, 16);
      em.createNativeQuery("INSERT INTO users (id, username, email, password_hash, created_at) VALUES (:id, :name, :email, 'test-only', CURRENT_TIMESTAMP)")
          .setParameter("id", author).setParameter("name", name).setParameter("email", name + "@example.test").executeUpdate();
      var machine = game.listMachines().stream().filter(m -> m.racingType() == dev.ringlab.domain.gamedata.RacingType.SPEED).findFirst().orElseThrow();
      var parts = game.listMachineParts().stream().filter(p -> p.sourceMachineId().equals(machine.id())).toList();
      for (int i = 0; i < 4; i++) {
        var b = new dev.ringlab.adapter.out.db.build.BuildDbEntity();
        b.id = java.util.UUID.randomUUID();
        b.authorId = author;
        b.racerId = game.listRacers().getFirst().id();
        b.frontPartId = parts.stream().filter(p -> p.type() == dev.ringlab.domain.gamedata.MachinePartType.FRONT).findFirst().orElseThrow().id();
        b.rearPartId = parts.stream().filter(p -> p.type() == dev.ringlab.domain.gamedata.MachinePartType.REAR).findFirst().orElseThrow().id();
        b.tirePartId = parts.stream().filter(p -> p.type() == dev.ringlab.domain.gamedata.MachinePartType.TIRE).findFirst().orElseThrow().id();
        b.title = i == 0 ? "[Wilson Demo] isolated example" : "Community API example " + i;
        b.description = "Isolated integration-test example";
        b.gameVersionId = game.listGameVersions().getFirst().id();
        b.createdAt = java.time.Instant.now().plusSeconds(10 - i);
        b.updatedAt = b.createdAt;
        b.visibility = dev.ringlab.domain.build.BuildVisibility.PUBLIC;
        b.firstPublishedAt = b.createdAt;
        em.persist(b);
      }
    });
  }
  @Test void publicSharedSnapshotConditionalRequestsAndUnsupportedParameters() throws Exception {
    addIsolatedExamples();
    var json = given().get("/api/community/top-builds").then().statusCode(200)
        .body("schemaVersion", equalTo(1)).body("ranking", equalTo("best-rated"))
        .body("scope", equalTo("overall")).body("patches", equalTo("all"))
        .header("Cache-Control", equalTo("public, no-cache, must-revalidate")).extract().response();
    String tag = json.header("ETag");
    assertNotNull(tag);
    assertTrue(json.jsonPath().getList("items").size() <= 3);
    List<String> topIds = json.jsonPath().getList("items.build.id");
    int completeTotal = given().queryParam("size", 1).get("/api/builds").then().statusCode(200)
        .extract().jsonPath().getInt("total");
    var withoutTop = given().queryParam("excludeTop", true).queryParam("size", 50)
        .get("/api/builds").then().statusCode(200).extract().response();
    assertEquals(completeTotal - topIds.size(), withoutTop.jsonPath().getInt("total"));
    assertTrue(java.util.Collections.disjoint(
        topIds, withoutTop.jsonPath().getList("items.id", String.class)));
    var pinned = given().queryParam("excludeId", topIds.toArray()).queryParam("size", 50)
        .get("/api/builds").then().statusCode(200).extract().response();
    assertEquals(completeTotal - topIds.size(), pinned.jsonPath().getInt("total"));
    assertTrue(java.util.Collections.disjoint(topIds, pinned.jsonPath().getList("items.id", String.class)));
    given().queryParam("excludeId", java.util.stream.IntStream.range(0, 4)
        .mapToObj(i -> java.util.UUID.randomUUID().toString()).toArray())
        .get("/api/builds").then().statusCode(400);
    given().header("If-None-Match", tag).get("/api/community/top-builds").then().statusCode(304).body(isEmptyString());
    given().header("If-None-Match", "\"different\", " + tag).get("/api/community/top-builds").then().statusCode(304);
    given().cookie("preferences", "wilson").header("Host", "attacker.example")
        .get("/api/community/top-builds").then().statusCode(200).header("ETag", equalTo(tag))
        .body("snapshotAt", equalTo(json.jsonPath().getString("snapshotAt")));
    var discord = given().get("/api/community/top-builds/discord").then().statusCode(200)
        .body("allowed_mentions.parse", empty()).extract().response();
    assertEquals(json.jsonPath().getList("items.buildUrl"), discord.jsonPath().getList("embeds.url"));
    assertNotEquals(tag, discord.header("ETag"));
    given().header("If-None-Match", discord.header("ETag")).get("/api/community/top-builds/discord")
        .then().statusCode(304).body(isEmptyString());
    for (var name : List.of("search", "racerId", "machineId", "gameVersionId", "sort", "page", "authorId")) {
      given().queryParam(name, "anything").get("/api/community/top-builds").then().statusCode(400);
      given().queryParam(name, "anything").get("/api/community/top-builds/discord").then().statusCode(400);
    }
    Files.createDirectories(Path.of("target", "community-examples"));
    Files.writeString(Path.of("target", "community-examples", "top-builds.json"), json.asPrettyString());
    Files.writeString(Path.of("target", "community-examples", "discord.json"), discord.asPrettyString());
  }
}
