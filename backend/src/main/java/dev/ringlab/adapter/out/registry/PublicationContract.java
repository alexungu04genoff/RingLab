package dev.ringlab.adapter.out.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.networknt.schema.*;
import dev.ringlab.domain.gamedata.registry.Publication;
import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import static dev.ringlab.adapter.out.registry.CanonicalJson.*;


public class PublicationContract {
    private final JsonSchema schema;
    private static final List<String> CATALOGS = List.of("racers", "machines", "machineParts", "gadgets", "maps", "gameVersions");

    public PublicationContract() {
        var config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).regularExpressionFactory(expression -> {
            // Match the authoritative Python validator's Unicode whitespace semantics.
            String pattern = expression.replace("\\s", "[\\p{javaWhitespace}\\p{Z}\\x{85}]")
                    .replace("\\S", "[^\\p{javaWhitespace}\\p{Z}\\x{85}]");
            var compiled = java.util.regex.Pattern.compile(pattern);
            return value -> compiled.matcher(value).find();
        }).build();
        schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(getClass().getResourceAsStream("/registry/v1/publication.schema.json"), config);
    }

    public Publication read(byte[] bytes) { return validate(parse(bytes), true); }

    /** Validate the closed metadata envelope with the same schema and semantics as a publication. */
    public Publication.Metadata readMetadata(byte[] bytes) {
        JsonNode node = parse(bytes);
        require(node.isObject() && node.has("datasetFingerprint") && node.get("datasetFingerprint").isTextual()
                && node.get("datasetFingerprint").asText().matches("[0-9a-f]{64}") && !node.has("dataset"),
                "/", "INVALID_METADATA", "Expected publication metadata and a SHA-256 dataset fingerprint.");
        ObjectNode envelope = node.deepCopy();
        envelope.remove("datasetFingerprint");
        ObjectNode empty = envelope.putObject("dataset");
        for (String catalog : CATALOGS) empty.putArray(catalog);
        empty.putArray("statSnapshots"); empty.putArray("ruleSets");
        validate(envelope, false);
        return MAPPER.convertValue(node, Publication.Metadata.class);
    }

    public Publication validate(JsonNode publication, boolean checksum) {
        bytes(publication);
        var errors = schema.validate(publication);
        if (!errors.isEmpty()) throw new ContractException(errors.stream()
                .sorted(Comparator.comparing(e -> e.getInstanceLocation().toString()))
                .limit(100).map(e -> new ContractException.Issue(e.getInstanceLocation().toString(), "SCHEMA_" + e.getType().toUpperCase(Locale.ROOT), e.getMessage())).toList());
        long revision = publication.get("revision").longValue();
        require(revision == 1 ? publication.get("parentRevision").isNull() : publication.get("parentRevision").asLong() == revision - 1,
                "/parentRevision", "INVALID_PARENT", "Parent must be the preceding revision.");
        try {
            String timestamp = publication.get("publishedAt").asText();
            require(!timestamp.startsWith("0000") && !timestamp.substring(17, 19).equals("60"), "/publishedAt", "INVALID_DATE", "Invalid UTC timestamp.");
            LocalDateTime.parse(timestamp.substring(0, 19));
        } catch (java.time.DateTimeException e) { throw new ContractException("/publishedAt", "INVALID_DATE", "Invalid UTC timestamp."); }
        JsonNode data = publication.get("dataset");
        semantics(data);
        require(data.equals(normalize(data)), "/dataset", "NON_CANONICAL_ORDER", "Identity collections must be normalized.");
        if (checksum) require(publication.get("checksum").asText().equals(checksum(publication)), "/checksum", "CHECKSUM_MISMATCH", "Publication checksum mismatch.");
        return MAPPER.convertValue(publication, Publication.class);
    }

    public String fingerprint(JsonNode dataset) { return digest(normalize(dataset)); }
    public String checksum(JsonNode publication) {
        ObjectNode preimage = publication.deepCopy(); preimage.remove("checksum"); return digest(preimage);
    }
    public Publication.Metadata metadata(Publication p) {
        return new Publication.Metadata(p.schemaVersion(), p.revision(), p.parentRevision(), p.publishedAt(),
                p.publishedBy(), p.changeSummary(), p.checksum(), fingerprint(tree(p.dataset())));
    }
    public void verifyFingerprint(Publication p, String expected) {
        require(fingerprint(tree(p.dataset())).equals(expected), "/datasetFingerprint", "FINGERPRINT_MISMATCH", "Dataset fingerprint mismatch.");
    }

    public JsonNode normalize(JsonNode original) {
        JsonNode data = original.deepCopy();
        for (String name : CATALOGS) sort(data.get(name), by("id"));
        sort(data.get("statSnapshots"), by("gameVersionId"));
        for (JsonNode snapshot : data.get("statSnapshots")) {
            sort(snapshot.get("racers"), by("racerId")); sort(snapshot.get("missingRacers"), by("racerId"));
            sort(snapshot.get("machineParts"), by("machinePartId")); sort(snapshot.get("missingMachineParts"), by("machinePartId"));
        }
        sort(data.get("ruleSets"), by("gameVersionId"));
        for (JsonNode rules : data.get("ruleSets")) {
            sort(rules.get("passiveRules"), by("gadgetId").thenComparingInt(r -> r.get("position").intValue()));
            sort(rules.get("scenarioRules"), Comparator.comparingInt(r -> r.get("position").intValue()));
            sort(rules.get("ruleSources"), by("effectType").thenComparing(by("gadgetId")).thenComparing(by("effectId")).thenComparingInt(r -> r.get("position").intValue()));
        }
        return data;
    }
    private static Comparator<JsonNode> by(String key) { return Comparator.comparing(n -> n.get(key).asText()); }
    private static void sort(JsonNode array, Comparator<JsonNode> order) {
        List<JsonNode> rows = new ArrayList<>(); array.forEach(rows::add); rows.sort(order); ((ArrayNode)array).removeAll().addAll(rows);
    }
    private static Set<String> unique(JsonNode rows, Function<JsonNode, String> key, String path) {
        Set<String> values = new HashSet<>(); int i = 0;
        for (JsonNode row : rows) { require(values.add(key.apply(row)), path + "/" + i, "DUPLICATE_IDENTITY", "Duplicate identity or name."); i++; }
        return values;
    }
    private static String field(JsonNode n, String key) { return n.get(key).asText(); }
    private static String effect(JsonNode n) { return field(n, "gadgetId") + ":" + field(n, "effectId"); }
    private static String source(JsonNode n) { return field(n, "effectType") + ":" + effect(n); }
    private void semantics(JsonNode data) {
        Map<String, Set<String>> ids = new HashMap<>();
        for (String name : CATALOGS) {
            ids.put(name, unique(data.get(name), n -> field(n, "id"), "/dataset/" + name));
            if (!Set.of("machineParts", "gameVersions").contains(name)) unique(data.get(name), n -> field(n, "name"), "/dataset/" + name);
        }
        unique(data.get("maps"), n -> field(n, "catalogOrder"), "/dataset/maps");
        unique(data.get("gameVersions"), n -> field(n, "version"), "/dataset/gameVersions");
        for (JsonNode version : data.get("gameVersions")) {
            try { require(LocalDate.parse(field(version, "releasedAt")).getYear() > 0, "/dataset/gameVersions", "INVALID_DATE", "Invalid release date."); }
            catch (DateTimeException e) { throw new ContractException("/dataset/gameVersions", "INVALID_DATE", "Invalid release date."); }
        }
        unique(data.get("machineParts"), n -> field(n, "sourceMachineId") + ":" + field(n, "partType"), "/dataset/machineParts");
        Map<String, Set<String>> parts = new HashMap<>();
        for (JsonNode part : data.get("machineParts")) {
            require(ids.get("machines").contains(field(part, "sourceMachineId")), "/dataset/machineParts", "UNKNOWN_REFERENCE", "Unknown source machine.");
            parts.computeIfAbsent(field(part, "sourceMachineId"), k -> new HashSet<>()).add(field(part, "partType"));
        }
        for (JsonNode machine : data.get("machines")) {
            Set<String> expected = field(machine, "racingType").equals("BOOST") ? Set.of("FRONT", "REAR") : Set.of("FRONT", "REAR", "TIRE");
            require(expected.equals(parts.get(field(machine, "id"))), "/dataset/machines", "MACHINE_COMPOSITION", "Machine parts do not match its racing type.");
        }
        for (JsonNode gadget : data.get("gadgets")) {
            String kind = field(gadget, "acquisitionKind"); boolean label = !gadget.get("acquisitionLabel").isNull();
            require(!kind.equals("UNKNOWN") || !label, "/dataset/gadgets", "ACQUISITION_LABEL", "Unknown acquisition cannot assert a label.");
            require(!kind.equals("FESTIVAL_REWARD") || label, "/dataset/gadgets", "ACQUISITION_LABEL", "Festival acquisition requires a label.");
        }
        Set<String> snapshots = unique(data.get("statSnapshots"), n -> field(n, "gameVersionId"), "/dataset/statSnapshots");
        require(snapshots.equals(ids.get("gameVersions")), "/dataset/statSnapshots", "SNAPSHOT_PARTITION", "Each game version needs one stat snapshot.");
        int si = 0;
        for (JsonNode snapshot : data.get("statSnapshots")) {
            String path = "/dataset/statSnapshots/" + si++;
            for (String category : List.of("racers", "machineParts")) {
                String key = category.equals("racers") ? "racerId" : "machinePartId";
                String gap = category.equals("racers") ? "missingRacers" : "missingMachineParts";
                Set<String> present = unique(snapshot.get(category), n -> field(n, key), path + "/" + category);
                Set<String> missing = unique(snapshot.get(gap), n -> field(n, key), path + "/" + gap);
                require(Collections.disjoint(present, missing), path, "SNAPSHOT_PARTITION", "A stat row cannot also be missing.");
                present.addAll(missing);
                require(present.equals(ids.get(category)), path, "SNAPSHOT_PARTITION", "Stats and gaps must partition catalog identities.");
            }
        }
        Set<String> versions = unique(data.get("ruleSets"), n -> field(n, "gameVersionId"), "/dataset/ruleSets");
        require(ids.get("gameVersions").containsAll(versions), "/dataset/ruleSets", "UNKNOWN_REFERENCE", "Unknown rule version.");
        int ri = 0;
        for (JsonNode rules : data.get("ruleSets")) validateRules(rules, ids.get("gadgets"), "/dataset/ruleSets/" + ri++);
        provenance(data, "/dataset");
    }
    private void validateRules(JsonNode rules, Set<String> gadgets, String path) {
        Set<String> passiveKeys = unique(rules.get("passiveRules"), PublicationContract::effect, path + "/passiveRules");
        Set<String> scenarioKeys = unique(rules.get("scenarioRules"), PublicationContract::effect, path + "/scenarioRules");
        positions(rules.get("passiveRules"), n -> field(n, "gadgetId"), path + "/passiveRules");
        positions(rules.get("scenarioRules"), n -> "scenario", path + "/scenarioRules");
        positions(rules.get("ruleSources"), PublicationContract::source, path + "/ruleSources");
        Map<String, JsonNode> passive = new HashMap<>();
        int i = 0;
        for (JsonNode rule : rules.get("passiveRules")) {
            String p = path + "/passiveRules/" + i++; passive.put(effect(rule), rule);
            require(gadgets.contains(field(rule, "gadgetId")), p + "/gadgetId", "UNKNOWN_REFERENCE", "Unknown gadget.");
            require(field(rule, "subject").equals("ANY") == rule.get("requiredRacingType").isNull(), p, "RULE_SUBJECT", "Subject and racing type disagree.");
            require(field(rule, "kind").equals("CONDITIONAL") == !rule.get("scenarioStatPotential").isNull(), p, "RULE_POTENTIAL", "Only conditional rules specify scenario potential.");
            if (!field(rule, "kind").equals("PASSIVE")) {
                boolean zero = true;
                for (String vector : List.of("matching", "nonMatching")) for (JsonNode v : rule.get(vector)) zero &= v.asText().equals("0");
                require(field(rule, "subject").equals("ANY") && rule.get("stackingGroup").isNull() && zero, p, "RULE_PLACEHOLDER", "Non-passive storage vectors must remain zero placeholders.");
            }
        }
        for (JsonNode rule : rules.get("scenarioRules")) require(passive.containsKey(effect(rule)) && field(passive.get(effect(rule)), "kind").equals("CONDITIONAL"), path + "/scenarioRules", "SCENARIO_REFERENCE", "Scenario must reference a conditional fact.");
        Set<String> sourced = new HashSet<>(), expected = new HashSet<>();
        for (JsonNode ref : rules.get("ruleSources")) {
            require((field(ref, "effectType").equals("PASSIVE") ? passiveKeys : scenarioKeys).contains(effect(ref)), path + "/ruleSources", "UNKNOWN_REFERENCE", "Dangling rule source.");
            sourced.add(source(ref)); url(field(ref, "url"), path + "/ruleSources");
        }
        passiveKeys.forEach(k -> expected.add("PASSIVE:" + k)); scenarioKeys.forEach(k -> expected.add("SCENARIO:" + k));
        require(expected.equals(sourced), path + "/ruleSources", "MISSING_SOURCE", "Every effect requires ordered sources.");
    }
    private static void positions(JsonNode rows, Function<JsonNode, String> group, String path) {
        Map<String, List<Integer>> groups = new HashMap<>();
        for (JsonNode row : rows) groups.computeIfAbsent(group.apply(row), k -> new ArrayList<>()).add(row.get("position").intValue());
        for (List<Integer> values : groups.values()) {
            Collections.sort(values);
            for (int i = 0; i < values.size(); i++) require(values.get(i) == i, path, "INVALID_POSITION", "Positions must be contiguous from zero.");
        }
    }
    private static void url(String text, String path) {
        try {
            URI uri = URI.create(text);
            require(Set.of("http", "https").contains(uri.getScheme()) && uri.getHost() != null && uri.getRawUserInfo() == null
                    && uri.getPort() <= 65535 && uri.getPort() >= -1 && text.codePoints().noneMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c) || c == 0x85), path, "INVALID_URL", "Use an HTTP(S) URL without credentials.");
        } catch (IllegalArgumentException e) { throw new ContractException(path, "INVALID_URL", "Invalid source URL."); }
    }
    private void provenance(JsonNode node, String path) {
        if (node.isArray()) { int i = 0; for (JsonNode child : node) provenance(child, path + "/" + i++); }
        else if (node.isObject()) {
            if (node.has("provenance")) {
                JsonNode p = node.get("provenance"); Set<String> fields = new HashSet<>(); node.fieldNames().forEachRemaining(fields::add); fields.remove("provenance");
                Set<String> mapped = new HashSet<>(); boolean matching = field(p, "status").equals("UNKNOWN");
                for (JsonNode claim : p.get("claims")) {
                    Set<String> claimFields = new HashSet<>();
                    for (JsonNode f : claim.get("fields")) require(claimFields.add(f.asText()) && fields.contains(f.asText()), path + "/provenance", "PROVENANCE_FIELD", "Invalid or repeated provenance field.");
                    mapped.addAll(claimFields); matching |= field(claim, "status").equals(field(p, "status"));
                    if (Set.of("REVIEWED_SOURCE", "HISTORICAL_PLACEHOLDER", "CONFLICTING").contains(field(claim, "status"))) require(!claim.get("evidence").isEmpty(), path + "/provenance", "PROVENANCE_EVIDENCE", "Sourced claims require evidence.");
                    for (JsonNode evidence : claim.get("evidence")) url(field(evidence, "url"), path + "/provenance");
                }
                require(matching, path + "/provenance", "PROVENANCE_STATUS", "Provenance status needs a matching claim.");
                if (field(p, "mapping").equals("COMPLETE")) {
                    fields.removeAll(Set.of("id", "racerId", "machinePartId", "gadgetId", "gameVersionId", "position", "effectId"));
                    require(mapped.containsAll(fields), path + "/provenance", "PROVENANCE_COVERAGE", "Complete mapping must cover all factual fields.");
                }
            }
            node.fields().forEachRemaining(e -> provenance(e.getValue(), path + "/" + e.getKey()));
        }
    }
    private static void require(boolean condition, String path, String code, String message) {
        if (!condition) throw new ContractException(path, code, message);
    }
}
