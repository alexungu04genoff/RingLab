package dev.ringlab.application.gamedata;

import com.fasterxml.jackson.databind.node.*;
import dev.ringlab.adapter.in.catalog.*;
import dev.ringlab.adapter.out.registry.*;
import dev.ringlab.domain.gamedata.registry.*;
import dev.ringlab.domain.gamedata.importing.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static dev.ringlab.adapter.out.registry.CanonicalJson.*;
import static org.junit.jupiter.api.Assertions.*;

class RegistryComparisonServiceTest {
  private final PublicationContract contract = new PublicationContract();
  private Publication baseline;
  private GameDataSet canonical;
  @TempDir Path temporary;
  @BeforeEach void fixture() throws Exception {
    baseline = contract.read(Files.readAllBytes(Path.of("src/test/resources/registry/baseline-v1.json")));
    canonical = new GameDataCsvReader().read(Path.of("../game-data"));
  }
  private RegistryComparison compare(Publication p) {
    return new RegistryComparisonService(() -> Optional.of(new VerifiedRegistryPublication(p, contract.metadata(p).datasetFingerprint())), () -> canonical)
        .compareLatest().orElseThrow();
  }
  private Publication changed(Consumer<ObjectNode> edit) {
    ObjectNode p = (ObjectNode)tree(baseline); edit.accept((ObjectNode)p.get("dataset"));
    p.set("dataset", contract.normalize(p.get("dataset"))); p.put("checksum", contract.checksum(p));
    return contract.read(bytes(p));
  }
  @Test void frozenBaselineProjectsExactlyToCurrentCanonicalFilesWithoutLosingItsDistinctions() {
    var result = compare(baseline);
    assertTrue(result.projectionCompatible(), result.incompatibleMappings().toString());
    assertTrue(result.changes().isEmpty()); assertTrue(result.protectedChanges().isEmpty());
    assertEquals(0, result.proposedAdditions()); assertEquals(0, result.proposedPresentationUpdates());
    assertEquals(result.databaseFingerprint(), result.projectionFingerprint());
    assertNotEquals(result.projectionFingerprint(), result.registry().datasetFingerprint());
    assertTrue(result.assessment().startsWith("PROJECTION_COMPATIBLE_REQUIRES_RETENTION_DESIGN"));
    assertEquals(15, result.observations().stream().filter(o -> o.kind().equals("MISSING_STAT_ROW")).count());
    assertTrue(result.observations().stream().anyMatch(o -> o.detail().contains("HISTORICAL_PLACEHOLDER")));
    assertArrayEquals(bytes(tree(baseline)), bytes(tree(result.registry().publication())));
    var projection = new RegistryProjection().project(baseline).data();
    assertEquals(CatalogFingerprint.snapshot(canonical), CatalogFingerprint.snapshot(projection));
    assertEquals(canonical.ruleSets().getFirst().snapshot(canonical.versions().stream()
        .filter(v -> v.value().id().equals(canonical.ruleSets().getFirst().versionId())).findFirst().orElseThrow().value()),
        projection.ruleSets().getFirst().snapshot(projection.versions().stream()
        .filter(v -> v.value().id().equals(projection.ruleSets().getFirst().versionId())).findFirst().orElseThrow().value()));
  }
  @Test void reportsPresentationAndAdditionsSeparatelyFromProtectedChanges() {
    var p = changed(d -> {
      ((ObjectNode)d.get("racers").get(0)).put("name", "Presentation change");
      var map = d.get("maps").get(0).deepCopy(); ((ObjectNode)map).put("id", "ffffffff-ffff-ffff-ffff-ffffffffffff").put("name", "New map").put("catalogOrder", 999);
      ((ArrayNode)d.get("maps")).add(map);
      ((ObjectNode)d.get("gadgets").get(0)).put("slotCost", 1);
      ((ObjectNode)d.get("statSnapshots").get(0).get("racers").get(0).get("stats")).put("speed", "123.12345678901234567890123456789");
    });
    var result = compare(p); assertFalse(result.projectionCompatible());
    assertEquals(1, result.proposedAdditions()); assertEquals(1, result.proposedPresentationUpdates());
    assertTrue(result.changes().stream().anyMatch(s -> s.contains("New map")));
    assertTrue(result.protectedChanges().stream().anyMatch(s -> s.contains("slot_cost")));
    assertTrue(result.protectedChanges().stream().anyMatch(s -> s.contains("123.12345678901234567890123456789")));
    var projection = new RegistryProjection().project(p).data();
    assertEquals(new BigDecimal("123.12345678901234567890123456789"), projection.snapshots().getFirst().racers().getFirst().value().stats().speed());
  }
  @Test void provenanceOnlyChangeIsVisibleEvenWhenPostgresProjectionMatches() {
    var p = changed(d -> ((ObjectNode)d.get("racers").get(0).get("provenance")).put("note", "Additional unresolved evidence"));
    var result = compare(p); assertTrue(result.changes().isEmpty());
    assertEquals(result.databaseFingerprint(), result.projectionFingerprint());
    assertNotEquals(contract.metadata(baseline).datasetFingerprint(), result.registry().datasetFingerprint());
    assertEquals("Additional unresolved evidence", result.registry().publication().dataset().racers().getFirst().provenance().note());
    assertTrue(result.observations().stream().anyMatch(o -> o.kind().equals("PERSISTENCE_GAP")));
  }
  @Test void newSnapshotWithExplicitMissingRowsIsNotConvertedToUnknownRows() {
    var p = changed(d -> {
      var version = (ObjectNode)d.get("gameVersions").get(0).deepCopy();
      version.put("id", "ffffffff-ffff-ffff-ffff-ffffffffffff").put("version", "test-next");
      ((ArrayNode)d.get("gameVersions")).add(version);
      var snapshot = (ObjectNode)d.get("statSnapshots").get(0).deepCopy(); snapshot.put("gameVersionId", version.get("id").asText());
      ((ArrayNode)d.get("statSnapshots")).add(snapshot);
    });
    var result = compare(p); assertFalse(result.projectionCompatible());
    assertTrue(result.protectedChanges().stream().anyMatch(s -> s.contains("New snapshot requires a row")));
    assertTrue(result.observations().stream().filter(o -> o.kind().equals("MISSING_STAT_ROW")).count() > 15);
  }
  @Test void registryLegalButRingLabIncompatibleRulesAndTextRemainIntactAndBlocked() {
    var p = changed(d -> {
      ((ObjectNode)d.get("racers").get(0)).put("name", "X".repeat(101));
      ((ObjectNode)d.get("gadgets").get(0)).put("description", "contains\u0000nul");
      for (var f : d.get("ruleSets").get(0).get("passiveRules")) if (f.get("kind").asText().equals("PASSIVE")) {
        ((ObjectNode)f).put("stackingGroup", "future-group"); break;
      }
    });
    var result = compare(p); assertFalse(result.projectionCompatible());
    assertTrue(result.incompatibleMappings().stream().anyMatch(s -> s.contains("PostgreSQL constraints")));
    assertTrue(result.incompatibleMappings().stream().anyMatch(s -> s.contains("historical tuner group")));
    assertEquals(101, result.registry().publication().dataset().racers().getFirst().name().length());
  }
  @Test void emptyAndFailedRegistryNeverReadTheDatabase() {
    var reads = new AtomicInteger();
    assertTrue(new RegistryComparisonService(Optional::empty, () -> { reads.incrementAndGet(); return canonical; }).compareLatest().isEmpty());
    assertThrows(RegistryException.class, () -> new RegistryComparisonService(() -> {
      throw new RegistryException(RegistryException.Code.UNAVAILABLE, "offline");
    }, () -> { reads.incrementAndGet(); return canonical; }).compareLatest());
    assertEquals(0, reads.get());
  }
  @Test void nullZeroAndMissingRemainDistinctWhileOversizedDecimalsAreReportedWithoutRounding() {
    String exact = "0." + "0".repeat(16383) + "1";
    var p = changed(d -> {
      var stats = (ObjectNode)d.get("statSnapshots").get(0).get("racers").get(0).get("stats");
      stats.putNull("speed").put("power", "0").put("boost", exact);
    });
    var projection = new RegistryProjection().project(p); var stats = projection.data().snapshots().getFirst().racers().getFirst().value().stats();
    assertNull(stats.speed()); assertEquals(BigDecimal.ZERO, stats.power()); assertEquals(new BigDecimal(exact), stats.boost());
    assertTrue(projection.incompatible().stream().anyMatch(s -> s.contains("NUMERIC limits")));
    assertEquals(15, projection.observations().stream().filter(o -> o.kind().equals("MISSING_STAT_ROW")).count());
  }
  @Test void sourceAndScenarioOrderingChangesAreProtectedFacts() {
    var result = compare(changed(d -> {
      var rules = d.get("ruleSets").get(0);
      var first = (ObjectNode)rules.get("scenarioRules").get(0);
      var second = (ObjectNode)rules.get("scenarioRules").get(1);
      first.put("position", 1); second.put("position", 0);
      ((ObjectNode)rules.get("ruleSources").get(0)).put("url", "https://example.test/different-evidence");
    }));
    assertFalse(result.projectionCompatible());
    assertTrue(result.protectedChanges().stream().anyMatch(s -> s.contains("field=position")));
    assertTrue(result.protectedChanges().stream().anyMatch(s -> s.contains("field=url")));
  }
  @Test void commandWritesLosslessReportWithoutApprovalTokenAndNeverOverwritesIt() throws Exception {
    var report = temporary.resolve("comparison.json"); var result = compare(baseline);
    var output = new ByteArrayOutputStream(); var errors = new ByteArrayOutputStream();
    var command = new RegistryComparisonCommand(() -> Optional.of(result));
    assertEquals(0, command.run(new String[]{"compare", report.toString()}, new PrintStream(output), new PrintStream(errors)));
    var json = parse(Files.readAllBytes(report));
    assertEquals(0, json.get("databaseWrites").asInt()); assertFalse(json.toString().contains("approvalToken"));
    assertEquals(digest(tree(baseline)), digest(json.get("comparison").get("registry").get("publication")));
    assertTrue(output.toString().contains("Database writes: 0"));
    assertEquals(2, command.run(new String[]{"compare", report.toString()}, new PrintStream(output), new PrintStream(errors)));
    assertEquals(2, command.run(new String[]{"apply", report.toString()}, new PrintStream(output), new PrintStream(errors)));
    assertEquals(1, command.run(new String[]{"compare", temporary.resolve("missing/report.json").toString()}, new PrintStream(output), new PrintStream(errors)));
  }
  @Test void commandReportsEmptyUnsafeAndFailureWithoutPretendingActivationIsSafe() {
    var out = new PrintStream(new ByteArrayOutputStream()); var path = temporary.resolve("empty.json");
    assertEquals(0, new RegistryComparisonCommand(Optional::empty).run(new String[]{"compare",path.toString()}, out,out));
    assertFalse(Files.exists(path));
    var unsafe = compare(changed(d -> ((ObjectNode)d.get("gadgets").get(0)).put("slotCost", 1)));
    assertEquals(2, new RegistryComparisonCommand(() -> Optional.of(unsafe)).run(new String[]{"compare", path.toString()}, out,out));
    assertEquals(1, new RegistryComparisonCommand(() -> { throw new RegistryException(RegistryException.Code.TIMEOUT,"Timed out"); })
        .run(new String[]{"compare", temporary.resolve("fail.json").toString()}, out,out));
    assertEquals(1, new RegistryComparisonCommand(() -> { throw new IllegalStateException("DB error"); })
        .run(new String[]{"compare", temporary.resolve("fail.json").toString()}, out,out));
  }
}
