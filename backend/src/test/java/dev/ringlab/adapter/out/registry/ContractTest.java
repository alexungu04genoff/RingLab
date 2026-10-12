package dev.ringlab.adapter.out.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import dev.ringlab.domain.gamedata.registry.Publication;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import static dev.ringlab.adapter.out.registry.CanonicalJson.*;
import static org.junit.jupiter.api.Assertions.*;

class ContractTest {
    private static final PublicationContract CONTRACT = new PublicationContract();
    static byte[] baselineBytes() throws Exception { return Files.readAllBytes(Path.of("src/test/resources/registry/baseline-v1.json")); }
    static ObjectNode baseline() throws Exception { return (ObjectNode)parse(baselineBytes()); }
    /** Test fixture builder only. Production consumer cannot seal or publish data. */
    static Publication seal(JsonNode dataset, long revision, Publication.Publisher publisher, String summary, Instant at) {
        ObjectNode p = MAPPER.createObjectNode(); p.put("schemaVersion", "1.0.0").put("revision", revision);
        if (revision == 1) p.putNull("parentRevision"); else p.put("parentRevision", revision - 1);
        p.put("publishedAt", at.toString()).put("changeSummary", summary);
        p.set("publishedBy", tree(publisher)); p.set("dataset", CONTRACT.normalize(dataset));
        p.put("checksum", CONTRACT.checksum(p)); return CONTRACT.read(bytes(p));
    }
    @Test void javaMatchesEveryIndependentGoldenVector() throws Exception {
        for (JsonNode vector : parse(Files.readAllBytes(Path.of("src/test/resources/registry/golden-vectors.json")))) {
            assertArrayEquals(vector.get("canonical").asText().getBytes(StandardCharsets.UTF_8), bytes(vector.get("value")), vector.get("name").asText());
            assertEquals(vector.get("sha256").asText(), digest(vector.get("value")));
        }
    }
    @Test void fullBaselineDtoRoundTripAndHashesAreExact() throws Exception {
        Publication publication = CONTRACT.read(baselineBytes());
        assertArrayEquals(baselineBytes(), bytes(tree(publication)));
        assertEquals("7fb52fb52848d13f798f290ea83192dea1cbb65212d3d96f7bd885bfc1257bff", publication.checksum());
        assertEquals("cb43981dee32d70c6f0266da1e002fb4ed55e2215ac9f9a1864d4b11e13c6ab9", CONTRACT.metadata(publication).datasetFingerprint());
        assertThrows(ContractException.class, () -> CONTRACT.verifyFingerprint(publication, "0".repeat(64)));
        assertThrows(UnsupportedOperationException.class, () -> publication.dataset().racers().clear());
        assertThrows(UnsupportedOperationException.class, () -> publication.dataset().racers().getFirst().provenance().claims().clear());
    }
    @ParameterizedTest @ValueSource(strings={"extra", "missing", "fingerprint", "schema", "dataset", "date", "notObject"})
    void metadataIsAlsoAStrictClosedContract(String mutation) throws Exception {
        var m = (ObjectNode)tree(CONTRACT.metadata(CONTRACT.read(baselineBytes())));
        switch (mutation) {
            case "extra" -> m.put("unexpected", true);
            case "missing" -> m.remove("publishedBy");
            case "fingerprint" -> m.put("datasetFingerprint", "invalid");
            case "schema" -> m.put("schemaVersion", "9.0.0");
            case "dataset" -> m.putObject("dataset");
            case "date" -> m.put("publishedAt", "2026-02-30T00:00:00Z");
            case "notObject" -> { assertThrows(ContractException.class, () -> CONTRACT.readMetadata(bytes(MAPPER.createArrayNode()))); return; }
        }
        assertThrows(ContractException.class, () -> CONTRACT.readMetadata(bytes(m)));
    }
    @Test void schemasAndBaselineRemainPinnedToTheFrozenRegistry() throws Exception {
        assertEquals("f89eb9fc7ec8b26c199e2f30781b2ee90d9e38bbd2dda2289ce44624681f9f8c", hash(Files.readAllBytes(Path.of("src/main/resources/registry/v1/publication.schema.json"))));
        assertEquals("886fd9c13c768c9427f1adf8a0dcdb78b189dcd7d36dfc0e967139783b739cb5", hash(baselineBytes()));
    }
    @ParameterizedTest @ValueSource(strings={"1.0", "1e0", "NaN", "Infinity", "-Infinity", "9007199254740992", "-0", "{} {}", "{\"x\":1,\"x\":2}", "{\"a\":1,\"\\u0061\":2}", "{\"nested\":{\"a\":1,\"a\":2}}", "\"\\ud800\"", "{\"\\udfff\":0}", "\uFEFF{}"})
    void strictParserRejectsAmbiguousTokens(String text) { assertThrows(ContractException.class, () -> parse(text.getBytes(StandardCharsets.UTF_8))); }
    @Test void strictUtf8RejectsReplacementDecoding() { assertThrows(ContractException.class, () -> parse(new byte[]{'"', (byte)0xff, '"'})); }
    @Test void unicodeAndExactDecimalPrecisionRemainUntouched() {
        assertNotEquals(digest(tree("é")), digest(tree("e\u0301")));
        assertEquals("12345678901234567890.123456789012345678901", parse("\"12345678901234567890.123456789012345678901\"".getBytes(StandardCharsets.UTF_8)).asText());
    }
    @ParameterizedTest @ValueSource(strings={"\u00a0", "\u0085", "\u001f", "\u2007"})
    void whitespaceOnlyTextMatchesPythonSchemaSemantics(String blank) throws Exception {
        ObjectNode p = baseline(); p.put("changeSummary", blank).put("checksum", "0".repeat(64));
        assertThrows(ContractException.class, () -> CONTRACT.validate(p, false));
    }
    @ParameterizedTest @ValueSource(strings={"extra", "missing", "enum", "parent", "checksum", "date", "timestamp", "reference", "gap", "duplicate", "order", "decimal", "source", "position", "provenance", "placeholder", "scenario"})
    void rejectsContractViolations(String mutation) throws Exception {
        ObjectNode p = baseline(); JsonNode d = p.get("dataset");
        switch (mutation) {
            case "extra" -> ((ObjectNode)d.get("racers").get(0)).put("unexpected", true);
            case "missing" -> ((ObjectNode)d.get("racers").get(0)).remove("imagePath");
            case "enum" -> ((ObjectNode)d.get("racers").get(0)).put("racingType", "FAST");
            case "parent" -> p.put("parentRevision", 1);
            case "checksum" -> p.put("checksum", "0".repeat(64));
            case "date" -> ((ObjectNode)d.get("gameVersions").get(0)).put("releasedAt", "2025-02-30");
            case "timestamp" -> p.put("publishedAt", "2026-10-12T23:59:60Z");
            case "reference" -> ((ObjectNode)d.get("machineParts").get(0)).put("sourceMachineId", "00000000-0000-0000-0000-000000000000");
            case "gap" -> ((ArrayNode)d.get("statSnapshots").get(0).get("racers")).remove(0);
            case "duplicate" -> ((ArrayNode)d.get("racers")).add(d.get("racers").get(0));
            case "order" -> { ArrayNode a = (ArrayNode)d.get("racers"); JsonNode first = a.remove(0); a.add(first); }
            case "decimal" -> ((ObjectNode)d.get("statSnapshots").get(0).get("racers").get(0).get("stats")).put("speed", "1.00");
            case "source" -> ((ObjectNode)d.get("ruleSets").get(0).get("ruleSources").get(0)).put("url", "https://user:password@example.org/source");
            case "position" -> ((ObjectNode)d.get("ruleSets").get(0).get("ruleSources").get(0)).put("position", 999);
            case "provenance" -> { ObjectNode claim = MAPPER.createObjectNode(); claim.putArray("fields").add("unexpected"); claim.put("status", "UNKNOWN").put("note", "Invalid mapping test"); claim.putArray("evidence"); ((ArrayNode)d.get("racers").get(0).get("provenance").get("claims")).add(claim); }
            case "placeholder" -> { for (JsonNode rule : d.get("ruleSets").get(0).get("passiveRules")) if (!rule.get("kind").asText().equals("PASSIVE")) { ((ObjectNode)rule.get("matching")).put("speed", "1"); break; } }
            case "scenario" -> ((ObjectNode)d.get("ruleSets").get(0).get("scenarioRules").get(0)).put("effectId", "missing-effect");
        }
        if (!mutation.equals("checksum")) p.put("checksum", CONTRACT.checksum(p));
        assertThrows(ContractException.class, () -> CONTRACT.validate(p, true), mutation);
    }
    @Test void missingUnknownAndZeroAreThreeDifferentFacts() throws Exception {
        JsonNode dataset = baseline().get("dataset");
        var row = (ObjectNode)dataset.get("statSnapshots").get(0).get("racers").get(0);
        String known = CONTRACT.fingerprint(dataset);
        ((ObjectNode)row.get("stats")).putNull("speed"); String unknown = CONTRACT.fingerprint(dataset);
        ((ObjectNode)row.get("stats")).put("speed", "0"); String zero = CONTRACT.fingerprint(dataset);
        assertNotEquals(known, unknown); assertNotEquals(unknown, zero);
        assertTrue(dataset.get("statSnapshots").get(0).get("missingMachineParts").size() > 0);
        var snapshot = (ObjectNode)dataset.get("statSnapshots").get(0);
        var gap = ((ArrayNode)snapshot.get("missingMachineParts")).remove(0);
        ObjectNode unknownRow = MAPPER.createObjectNode(); unknownRow.set("machinePartId", gap.get("machinePartId")); unknownRow.set("provenance", gap.get("provenance"));
        ObjectNode stats = unknownRow.putObject("stats"); for (String field : java.util.List.of("speed", "acceleration", "handling", "power", "boost")) stats.putNull(field);
        // Gap provenance has a different factual field mapping; use an explicit unknown claim for this synthetic row.
        ObjectNode provenance = unknownRow.putObject("provenance"); provenance.put("status", "UNKNOWN").put("mapping", "INCOMPLETE").put("note", "Synthetic unknown row"); provenance.putArray("claims");
        ((ArrayNode)snapshot.get("machineParts")).add(unknownRow);
        Publication p = ContractTest.seal(dataset, 1, new Publication.Publisher("00000000-0000-0000-0000-000000000001", "Test"), "Explicit unknown row", Instant.parse("2026-10-12T01:00:00Z"));
        assertNotEquals(zero, CONTRACT.metadata(p).datasetFingerprint());
        var preserved = p.dataset().statSnapshots().getFirst().machineParts().stream().filter(r -> r.machinePartId().equals(gap.get("machinePartId").asText())).findFirst().orElseThrow();
        assertNull(preserved.stats().speed());
    }
    @Test void sealingNormalizesIdentityCollectionsButPreservesSourcePositions() throws Exception {
        JsonNode d = baseline().get("dataset"); ArrayNode racers = (ArrayNode)d.get("racers"); racers.add(racers.remove(0));
        Publication p = ContractTest.seal(d, 2, new Publication.Publisher("00000000-0000-0000-0000-000000000001", "Test"), "Metadata only", Instant.parse("2026-10-12T01:00:00Z"));
        assertEquals("cb43981dee32d70c6f0266da1e002fb4ed55e2215ac9f9a1864d4b11e13c6ab9", CONTRACT.metadata(p).datasetFingerprint());
        assertNotEquals(baseline().get("checksum").asText(), p.checksum());
    }
}
