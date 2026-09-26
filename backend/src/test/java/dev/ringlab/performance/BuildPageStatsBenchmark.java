package dev.ringlab.performance;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.port.out.GameDataRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;

/** Explicitly invoked benchmark, never included in the ordinary Surefire suite. */
@QuarkusTest
class BuildPageStatsBenchmark {
  private static final UUID AUTHOR = UUID.fromString("abc00000-0000-0000-0000-000000000001");
  private static final UUID EMPTY_PATCH = UUID.fromString("abc00000-0000-0000-0000-000000000002");
  @Inject EntityManager em;
  @Inject GameDataRepository game;
  @Inject SessionFactory sessions;

  @Test
  void measurePageAcquisition() {
    String database = (String) em.createNativeQuery("select current_database()", String.class).getSingleResult();
    assertTrue(database.startsWith("ringlab_browse_"), "Only a disposable ringlab_browse_* database may be benchmarked");
    var racer = game.listRacers().stream().filter(r -> r.name().equals("Sonic the Hedgehog")).findFirst().orElseThrow();
    var machine = game.listMachines().stream().filter(m -> m.name().equals("Road Dragoon")).findFirst().orElseThrow();
    var parts = game.listMachineParts().stream().filter(p -> p.sourceMachineId().equals(machine.id())).toList();
    UUID front = parts.stream().filter(p -> p.type().name().equals("FRONT")).findFirst().orElseThrow().id();
    UUID rear = parts.stream().filter(p -> p.type().name().equals("REAR")).findFirst().orElseThrow().id();
    UUID tire = parts.stream().filter(p -> p.type().name().equals("TIRE")).findFirst().orElseThrow().id();
    var versions = game.listGameVersions();
    boolean enriched = Boolean.getBoolean("ringlab.benchmark.includeStats");
    Statistics statistics = sessions.getStatistics();
    statistics.setStatisticsEnabled(true);
    try {
      QuarkusTransaction.requiringNew().run(() -> {
        em.createNativeQuery("insert into users(id,username,email,created_at) values (:id,'browse_benchmark','browse_benchmark@example.test',now())")
            .setParameter("id", AUTHOR).executeUpdate();
        em.createNativeQuery("insert into game_versions(id,version,released_at) values (:id,'browse-empty','2000-01-01')")
            .setParameter("id", EMPTY_PATCH).executeUpdate();
        for (String scenario : List.of("one-patch", "mixed-patch", "missing-versionless")) {
          for (int i = 0; i < 12; i++) {
            UUID patch = scenario.equals("one-patch") ? versions.getFirst().id()
                : scenario.equals("mixed-patch") ? versions.get(i % 2).id()
                : i < 4 ? versions.getFirst().id() : i < 8 ? EMPTY_PATCH : null;
            em.createNativeQuery("insert into builds(id,title,description,author_id,racer_id,front_part_id,rear_part_id,tire_part_id,game_version_id,created_at,updated_at) "
                + "values (:id,:title,'',:author,:racer,:front,:rear,:tire,:patch,'2026-01-01','2026-01-01')")
                .setParameter("id", UUID.nameUUIDFromBytes((scenario + i).getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .setParameter("title", "browse-benchmark-" + scenario + "-" + i).setParameter("author", AUTHOR)
                .setParameter("racer", racer.id()).setParameter("front", front).setParameter("rear", rear)
                .setParameter("tire", tire).setParameter("patch", patch).executeUpdate();
          }
        }
      });
      System.out.println("PAGE_STATS,mode,scenario,http_stats,http_total,racer_map_queries,part_map_queries,list_sql,total_sql,median_sequential_ms");
      for (String scenario : List.of("one-patch", "mixed-patch", "missing-versionless")) {
        acquire(scenario, enriched, statistics); // One unrecorded warm-up per scenario.
        List<Measurement> measurements = new ArrayList<>();
        for (int i = 0; i < 3; i++) measurements.add(acquire(scenario, enriched, statistics));
        measurements.sort(Comparator.comparingLong(Measurement::nanos));
        var median = measurements.get(1);
        System.out.printf(Locale.ROOT, "PAGE_STATS,%s,%s,%d,%d,%d,%d,%d,%d,%.3f%n", enriched ? "after" : "before", scenario,
            median.statsHttp(), median.statsHttp() + 1, median.racerMaps(), median.partMaps(), median.listSql(), median.totalSql(), median.nanos() / 1_000_000.0);
      }
    } finally {
      QuarkusTransaction.requiringNew().run(() -> {
        em.createNativeQuery("delete from builds where author_id = :id").setParameter("id", AUTHOR).executeUpdate();
        em.createNativeQuery("delete from users where id = :id").setParameter("id", AUTHOR).executeUpdate();
        em.createNativeQuery("delete from game_versions where id = :id").setParameter("id", EMPTY_PATCH).executeUpdate();
      });
      statistics.setStatisticsEnabled(false);
    }
  }

  private Measurement acquire(String scenario, boolean enriched, Statistics statistics) {
    statistics.clear();
    long started = System.nanoTime();
    var page = given().queryParam("authorId", AUTHOR).queryParam("search", "browse-benchmark-" + scenario)
        .queryParam("sort", "newest").queryParam("size", 12).queryParam("includeStats", enriched)
        .get("/api/builds").then().statusCode(200).extract().jsonPath();
    long listSql = statistics.getPrepareStatementCount();
    List<Map<String, Object>> items = page.getList("items");
    assertEquals(12, items.size());
    int statsHttp = 0;
    for (var item : items) {
      if (enriched) {
        assertNotNull(page.getMap("statsByBuildId").get(item.get("id")));
      } else if (item.get("gameVersion") instanceof Map<?, ?> patch) {
        var parameters = new HashMap<String, Object>();
        parameters.put("gameVersionId", patch.get("id"));
        for (String reference : List.of("racer", "frontPart", "rearPart", "tirePart")) {
          if (item.get(reference) instanceof Map<?, ?> value) parameters.put(reference + "Id", value.get("id"));
        }
        given().queryParams(parameters).get("/api/stats/build").then().statusCode(200);
        statsHttp++;
      }
    }
    long nanos = System.nanoTime() - started;
    return new Measurement(statsHttp, mapReads(statistics, "racer_stats"), mapReads(statistics, "machine_part_stats"),
        listSql, statistics.getPrepareStatementCount(), nanos);
  }

  private long mapReads(Statistics statistics, String table) {
    return Arrays.stream(statistics.getQueries()).filter(query -> query.contains("FROM " + table + " WHERE"))
        .mapToLong(query -> statistics.getQueryStatistics(query).getExecutionCount()).sum();
  }
  private record Measurement(int statsHttp, long racerMaps, long partMaps, long listSql, long totalSql, long nanos) {}
}
