package dev.ringlab;

import static dev.ringlab.importing.ImportFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.adapter.in.catalog.GameDataCsvReader;
import dev.ringlab.adapter.out.db.gamedata.GameDataImportDbAdapter;
import dev.ringlab.adapter.out.db.gamedata.GadgetRuleDbAdapter;
import dev.ringlab.application.gamedata.GameDataImportService;
import dev.ringlab.application.gamedata.BaseStatsService;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.domain.gamedata.importing.*;
import dev.ringlab.domain.gamedata.importing.GameDataSet.*;
import dev.ringlab.domain.gamedata.importing.GameDataRuleSet.*;
import dev.ringlab.port.out.BuildRepository;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import javax.sql.DataSource;
import org.eclipse.microprofile.config.ConfigProvider;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.postgresql.ds.PGSimpleDataSource;

@QuarkusTest
class GameDataImportIntegrationTest {
  @Inject DataSource dataSource;
  @Inject EntityManager em;
  @Inject BuildRepository builds;
  @Inject BaseStatsService stats;
  private String schema;
  private PGSimpleDataSource isolated;
  private GameDataImportDbAdapter repository;
  private GameDataImportService importer;
  private GameDataSet canonical;

  @BeforeEach void database(TestInfo test) throws Exception {
    schema = "import_test_" + UUID.randomUUID().toString().replace("-", "");
    var migration = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
        .locations("classpath:db/migration");
    if (test.getTestMethod().orElseThrow().getName().equals("phase5bMigrationPreservesAnInvalidatedLegacyPlate")) migration.target("34");
    migration.load().migrate();
    isolated = new PGSimpleDataSource();
    try (var c = dataSource.getConnection()) { isolated.setURL(c.getMetaData().getURL()); }
    var config = ConfigProvider.getConfig();
    isolated.setUser(config.getValue("quarkus.datasource.username", String.class));
    isolated.setPassword(config.getValue("quarkus.datasource.password", String.class));
    isolated.setCurrentSchema(schema);
    repository = new GameDataImportDbAdapter(isolated);
    importer = new GameDataImportService(repository);
    canonical = new GameDataCsvReader().read(Path.of("../game-data"));
    if (!Set.of("phase5bMigrationPreservesAnInvalidatedLegacyPlate", "presentationImportUpdatesOnlyReviewedGadgetMetadata")
        .contains(test.getTestMethod().orElseThrow().getName())) {
      var initial = importer.plan(canonical);
      importer.apply(canonical, initial.approvalToken());
    }
  }

  @AfterEach void cleanup() throws Exception {
    if (schema != null) try (var c = dataSource.getConnection(); var s = c.createStatement()) {
      s.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }
  }

  @Test void phase5bMigrationPreservesAnInvalidatedLegacyPlate() throws Exception {
    var twoSlot = canonical.gadgets().stream().map(CatalogRow::value)
        .filter(g -> Integer.valueOf(2).equals(g.slotCost()) && !g.id().toString().startsWith("84000000-")
            && !g.id().equals(PassiveGadgetRules.id(3))).limit(2).map(Gadget::id).toList();
    var ids = List.of(PassiveGadgetRules.id(3),twoSlot.get(0),twoSlot.get(1));
    try (var c = isolated.getConnection(); var s = c.createStatement()) {
      s.executeUpdate("INSERT INTO users(id,username,email,password_hash,created_at) VALUES ('" + id(501) + "','legacy-plate','legacy@example.test','unused',now())");
      s.executeUpdate("INSERT INTO builds(id,title,description,author_id,racer_id,front_part_id,rear_part_id,tire_part_id,game_version_id,created_at,updated_at) VALUES ('"
          + id(500) + "','Legacy plate','Preserve selections','" + id(501)
          + "','013ecae2-55b9-58bb-b1c5-b054578058ff','10000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000001','50000000-0000-0000-0000-000000000001',now(),now())");
      try (var insert = c.prepareStatement("INSERT INTO build_gadgets(build_id,gadget_id,position) VALUES (?,?,?)")) {
        for (int i = 0; i < ids.size(); i++) { insert.setObject(1,id(500)); insert.setObject(2,ids.get(i)); insert.setInt(3,i); insert.executeUpdate(); }
      }
    }
    assertTrue(dev.ringlab.domain.build.GadgetPlate.canFit(List.of(1,2,2)));
    Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
        .locations("classpath:db/migration").load().migrate();
    try (var c = isolated.getConnection(); var query = c.prepareStatement(
        "SELECT bg.gadget_id,g.slot_cost FROM build_gadgets bg JOIN gadgets g ON g.id=bg.gadget_id WHERE bg.build_id=? ORDER BY bg.position")) {
      query.setObject(1,id(500));
      var retained = new ArrayList<UUID>(); var costs = new ArrayList<Integer>();
      try (var rows = query.executeQuery()) { while (rows.next()) { retained.add(rows.getObject(1,UUID.class)); costs.add(rows.getInt(2)); } }
      assertEquals(ids,retained); assertEquals(List.of(2,2,2),costs);
      assertFalse(dev.ringlab.domain.build.GadgetPlate.canFit(costs));
    }
    var plan = importer.plan(canonical);
    assertTrue(plan.safe(),plan.unsafeChanges().toString()); assertEquals(0,plan.inserts()); assertEquals(117,plan.updateCount());
  }

  @Test void presentationImportUpdatesOnlyReviewedGadgetMetadata() throws Exception {
    var before = repository.read();
    var plan = importer.plan(canonical);
    assertTrue(plan.safe(), plan.unsafeChanges().toString());
    assertEquals(0, plan.inserts()); assertEquals(117, plan.updateCount());
    assertEquals(117, plan.updates().gadgets().size());
    var old = before.gadgets().stream().collect(java.util.stream.Collectors.toMap(r -> r.value().id(), CatalogRow::value));
    for (var row : plan.updates().gadgets()) {
      var prior = old.get(row.value().id()); var next = row.value();
      assertEquals(prior.name(), next.name()); assertEquals(prior.description(), next.description());
      assertEquals(prior.slotCost(), next.slotCost());
    }
    importer.apply(canonical, plan.approvalToken());
    var second = importer.plan(canonical);
    assertTrue(second.safe()); assertEquals(0, second.inserts()); assertEquals(0, second.updateCount());
    var after = repository.read();
    assertEquals(before.snapshots(), after.snapshots()); assertEquals(before.ruleSets(), after.ruleSets());
    assertEquals(before.racers(), after.racers()); assertEquals(before.parts(), after.parts());
    assertEquals(before.machines(), after.machines()); assertEquals(before.maps(), after.maps());
    assertEquals(before.versions(), after.versions());
    try (var connection = isolated.getConnection(); var query = connection.createStatement();
        var rows = query.executeQuery("SELECT count(*) FROM gadgets WHERE acquisition_kind='FESTIVAL_REWARD' AND acquisition_label IS NOT NULL")) {
      assertTrue(rows.next()); assertEquals(18, rows.getInt(1));
    }
  }

  @Test void canonicalFilesMatchImportedPresentationAndReimportWritesNothing() {
    var plan = importer.plan(canonical);
    assertTrue(plan.safe(), plan.unsafeChanges().toString());
    assertEquals(0, plan.inserts()); assertEquals(0, plan.updateCount());
    importer.apply(canonical, plan.approvalToken());
    var second = importer.plan(canonical);
    assertEquals(plan, second);
    var runtime = new GadgetRuleDbAdapter(isolated);
    assertEquals(dev.ringlab.importing.RuleFixtures.snapshot(), runtime.findByVersion("1.4.1").orElseThrow());
    assertTrue(runtime.findByVersion("missing-version").isEmpty());
    assertTrue(runtime.findByVersion("1.3.1").isEmpty());
  }

  @Test void newRuleFactsAndBaseStatsAreImportedTogetherWithoutEnablingAnUnreviewedPatch() {
    var edit = new Edit(canonical);
    var next = new GameVersion(id(950), "test-rules-next", LocalDate.of(2026, 12, 1));
    edit.versions.add(row(next));
    var oldStats = latestStats();
    edit.snapshots.add(new VersionStats(next.id(), oldStats.racers(), completeParts(oldStats.parts())));
    var original = canonical.ruleSets().getFirst();
    edit.ruleSets.add(new GameDataRuleSet(row(new Metadata(next.id(), "test-passive-next", "test-scenario-next")),
        original.passive(), original.scenario(), original.sources()));
    var runtime = new GadgetRuleDbAdapter(isolated);
    var oldRules = runtime.findByVersion("1.4.1").orElseThrow();
    var base = new BaseStatsBreakdown(BaseStats.ZERO, BaseStats.ZERO, BaseStats.ZERO);
    var selected = canonical.gadgets().stream().map(CatalogRow::value)
        .filter(g -> List.of(PassiveGadgetRules.id(55), PassiveGadgetRules.id(34)).contains(g.id())).toList();
    var before = PassiveStatsCalculator.calculate(oldRules, base, "1.4.1", RacingType.ACCELERATION, RacingType.ACCELERATION, selected, true);
    var plan = importer.plan(edit.build()); assertTrue(plan.safe(), plan.unsafeChanges().toString()); assertEquals(767, plan.inserts());
    importer.apply(edit.build(), plan.approvalToken());
    var imported = runtime.findByVersion(next.version()).orElseThrow();
    assertEquals("test-passive-next", imported.passiveRuleset()); assertEquals(oldRules.passive(), imported.passive());
    assertEquals(oldRules.scenario(), imported.scenario()); assertEquals(oldRules.scenarioUtilities(), imported.scenarioUtilities());
    assertEquals(oldRules, runtime.findByVersion("1.4.1").orElseThrow());
    assertEquals(before, PassiveStatsCalculator.calculate(runtime.findByVersion("1.4.1").orElseThrow(), base,
        "1.4.1", RacingType.ACCELERATION, RacingType.ACCELERATION, selected, true));
    var unavailable = PassiveStatsCalculator.calculate(imported, base, next.version(), RacingType.ACCELERATION, RacingType.ACCELERATION, selected, true);
    assertEquals(PassiveStatsResult.Coverage.UNSUPPORTED_VERSION, unavailable.coverage());
    assertEquals(ScenarioStatsResult.Coverage.UNAVAILABLE, ScenarioStatsCalculator.calculate(imported, unavailable, ScenarioContext.UNSPECIFIED).coverage());
    var noOp = importer.plan(edit.build()); assertEquals(0, noOp.inserts()); importer.apply(edit.build(), noOp.approvalToken());
  }

  @Test void lateRuleSourceForeignKeyFailureRollsBackCatalogVersionAndEveryRuleRow() {
    var additions = new Edit(empty()); additions.racers.addAll(sample().racers());
    additions.versions.add(row(new GameVersion(id(951), "test-rule-rollback", LocalDate.of(2026, 12, 2))));
    var original = canonical.ruleSets().getFirst(); var sources = new ArrayList<>(original.sources());
    sources.add(row(new Source(EffectType.SCENARIO, PassiveGadgetRules.id(45), "missing-effect", 0, "https://example.test/source")));
    additions.ruleSets.add(new GameDataRuleSet(row(new Metadata(id(951), "rollback-passive", "rollback-scenario")), original.passive(), original.scenario(), sources));
    var invalid = new GameDataImportPlan(additions.build(), empty(), List.of(), List.of(), "test");
    assertTrue(assertThrows(IllegalStateException.class, () -> repository.apply(current -> invalid)).getMessage().contains("23503"));
    assertTrue(new GadgetRuleDbAdapter(isolated).findByVersion("test-rule-rollback").isEmpty());
    assertFalse(repository.read().racers().stream().anyMatch(r -> r.value().id().equals(id(1))));
    assertTrue(importer.plan(canonical).safe()); assertEquals(0, importer.plan(canonical).inserts());
  }

  @Test void realRuleUniqueConstraintRollsBackTheEntireNewSnapshot() {
    var additions = new Edit(empty());
    additions.versions.add(row(new GameVersion(id(952), "test-rule-unique", LocalDate.of(2026, 12, 3))));
    var original = canonical.ruleSets().getFirst(); var sources = new ArrayList<>(original.sources()); sources.add(sources.getFirst());
    additions.ruleSets.add(new GameDataRuleSet(row(new Metadata(id(952), "unique-passive", "unique-scenario")), original.passive(), original.scenario(), sources));
    var invalid = new GameDataImportPlan(additions.build(), empty(), List.of(), List.of(), "test");
    assertTrue(assertThrows(IllegalStateException.class, () -> repository.apply(current -> invalid)).getMessage().contains("23505"));
    assertTrue(new GadgetRuleDbAdapter(isolated).findByVersion("test-rule-unique").isEmpty());
    assertTrue(importer.plan(canonical).changes().isEmpty());
  }

  @Test void insertsAllCatalogKindsAndCompleteVersionThenReimportsAsNoOp() {
    var edit = new Edit(canonical); var newData = sample();
    edit.racers.addAll(newData.racers()); edit.machines.addAll(newData.machines());
    edit.parts.addAll(newData.parts()); edit.gadgets.addAll(newData.gadgets());
    edit.maps.add(row(new RaceMap(id(7), "Test map", RaceMap.Category.CROSSWORLD, "Test pack", null, 1000)));
    edit.versions.addAll(newData.versions());
    var racerStats = new ArrayList<>(latestStats().racers());
    var partStats = new ArrayList<>(completeParts(latestStats().parts()));
    racerStats.addAll(newData.snapshots().getFirst().racers());
    partStats.addAll(newData.snapshots().getFirst().parts());
    edit.snapshots.add(new VersionStats(id(8), racerStats, partStats));
    var plan = importer.plan(edit.build()); assertTrue(plan.safe(), plan.unsafeChanges().toString());
    assertEquals(242, plan.inserts());
    importer.apply(edit.build(), plan.approvalToken());
    assertEquals(54, repository.read().racers().size());
    assertEquals(180, repository.read().parts().size());
    var noOp = importer.plan(edit.build()); assertEquals(0, noOp.inserts()); assertEquals(0, noOp.updateCount());
    importer.apply(edit.build(), noOp.approvalToken());
    assertTrue(importer.plan(edit.build()).changes().isEmpty());
  }

  @Test void updatesPresentationFieldsWithoutChangingMechanics() {
    var edit = new Edit(canonical);
    var r = edit.racers.getFirst().value(); var m = edit.machines.getFirst().value();
    var g = edit.gadgets.getFirst().value(); var map = edit.maps.getFirst().value();
    edit.racers.set(0, row(new Racer(r.id(), "Changed racer", r.racingType(), "/assets/test.png")));
    edit.machines.set(0, row(new Machine(m.id(), "Changed machine", m.racingType(), "/assets/test.png")));
    edit.gadgets.set(0, row(new Gadget(g.id(), "Changed gadget", "Description with ' and ,", g.slotCost(), "/assets/test.png")));
    edit.maps.set(0, row(new RaceMap(map.id(), "Changed map", map.category(), "Changed pack", "/assets/test.png", map.catalogOrder())));
    var plan = importer.plan(edit.build()); assertEquals(4, plan.updateCount());
    importer.apply(edit.build(), plan.approvalToken());
    assertEquals(0, importer.plan(edit.build()).updateCount());
  }

  @Test void databaseConstraintFailureRollsBackEarlierSuccessfulInsert() {
    var additions = new Edit(empty()); additions.racers.addAll(sample().racers());
    additions.parts.add(row(new MachinePart(id(30), id(999), MachinePartType.FRONT)));
    var invalid = new GameDataImportPlan(additions.build(), empty(), List.of(), List.of(), "test");
    var error = assertThrows(IllegalStateException.class, () -> repository.apply(current -> invalid));
    assertTrue(error.getMessage().contains("23503"));
    assertEquals(0, importer.plan(canonical).inserts());
    assertTrue(importer.plan(canonical).safe());
    assertFalse(repository.read().racers().stream().anyMatch(r -> r.value().id().equals(id(1))));
  }

  @Test void uniqueConstraintsAndPreparationFailureLeaveDatabaseUntouched() {
    var additions = new Edit(empty()); additions.racers.addAll(sample().racers());
    var original = canonical.gadgets().getFirst().value();
    additions.gadgets.add(row(new Gadget(id(60), original.name(), null, 1, null)));
    var invalid = new GameDataImportPlan(additions.build(), empty(), List.of(), List.of(), "test");
    assertTrue(assertThrows(IllegalStateException.class, () -> repository.apply(current -> invalid)).getMessage().contains("23505"));
    var changed = new Edit(canonical); changed.gadgets.addAll(sample().gadgets());
    assertThrows(ImportValidationException.class, () -> importer.apply(changed.build(), "not-the-reviewed-plan"));
    assertTrue(importer.plan(canonical).safe()); assertTrue(importer.plan(canonical).changes().isEmpty());
    var unsafe = new GameDataImportPlan(empty(), empty(), List.of(), List.of("unsafe"), "test");
    assertThrows(IllegalArgumentException.class, () -> repository.apply(current -> unsafe));
  }

  @Test void aNewVersionPreservesOldBuildReferencesAndCalculatedStats() throws Exception {
    UUID buildId = id(500), author = id(501);
    UUID racer = UUID.fromString("013ecae2-55b9-58bb-b1c5-b054578058ff");
    UUID version = UUID.fromString("50000000-0000-0000-0000-000000000001");
    try (var c = isolated.getConnection(); var s = c.createStatement()) {
      s.executeUpdate("INSERT INTO users(id,username,email,password_hash,created_at) VALUES ('" + author + "','import-test','import@example.test','unused',now())");
      s.executeUpdate("INSERT INTO builds(id,title,description,author_id,racer_id,front_part_id,rear_part_id,tire_part_id,game_version_id,created_at,updated_at) VALUES ('"
          + buildId + "','Historical build','Preserve me','" + author + "','" + racer
          + "','10000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000001','" + version + "',now(),now())");
    }
    var before = oldBuildStats(buildId, version);
    var edit = new Edit(canonical);
    edit.versions.add(row(new GameVersion(id(900), "test-next", LocalDate.of(2026, 12, 1))));
    var old = canonical.snapshots().stream().filter(s -> s.versionId().equals(version)).findFirst().orElseThrow();
    var changedStats = old.racers().stream().map(r -> r.value().itemId().equals(racer)
        ? row(new StatRow(racer, BaseStats.ZERO)) : r).toList();
    edit.snapshots.add(new VersionStats(id(900), changedStats, completeParts(old.parts())));
    var plan = importer.plan(edit.build()); importer.apply(edit.build(), plan.approvalToken());
    assertEquals(before, oldBuildStats(buildId, version));
    var persisted = repository.read().snapshots().stream().filter(s -> s.versionId().equals(version)).findFirst().orElseThrow();
    assertEquals(old.racers().stream().map(CatalogRow::value).toList(), persisted.racers().stream().map(CatalogRow::value).toList());
    var changed = repository.read().snapshots().stream().filter(s -> s.versionId().equals(id(900))).findFirst().orElseThrow();
    assertEquals(BigDecimal.ZERO, changed.racers().stream().filter(r -> r.value().itemId().equals(racer)).findFirst().orElseThrow().value().stats().speed());
  }

  private VersionStats latestStats() {
    return canonical.snapshots().stream().filter(s -> s.versionId().equals(
        UUID.fromString("50000000-0000-0000-0000-000000000001"))).findFirst().orElseThrow();
  }

  // A future complete snapshot must explicitly carry unknowns, not copy fabricated values.
  private List<CatalogRow<StatRow>> completeParts(List<CatalogRow<StatRow>> rows) {
    var result = new ArrayList<>(rows);
    for (var part : canonical.parts()) if (rows.stream().noneMatch(r -> r.value().itemId().equals(part.value().id())))
      result.add(row(new StatRow(part.value().id(), BaseStats.UNKNOWN)));
    return result;
  }

  private BaseStats oldBuildStats(UUID id, UUID version) {
    return QuarkusTransaction.requiringNew().call(() -> {
      em.createNativeQuery("SET LOCAL search_path TO " + schema).executeUpdate();
      var build = builds.find(id).orElseThrow();
      assertEquals("Historical build", build.title()); assertEquals(version, build.gameVersionId());
      var result = stats.build(version, build.racerId(), build.frontPartId(), build.rearPartId(), build.tirePartId());
      assertEquals(0, new BigDecimal("80").compareTo(result.speed()));
      return result;
    });
  }
}
