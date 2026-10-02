package dev.ringlab.application.gamedata;

import dev.ringlab.domain.gamedata.*;
import dev.ringlab.domain.gamedata.importing.GameDataSet.StatRow;
import dev.ringlab.domain.gamedata.importing.GameDataRuleSet.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Named fields used for readable differences and deterministic approval fingerprints. */
final class CatalogFields {
  static Map<String, Object> racer(Racer r) {
    return fields("id", r.id(), "name", r.name(), "racing_type", r.racingType(), "image_path", r.imagePath());
  }
  static Map<String, Object> machine(Machine m) {
    return fields("id", m.id(), "name", m.name(), "racing_type", m.racingType(), "image_path", m.imagePath());
  }
  static Map<String, Object> part(MachinePart p) {
    return fields("id", p.id(), "source_machine_id", p.sourceMachineId(), "part_type", p.type());
  }
  static Map<String, Object> gadget(Gadget g) {
    return fields("id", g.id(), "name", g.name(), "description", g.description(), "slot_cost", g.slotCost(), "image_path", g.imagePath());
  }
  static Map<String, Object> map(RaceMap m) {
    return fields("id", m.id(), "name", m.name(), "category", m.category(), "content_pack", m.contentPack(), "image_path", m.imagePath(), "catalog_order", m.catalogOrder());
  }
  static Map<String, Object> version(GameVersion v) {
    return fields("id", v.id(), "version", v.version(), "released_at", v.releasedAt());
  }
  static Map<String, Object> stats(StatRow r) {
    var s = r.stats();
    return fields("id", r.itemId(), "speed", s.speed(), "acceleration", s.acceleration(), "handling", s.handling(), "power", s.power(), "boost", s.boost());
  }
  static Map<String, Object> ruleSet(Metadata m) {
    return fields("id", m.versionId(), "passive_ruleset", m.passiveRuleset(), "scenario_ruleset", m.scenarioRuleset());
  }
  static Map<String, Object> passive(PassiveFact f) {
    var r = f.rule();
    var values = fields("id", r.gadgetId() + "/" + r.effectId(), "position", f.position(), "kind", r.kind(),
        "subject", r.subject(), "required_racing_type", r.requiredType(), "label", r.label(), "explanation", r.explanation(),
        "stacking_group", r.stackingGroup(), "scenario_stat_potential", f.scenarioStatPotential());
    vector(values, "matching_", r.matching()); vector(values, "nonmatching_", r.nonMatching());
    return values;
  }
  static Map<String, Object> scenario(ScenarioFact f) {
    var r = f.rule();
    var values = fields("id", r.gadgetId() + "/" + r.effectId(), "position", f.position(), "condition", r.condition(),
        "label", r.label(), "explanation", r.explanation());
    vector(values, "", r.adjustment());
    return values;
  }
  static Map<String, Object> source(Source s) {
    return fields("id", s.effectType() + "/" + s.gadgetId() + "/" + s.effectId() + "/" + s.position(), "url", s.url());
  }
  private static void vector(Map<String, Object> values, String prefix, BaseStats s) {
    var stats = s == null ? BaseStats.UNKNOWN : s;
    values.put(prefix + "speed", stats.speed()); values.put(prefix + "acceleration", stats.acceleration());
    values.put(prefix + "handling", stats.handling()); values.put(prefix + "power", stats.power()); values.put(prefix + "boost", stats.boost());
  }
  private static Map<String, Object> fields(Object... pairs) {
    var result = new LinkedHashMap<String, Object>();
    for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
    return result;
  }
}
