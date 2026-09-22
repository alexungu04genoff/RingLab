package dev.ringlab.performance;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.ringlab.application.build.BuildService;
import dev.ringlab.adapter.out.db.auth.UserDbEntity;
import dev.ringlab.adapter.out.db.gamedata.GadgetDbEntity;
import dev.ringlab.adapter.out.db.gamedata.GameVersionDbEntity;
import dev.ringlab.adapter.out.db.gamedata.MachineDbEntity;
import dev.ringlab.adapter.out.db.gamedata.MachinePartDbEntity;
import dev.ringlab.adapter.out.db.gamedata.RacerDbEntity;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.build.ranking.BuildRanking;
import dev.ringlab.domain.build.ranking.BuildSort;
import dev.ringlab.port.out.BuildRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@QuarkusTest
@TestProfile(BuildBrowsePerformanceBenchmark.Profile.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BuildBrowsePerformanceBenchmark {
  private static final int[] DATASET_SIZES = {60, 500, 5_000};
  private static final int PAGE_SIZE = 12;
  private static final int WARM_UPS = 2;
  private static final int ITERATIONS = 7;
  private static final Instant CREATED_BASE = Instant.parse("2026-01-01T00:00:00Z");

  @Inject DataSource dataSource;
  @Inject EntityManager entityManager;
  @Inject BuildService buildService;
  @Inject BuildRepository buildRepository;
  @Inject SessionFactory sessionFactory;

  private Catalog catalog;

  @Test
  void measureBuildBrowseBaseline() throws Exception {
    Statistics statistics = sessionFactory.getStatistics();
    statistics.setStatisticsEnabled(true);
    catalog = readCatalog();

    System.out.println(
        "PERF_RESULT,dataset,scenario,candidates,returned,total,service_median_ms,http_median_ms,"
            + "service_sql,http_sql,enrichment_sql,bulk_vote_ids,logical_author_lookups,"
            + "logical_racer_lookups,logical_part_lookups,logical_machine_lookups,"
            + "logical_gadget_lookups,logical_version_lookups,logical_vote_lookups,"
            + "logical_remix_lookups");

    for (int datasetSize : DATASET_SIZES) {
      replaceDataset(datasetSize);
      ServiceMeasurement newest = null;
      for (Scenario scenario : scenarios()) {
        ServiceMeasurement service = measureService(statistics, scenario);
        if (scenario.sort() == BuildSort.NEWEST && scenario.page() == 0) newest = service;
        HttpMeasurement http = measureHttp(statistics, scenario);

        assertEquals(service.total(), http.total(), scenario.name());
        assertEquals(service.ids(), http.ids(), scenario.name());
        assertEquals(Math.min(PAGE_SIZE, Math.max(0, service.total() - scenario.page() * PAGE_SIZE)),
            http.returned(), scenario.name());

        System.out.printf(
            Locale.ROOT,
            "PERF_RESULT,%d,%s,%d,%d,%d,%.3f,%.3f,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
            datasetSize,
            scenario.name(),
            service.total(),
            http.returned(),
            http.total(),
            service.medianMilliseconds(),
            http.medianMilliseconds(),
            service.medianSql(),
            http.medianSql(),
            http.medianSql() - service.medianSql(),
            service.total(),
            http.returned(),
            http.returned(),
            http.returned() * 3,
            http.returned() * 3,
            http.gadgetLookups(),
            http.versionLookups(),
            http.returned(),
            http.remixLookups());
        System.out.printf(
            Locale.ROOT,
            "PERF_FANOUT,%d,%s,author=%d,racer=%d,part=%d,machine=%d,gadget=%d,version=%d%n",
            datasetSize, scenario.name(), http.authorLoads(), http.racerLoads(), http.partLoads(),
            http.machineLoads(), http.gadgetLoads(), http.versionLoads());
      }
      ServiceMeasurement withoutVotes = measureNewestWithoutVotes(statistics);
      System.out.printf(
          Locale.ROOT,
          "PERF_NEWEST_IMPACT,%d,current_ms=%.3f,without_votes_ms=%.3f,delta_ms=%.3f,"
              + "current_sql=%d,without_votes_sql=%d%n",
          datasetSize, newest.medianMilliseconds(), withoutVotes.medianMilliseconds(),
          newest.medianMilliseconds() - withoutVotes.medianMilliseconds(), newest.medianSql(),
          withoutVotes.medianSql());
    }
  }

  @AfterAll
  void removeGeneratedData() throws SQLException {
    if (dataSource == null) return;
    try (Connection connection = dataSource.getConnection()) {
      if (!isDisposableDatabase(connection)) return;
      try (Statement statement = connection.createStatement()) {
      statement.execute("truncate table comments, votes, build_gadgets, external_identities, builds, users");
      }
    }
  }

  private List<Scenario> scenarios() {
    return List.of(
        new Scenario("newest_page_0", BuildSort.NEWEST, 0, Map.of()),
        new Scenario("score_page_0", BuildSort.SCORE, 0, Map.of()),
        new Scenario("rated_page_0", BuildSort.BEST_RATED, 0, Map.of()),
        new Scenario("rated_racer", BuildSort.BEST_RATED, 0,
            Map.of("racerId", catalog.racers().getFirst().toString())),
        new Scenario("rated_machine", BuildSort.BEST_RATED, 0,
            Map.of("machineId", catalog.machines().getFirst().id().toString())),
        new Scenario("rated_version", BuildSort.BEST_RATED, 0,
            Map.of("gameVersionId", catalog.versions().getFirst().toString())),
        new Scenario("title_search", BuildSort.BEST_RATED, 0, Map.of("search", "Search Target")),
        new Scenario("rated_page_4", BuildSort.BEST_RATED, 4, Map.of()));
  }

  private ServiceMeasurement measureService(Statistics statistics, Scenario scenario) {
    for (int i = 0; i < WARM_UPS; i++) invokeService(scenario);

    List<Long> nanos = new ArrayList<>();
    List<Long> sqlCounts = new ArrayList<>();
    BuildService.Page last = null;
    for (int i = 0; i < ITERATIONS; i++) {
      statistics.clear();
      long started = System.nanoTime();
      last = invokeService(scenario);
      nanos.add(System.nanoTime() - started);
      sqlCounts.add(statistics.getPrepareStatementCount());
    }
    return new ServiceMeasurement(
        medianMilliseconds(nanos),
        medianLong(sqlCounts),
        last.total(),
        last.items().stream().map(Build::id).toList());
  }

  private BuildService.Page invokeService(Scenario scenario) {
    return QuarkusTransaction.requiringNew().call(() -> {
      entityManager.clear();
      return buildService.list(query(scenario));
    });
  }

  private HttpMeasurement measureHttp(Statistics statistics, Scenario scenario) {
    for (int i = 0; i < WARM_UPS; i++) invokeHttp(scenario);

    List<Long> nanos = new ArrayList<>();
    List<Long> sqlCounts = new ArrayList<>();
    Response last = null;
    for (int i = 0; i < ITERATIONS; i++) {
      statistics.clear();
      long started = System.nanoTime();
      last = invokeHttp(scenario);
      nanos.add(System.nanoTime() - started);
      sqlCounts.add(statistics.getPrepareStatementCount());
    }

    List<Map<String, Object>> items = last.jsonPath().getList("items");
    List<UUID> ids = items.stream().map(item -> UUID.fromString(item.get("id").toString())).toList();
    int gadgetLookups = items.stream()
        .mapToInt(item -> ((List<?>) item.get("gadgets")).size())
        .sum();
    int versionLookups = (int) items.stream().filter(item -> item.get("gameVersion") != null).count();
    int remixLookups = (int) items.stream().filter(item -> item.get("remixedFrom") != null).count();
    return new HttpMeasurement(
        medianMilliseconds(nanos),
        medianLong(sqlCounts),
        ((Number) last.jsonPath().get("total")).longValue(),
        items.size(), ids, gadgetLookups, versionLookups, remixLookups,
        entityLoads(statistics, UserDbEntity.class),
        entityLoads(statistics, RacerDbEntity.class),
        entityLoads(statistics, MachinePartDbEntity.class),
        entityLoads(statistics, MachineDbEntity.class),
        entityLoads(statistics, GadgetDbEntity.class),
        entityLoads(statistics, GameVersionDbEntity.class));
  }

  private ServiceMeasurement measureNewestWithoutVotes(Statistics statistics) {
    for (int i = 0; i < WARM_UPS; i++) invokeNewestWithoutVotes();
    List<Long> nanos = new ArrayList<>();
    List<Long> sqlCounts = new ArrayList<>();
    BuildService.Page last = null;
    for (int i = 0; i < ITERATIONS; i++) {
      statistics.clear();
      long started = System.nanoTime();
      last = invokeNewestWithoutVotes();
      nanos.add(System.nanoTime() - started);
      sqlCounts.add(statistics.getPrepareStatementCount());
    }
    return new ServiceMeasurement(medianMilliseconds(nanos), medianLong(sqlCounts), last.total(),
        last.items().stream().map(Build::id).toList());
  }

  private BuildService.Page invokeNewestWithoutVotes() {
    return QuarkusTransaction.requiringNew().call(() -> {
      entityManager.clear();
      List<BuildRanking.Candidate> candidates = buildRepository.searchCandidates(
          new BuildRepository.Filter(null, null, null, null, null));
      List<BuildRanking.Candidate> ranked = candidates.stream()
          .sorted(BuildRanking.comparator(BuildSort.NEWEST, Map.of(), Map.of()))
          .toList();
      List<UUID> pageIds = ranked.subList(0, Math.min(PAGE_SIZE, ranked.size())).stream()
          .map(BuildRanking.Candidate::id).toList();
      Map<UUID, Build> hydrated = buildRepository.findAll(pageIds).stream()
          .collect(java.util.stream.Collectors.toMap(Build::id, build -> build));
      return new BuildService.Page(pageIds.stream().map(hydrated::get)
          .filter(java.util.Objects::nonNull).toList(), ranked.size());
    });
  }

  private static long entityLoads(Statistics statistics, Class<?> entityClass) {
    return statistics.getEntityStatistics(entityClass.getName()).getLoadCount();
  }

  private Response invokeHttp(Scenario scenario) {
    Map<String, Object> parameters = new LinkedHashMap<>();
    parameters.put("sort", restSort(scenario.sort()));
    parameters.put("page", scenario.page());
    parameters.put("size", PAGE_SIZE);
    parameters.putAll(scenario.parameters());
    return given().queryParams(parameters).when().get("/api/builds").then().statusCode(200).extract().response();
  }

  private BuildService.Query query(Scenario scenario) {
    Map<String, String> p = scenario.parameters();
    return new BuildService.Query(
        new BuildRepository.Filter(
            p.get("search"), uuid(p.get("racerId")), uuid(p.get("machineId")), null,
            uuid(p.get("gameVersionId"))),
        scenario.sort(), scenario.page(), PAGE_SIZE);
  }

  private static UUID uuid(String value) {
    return value == null ? null : UUID.fromString(value);
  }

  private static String restSort(BuildSort sort) {
    return switch (sort) {
      case NEWEST -> "newest";
      case SCORE -> "score";
      case BEST_RATED -> "rated";
    };
  }

  private void replaceDataset(int size) throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      if (!isDisposableDatabase(connection)) {
        throw new IllegalStateException(
            "Performance benchmark refuses to modify any database except ringlab_benchmark");
      }
      connection.setAutoCommit(false);
      try (Statement statement = connection.createStatement()) {
        statement.execute("truncate table comments, votes, build_gadgets, external_identities, builds, users");
      }
      insertUsers(connection);
      insertBuilds(connection, size);
      insertVotes(connection, size);
      connection.commit();
    }
  }

  private void insertUsers(Connection connection) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "insert into users(id,username,email,password_hash,created_at) values (?,?,?,?,?)")) {
      for (int i = 0; i < 20; i++) addUser(statement, authorId(i), "perf_author_" + i);
      for (int i = 0; i < 50; i++) addUser(statement, voterId(i), "perf_voter_" + i);
      statement.executeBatch();
    }
  }

  private static void addUser(PreparedStatement statement, UUID id, String username) throws SQLException {
    statement.setObject(1, id);
    statement.setString(2, username);
    statement.setString(3, username + "@example.test");
    statement.setNull(4, java.sql.Types.VARCHAR);
    statement.setTimestamp(5, Timestamp.from(CREATED_BASE));
    statement.addBatch();
  }

  private void insertBuilds(Connection connection, int size) throws SQLException {
    String sql = "insert into builds(id,title,description,author_id,racer_id,front_part_id,"
        + "rear_part_id,tire_part_id,game_version_id,remixed_from_build_id,created_at,updated_at)"
        + " values (?,?,?,?,?,?,?,?,?,?,?,?)";
    try (PreparedStatement builds = connection.prepareStatement(sql);
         PreparedStatement gadgets = connection.prepareStatement(
             "insert into build_gadgets(build_id,gadget_id,position) values (?,?,?)")) {
      for (int i = 0; i < size; i++) {
        UUID buildId = buildId(i);
        MachineParts machine = catalog.machines().get(i % catalog.machines().size());
        builds.setObject(1, buildId);
        builds.setString(2, i % 10 == 0 ? "Search Target " + i : "Benchmark Build " + i);
        builds.setString(3, "Generated disposable performance build " + i);
        builds.setObject(4, authorId(i % 20));
        builds.setObject(5, catalog.racers().get(i % catalog.racers().size()));
        builds.setObject(6, machine.front());
        builds.setObject(7, machine.rear());
        builds.setObject(8, machine.tire());
        builds.setObject(9, catalog.versions().get(i % catalog.versions().size()));
        builds.setObject(10, i > 0 && i % 10 == 0 ? buildId(i - 1) : null);
        Timestamp timestamp = Timestamp.from(CREATED_BASE.plusSeconds(i));
        builds.setTimestamp(11, timestamp);
        builds.setTimestamp(12, timestamp);
        builds.addBatch();

        for (int position = 0; position < 2; position++) {
          gadgets.setObject(1, buildId);
          gadgets.setObject(2, catalog.gadgets().get((i + position) % catalog.gadgets().size()));
          gadgets.setInt(3, position);
          gadgets.addBatch();
        }
        if ((i + 1) % 500 == 0) {
          builds.executeBatch();
          gadgets.executeBatch();
        }
      }
      builds.executeBatch();
      gadgets.executeBatch();
    }
  }

  private void insertVotes(Connection connection, int size) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(
        "insert into votes(id,user_id,build_id,value) values (?,?,?,?)")) {
      for (int buildIndex = 0; buildIndex < size; buildIndex++) {
        VoteCounts counts = voteCounts(buildIndex);
        int voteIndex = 0;
        for (; voteIndex < counts.upvotes(); voteIndex++) {
          addVote(statement, buildIndex, voteIndex, (short) 1);
        }
        for (int down = 0; down < counts.downvotes(); down++, voteIndex++) {
          addVote(statement, buildIndex, voteIndex, (short) -1);
        }
        if ((buildIndex + 1) % 250 == 0) statement.executeBatch();
      }
      statement.executeBatch();
    }
  }

  private static void addVote(PreparedStatement statement, int buildIndex, int voteIndex, short value)
      throws SQLException {
    statement.setObject(1, namedId("vote-" + buildIndex + "-" + voteIndex));
    statement.setObject(2, voterId(voteIndex));
    statement.setObject(3, buildId(buildIndex));
    statement.setShort(4, value);
    statement.addBatch();
  }

  private Catalog readCatalog() throws SQLException {
    try (Connection connection = dataSource.getConnection()) {
      List<UUID> racers = ids(connection, "select id from racers order by id limit 5");
      List<UUID> versions = ids(connection, "select id from game_versions order by id");
      List<UUID> gadgets = ids(connection,
          "select id from gadgets where slot_cost = 1 order by id limit 5");
      List<MachineParts> machines = new ArrayList<>();
      try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
          "select front.source_machine_id, front.id, rear.id, tire.id"
              + " from machine_parts front"
              + " join machine_parts rear on rear.source_machine_id=front.source_machine_id"
              + " and rear.part_type='REAR'"
              + " join machine_parts tire on tire.source_machine_id=front.source_machine_id"
              + " and tire.part_type='TIRE'"
              + " where front.part_type='FRONT' order by front.source_machine_id limit 5")) {
        while (rows.next()) {
          machines.add(new MachineParts(
              rows.getObject(1, UUID.class), rows.getObject(2, UUID.class),
              rows.getObject(3, UUID.class), rows.getObject(4, UUID.class)));
        }
      }
      if (racers.size() < 5 || machines.size() < 5 || versions.isEmpty() || gadgets.size() < 2) {
        throw new IllegalStateException("Flyway catalog does not contain the required benchmark fixtures");
      }
      return new Catalog(racers, machines, versions, gadgets);
    }
  }

  private static List<UUID> ids(Connection connection, String sql) throws SQLException {
    List<UUID> result = new ArrayList<>();
    try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
      while (rows.next()) result.add(rows.getObject(1, UUID.class));
    }
    return result;
  }

  private static boolean isDisposableDatabase(Connection connection) throws SQLException {
    try (Statement statement = connection.createStatement();
         ResultSet rows = statement.executeQuery("select current_database()")) {
      rows.next();
      return "ringlab_benchmark".equals(rows.getString(1));
    }
  }

  private static VoteCounts voteCounts(int buildIndex) {
    return switch (buildIndex % 8) {
      case 0 -> new VoteCounts(40, 1);
      case 1 -> new VoteCounts(3, 0);
      case 2 -> new VoteCounts(20, 5);
      case 3 -> new VoteCounts(1, 0);
      case 4 -> new VoteCounts(20, 20);
      case 5 -> new VoteCounts(0, 0);
      case 6 -> new VoteCounts(0, 1);
      default -> new VoteCounts(5, 9);
    };
  }

  private static UUID buildId(int index) { return namedId("build-" + index); }
  private static UUID authorId(int index) { return namedId("author-" + index); }
  private static UUID voterId(int index) { return namedId("voter-" + index); }

  private static UUID namedId(String value) {
    return UUID.nameUUIDFromBytes(("ringlab-performance-" + value).getBytes(StandardCharsets.UTF_8));
  }

  private static double medianMilliseconds(List<Long> nanos) {
    return medianLong(nanos) / 1_000_000.0;
  }

  private static long medianLong(List<Long> values) {
    return values.stream().sorted().skip(values.size() / 2).findFirst().orElseThrow();
  }

  public static class Profile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return Map.of(
          "ringlab.rate-limit.build-list", "100000/PT1M",
          "quarkus.hibernate-orm.statistics", "true");
    }
  }

  private record Scenario(String name, BuildSort sort, int page, Map<String, String> parameters) {}
  private record MachineParts(UUID id, UUID front, UUID rear, UUID tire) {}
  private record Catalog(List<UUID> racers, List<MachineParts> machines, List<UUID> versions,
                         List<UUID> gadgets) {}
  private record VoteCounts(int upvotes, int downvotes) {}
  private record ServiceMeasurement(double medianMilliseconds, long medianSql, long total,
                                    List<UUID> ids) {}
  private record HttpMeasurement(double medianMilliseconds, long medianSql, long total, int returned,
                                 List<UUID> ids, int gadgetLookups, int versionLookups,
                                 int remixLookups, long authorLoads, long racerLoads, long partLoads,
                                 long machineLoads, long gadgetLoads, long versionLoads) {}
}
