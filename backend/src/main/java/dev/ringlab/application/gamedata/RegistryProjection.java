package dev.ringlab.application.gamedata;

import dev.ringlab.domain.gamedata.*;
import dev.ringlab.domain.gamedata.importing.*;
import dev.ringlab.domain.gamedata.importing.GameDataSet.*;
import dev.ringlab.domain.gamedata.importing.GameDataRuleSet.*;
import dev.ringlab.domain.gamedata.registry.Publication;
import dev.ringlab.domain.gamedata.registry.RegistryComparison.Observation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

/** Projects only representable facts for comparison; the report retains the full original publication. */
final class RegistryProjection {
  record Projection(GameDataSet data, List<String> incompatible, List<Observation> observations) {}
  private final List<String> incompatible = new ArrayList<>();
  private final List<Observation> observations = new ArrayList<>();

  Projection project(Publication publication) {
    var d = publication.dataset();
    observations.add(new Observation("/dataset", "PERSISTENCE_GAP",
        "PostgreSQL has no Registry revision, publication provenance, claim/evidence mappings or explicit gap records. These remain in this report; comparison does not persist them."));
    var data = new GameDataSet(
        rows("racers", d.racers(), r -> new Racer(id(r.id()), r.name(), type(r.racingType()), r.imagePath()), Publication.Racer::provenance),
        rows("machines", d.machines(), m -> new Machine(id(m.id()), m.name(), type(m.racingType()), m.imagePath()), Publication.Machine::provenance),
        rows("machineParts", d.machineParts(), p -> new MachinePart(id(p.id()), id(p.sourceMachineId()), MachinePartType.valueOf(p.partType().name())), Publication.MachinePart::provenance),
        rows("gadgets", d.gadgets(), g -> new Gadget(id(g.id()), g.name(), g.description(), g.slotCost(), g.imagePath(), GadgetAcquisitionKind.valueOf(g.acquisitionKind().name()), g.acquisitionLabel()), Publication.Gadget::provenance),
        rows("maps", d.maps(), m -> new RaceMap(id(m.id()), m.name(), RaceMap.Category.valueOf(m.category().name()), m.contentPack(), m.imagePath(), m.catalogOrder()), Publication.GameMap::provenance),
        rows("gameVersions", d.gameVersions(), v -> new GameVersion(id(v.id()), v.version(), LocalDate.parse(v.releasedAt())), Publication.GameVersion::provenance),
        d.statSnapshots().stream().map(s -> {
          String path = "statSnapshots/" + s.gameVersionId(); provenance(path, s.provenance());
          for (var gap : s.missingRacers()) gap(path + "/missingRacers/" + gap.racerId(), gap.provenance());
          for (var gap : s.missingMachineParts()) gap(path + "/missingMachineParts/" + gap.machinePartId(), gap.provenance());
          return new VersionStats(id(s.gameVersionId()),
              rows(path + "/racers", s.racers(), r -> new StatRow(id(r.racerId()), stats(r.stats())), Publication.RacerStats::provenance),
              rows(path + "/machineParts", s.machineParts(), r -> new StatRow(id(r.machinePartId()), stats(r.stats())), Publication.MachinePartStats::provenance));
        }).toList(), d.ruleSets().stream().map(this::rules).toList());
    checkRows(data.racers(), CatalogFields::racer, Map.of("name",100,"image_path",255));
    checkRows(data.machines(), CatalogFields::machine, Map.of("name",100,"image_path",255));
    checkRows(data.gadgets(), CatalogFields::gadget, Map.of("name",100,"image_path",255,"acquisition_label",255));
    checkRows(data.maps(), CatalogFields::map, Map.of("name",120,"image_path",255,"content_pack",120));
    checkRows(data.versions(), CatalogFields::version, Map.of("version",32));
    for (var s : data.snapshots()) { checkRows(s.racers(), CatalogFields::stats, Map.of()); checkRows(s.parts(), CatalogFields::stats, Map.of()); }
    for (var r : data.ruleSets()) {
      checkRows(List.of(r.metadata()), CatalogFields::ruleSet, Map.of("passive_ruleset",120,"scenario_ruleset",120));
      checkRows(r.passive(), CatalogFields::passive, Map.of("label",255,"stacking_group",80));
      checkRows(r.scenario(), CatalogFields::scenario, Map.of("label",255));
      checkRows(r.sources(), CatalogFields::source, Map.of("url",2048));
      for (var f : r.passive()) checkText(f.file(), "effect_id", f.value().rule().effectId(), 80);
    }
    return new Projection(data, List.copyOf(incompatible), List.copyOf(observations));
  }

  private GameDataRuleSet rules(Publication.RuleSet r) {
    String path = "ruleSets/" + r.gameVersionId(); provenance(path, r.provenance());
    for (var f : r.passiveRules()) if (f.kind() != Publication.PassiveKind.PASSIVE)
      observations.add(new Observation("/dataset/" + path + "/passiveRules/" + f.gadgetId() + "/" + f.effectId(),
          "NON_PASSIVE_RULE", f.kind() + ": stored zero vectors are placeholders, not numerical zero effects. No calculation permission granted."));
    return new GameDataRuleSet(new CatalogRow<>(path, 1, new Metadata(id(r.gameVersionId()), r.passiveRuleset(), r.scenarioRuleset())),
        rows(path + "/passiveRules", r.passiveRules(), f -> new PassiveFact(f.position(),
            new GadgetEffectRule(id(f.gadgetId()), f.effectId(), f.label(), GadgetEffectRule.Kind.valueOf(f.kind().name()),
                GadgetEffectRule.Subject.valueOf(f.subject().name()), type(f.requiredRacingType()), stats(f.matching()), stats(f.nonMatching()),
                f.explanation(), List.of(), f.stackingGroup()), f.scenarioStatPotential()), Publication.PassiveRule::provenance),
        rows(path + "/scenarioRules", r.scenarioRules(), f -> new ScenarioFact(f.position(),
            new ScenarioEffectRule(id(f.gadgetId()), f.effectId(), f.label(), ScenarioEffectRule.Condition.valueOf(f.condition().name()),
                stats(f.adjustment()), f.explanation(), List.of())), Publication.ScenarioRule::provenance),
        rows(path + "/ruleSources", r.ruleSources(), s -> new Source(EffectType.valueOf(s.effectType().name()), id(s.gadgetId()), s.effectId(), s.position(), s.url()), Publication.RuleSource::provenance));
  }
  private <S,T> List<CatalogRow<T>> rows(String path, List<S> source, Function<S,T> map, Function<S,Publication.Provenance> provenance) {
    var result = new ArrayList<CatalogRow<T>>();
    for (int i = 0; i < source.size(); i++) {
      provenance(path + "/" + i, provenance.apply(source.get(i)));
      result.add(new CatalogRow<>("registry/" + path, i + 1, map.apply(source.get(i))));
    }
    return result;
  }
  private void gap(String path, Publication.Provenance provenance) {
    observations.add(new Observation("/dataset/" + path, "MISSING_STAT_ROW", "Explicit historical absence; no null or zero row was synthesized."));
    provenance(path, provenance);
  }
  private void provenance(String path, Publication.Provenance p) {
    if (p.mapping() == Publication.Mapping.INCOMPLETE || p.status() != Publication.Status.REVIEWED_SOURCE)
      observations.add(new Observation("/dataset/" + path + "/provenance", "UNCERTAINTY", p.status() + "; mapping=" + p.mapping() + "; " + p.note()));
    for (int i = 0; i < p.claims().size(); i++) {
      var claim = p.claims().get(i);
      if (claim.status() != Publication.Status.REVIEWED_SOURCE)
        observations.add(new Observation("/dataset/" + path + "/provenance/claims/" + i, "CLAIM_UNCERTAINTY",
            claim.status() + "; fields=" + claim.fields() + "; " + claim.note()));
    }
  }
  private <T> void checkRows(List<CatalogRow<T>> rows, Function<T,Map<String,Object>> fields, Map<String,Integer> lengths) {
    for (var row : rows) for (var entry : fields.apply(row.value()).entrySet()) {
      Object value = entry.getValue(); String field = entry.getKey();
      if (value == null) observations.add(new Observation(row.file() + "/" + row.row() + "/" + field, "EXPLICIT_NULL", "Unknown or inapplicable; preserved as null."));
      if (value instanceof String s) checkText(row.file() + "/" + row.row(), field, s, lengths.getOrDefault(field, Integer.MAX_VALUE));
      if (value instanceof BigDecimal n && (n.precision() - n.scale() > 131072 || n.scale() > 16383))
        incompatible.add(row.file() + "/" + row.row() + "/" + field + ": exact decimal exceeds PostgreSQL NUMERIC limits; never rounded.");
    }
  }
  private void checkText(String path, String field, String value, int limit) {
    if (value.indexOf(0) >= 0 || value.codePointCount(0, value.length()) > limit)
      incompatible.add(path + "/" + field + ": text cannot fit PostgreSQL constraints without loss.");
  }
  private static UUID id(String value) { return UUID.fromString(value); }
  private static RacingType type(Publication.RacingType value) { return value == null ? null : RacingType.valueOf(value.name()); }
  private static BigDecimal decimal(String value) { return value == null ? null : new BigDecimal(value); }
  private static BaseStats stats(Publication.Stats s) {
    return s == null ? null : new BaseStats(decimal(s.speed()), decimal(s.acceleration()), decimal(s.handling()), decimal(s.power()), decimal(s.boost()));
  }
}
