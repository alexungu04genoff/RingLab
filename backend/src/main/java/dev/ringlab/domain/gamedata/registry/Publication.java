package dev.ringlab.domain.gamedata.registry;

import java.util.List;

/** Lossless v1 publication values. Exact decimals remain strings, including arbitrary precision. */
public record Publication(String schemaVersion, long revision, Long parentRevision, String publishedAt,
                          Publisher publishedBy, String changeSummary, String checksum, Dataset dataset) {
    public record Publisher(String id, String displayName) {}
    public record Metadata(String schemaVersion, long revision, Long parentRevision, String publishedAt,
                           Publisher publishedBy, String changeSummary, String checksum, String datasetFingerprint) {}
    public enum RacingType { SPEED, ACCELERATION, HANDLING, POWER, BOOST }
    public enum PartType { FRONT, REAR, TIRE }
    public enum Status { REVIEWED_SOURCE, ASSUMED, DERIVED, HISTORICAL_PLACEHOLDER, CONFLICTING, UNKNOWN }
    public enum Mapping { COMPLETE, INCOMPLETE }
    public enum EvidenceKind { OFFICIAL, COMMUNITY, REVIEW_LEDGER }
    public enum AcquisitionKind { STANDARD_UNLOCK, FESTIVAL_REWARD, UNKNOWN }
    public enum MapCategory { MAIN_COURSE, CROSSWORLD }
    public enum PassiveKind { PASSIVE, CONDITIONAL, NON_STAT, UNSUPPORTED }
    public enum Subject { ANY, MACHINE, RACER }
    public enum Condition { LAP_ONE, LAP_THREE, WATER, FLIGHT, TRANSFORMED, HAS_RINGS, LANDING_BOOST, FINISH_ZONE }
    public enum EffectType { PASSIVE, SCENARIO }
    public record Evidence(String url, EvidenceKind kind, String locator) {}
    public record Claim(List<String> fields, Status status, List<Evidence> evidence, String note) {
        public Claim { fields = List.copyOf(fields); evidence = List.copyOf(evidence); }
    }
    public record Provenance(Status status, Mapping mapping, List<Claim> claims, String note) {
        public Provenance { claims = List.copyOf(claims); }
    }
    public record Stats(String speed, String acceleration, String handling, String power, String boost) {}
    public record Racer(String id, String name, RacingType racingType, String imagePath, Provenance provenance) {}
    public record Machine(String id, String name, RacingType racingType, String imagePath, Provenance provenance) {}
    public record MachinePart(String id, String sourceMachineId, PartType partType, Provenance provenance) {}
    public record Gadget(String id, String name, String description, Integer slotCost, String imagePath,
                         AcquisitionKind acquisitionKind, String acquisitionLabel, Provenance provenance) {}
    public record GameMap(String id, String name, MapCategory category, String contentPack, String imagePath,
                          int catalogOrder, Provenance provenance) {}
    public record GameVersion(String id, String version, String releasedAt, Provenance provenance) {}
    public record RacerStats(String racerId, Stats stats, Provenance provenance) {}
    public record MachinePartStats(String machinePartId, Stats stats, Provenance provenance) {}
    public record MissingRacer(String racerId, Provenance provenance) {}
    public record MissingMachinePart(String machinePartId, Provenance provenance) {}
    public record StatSnapshot(String gameVersionId, List<RacerStats> racers, List<MachinePartStats> machineParts,
                               List<MissingRacer> missingRacers, List<MissingMachinePart> missingMachineParts,
                               Provenance provenance) {
        public StatSnapshot {
            racers = List.copyOf(racers); machineParts = List.copyOf(machineParts);
            missingRacers = List.copyOf(missingRacers); missingMachineParts = List.copyOf(missingMachineParts);
        }
    }
    public record PassiveRule(String gadgetId, String effectId, int position, PassiveKind kind, Subject subject,
                              RacingType requiredRacingType, Stats matching, Stats nonMatching, String label,
                              String explanation, String stackingGroup, Boolean scenarioStatPotential,
                              Provenance provenance) {}
    public record ScenarioRule(String gadgetId, String effectId, int position, Condition condition, Stats adjustment,
                               String label, String explanation, Provenance provenance) {}
    public record RuleSource(EffectType effectType, String gadgetId, String effectId, int position, String url,
                             Provenance provenance) {}
    public record RuleSet(String gameVersionId, String passiveRuleset, String scenarioRuleset,
                          List<PassiveRule> passiveRules, List<ScenarioRule> scenarioRules,
                          List<RuleSource> ruleSources, Provenance provenance) {
        public RuleSet { passiveRules = List.copyOf(passiveRules); scenarioRules = List.copyOf(scenarioRules); ruleSources = List.copyOf(ruleSources); }
    }
    public record Dataset(List<Racer> racers, List<Machine> machines, List<MachinePart> machineParts,
                          List<Gadget> gadgets, List<GameMap> maps, List<GameVersion> gameVersions,
                          List<StatSnapshot> statSnapshots, List<RuleSet> ruleSets) {
        public Dataset {
            racers = List.copyOf(racers); machines = List.copyOf(machines); machineParts = List.copyOf(machineParts);
            gadgets = List.copyOf(gadgets); maps = List.copyOf(maps); gameVersions = List.copyOf(gameVersions);
            statSnapshots = List.copyOf(statSnapshots); ruleSets = List.copyOf(ruleSets);
        }
    }
}
