package dev.ringlab.adapter.out.db.gamedata;

import dev.ringlab.domain.gamedata.*;
import dev.ringlab.domain.gamedata.importing.*;
import dev.ringlab.domain.gamedata.importing.GameDataRuleSet.*;
import java.sql.*;
import java.util.*;

/** Shared JDBC row mapping for administrative imports and detached runtime snapshots. */
final class GameDataRuleStorage {
  private static final String PASSIVE_COLUMNS = "gadget_id,effect_id,position,kind,subject,required_racing_type,matching_speed,matching_acceleration,matching_handling,matching_power,matching_boost,nonmatching_speed,nonmatching_acceleration,nonmatching_handling,nonmatching_power,nonmatching_boost,label,explanation,stacking_group,scenario_stat_potential";
  private static final String SCENARIO_COLUMNS = "gadget_id,effect_id,position,condition,speed,acceleration,handling,power,boost,label,explanation";

  List<GameDataRuleSet> read(Connection c, UUID version) throws SQLException {
    var metadata = rows(c, "gadget_rule_sets", version, r -> new Metadata(uuid(r, "game_version_id"), r.getString("passive_ruleset"), r.getString("scenario_ruleset")));
    var result = new ArrayList<GameDataRuleSet>();
    for (var row : metadata) {
      var id = row.value().versionId();
      var passive = rows(c, "passive_gadget_rules", id, r -> new PassiveFact(r.getInt("position"),
          new GadgetEffectRule(uuid(r, "gadget_id"), r.getString("effect_id"), r.getString("label"),
              GadgetEffectRule.Kind.valueOf(r.getString("kind")), GadgetEffectRule.Subject.valueOf(r.getString("subject")),
              r.getString("required_racing_type") == null ? null : RacingType.valueOf(r.getString("required_racing_type")),
              stats(r, "matching_"), stats(r, "nonmatching_"), r.getString("explanation"), List.of(), r.getString("stacking_group")),
          r.getObject("scenario_stat_potential", Boolean.class)));
      var scenario = rows(c, "scenario_gadget_rules", id, r -> {
        var values = stats(r, "");
        return new ScenarioFact(r.getInt("position"), new ScenarioEffectRule(uuid(r, "gadget_id"), r.getString("effect_id"),
            r.getString("label"), ScenarioEffectRule.Condition.valueOf(r.getString("condition")),
            values.equals(BaseStats.UNKNOWN) ? null : values, r.getString("explanation"), List.of()));
      });
      var sources = new ArrayList<CatalogRow<Source>>();
      for (var type : EffectType.values()) sources.addAll(rows(c, sourceTable(type), id,
          r -> new Source(type, uuid(r, "gadget_id"), r.getString("effect_id"), r.getInt("position"), r.getString("url"))));
      result.add(new GameDataRuleSet(row, passive, scenario, sources));
    }
    return List.copyOf(result);
  }

  void insert(Connection c, List<GameDataRuleSet> bundles) throws SQLException {
    for (var bundle : bundles) {
      var m = bundle.metadata().value();
      insert(c, "gadget_rule_sets", "game_version_id,passive_ruleset,scenario_ruleset", m.versionId(), m.passiveRuleset(), m.scenarioRuleset());
      for (var row : bundle.passive()) {
        var f = row.value(); var r = f.rule(); var yes = r.matching(); var no = r.nonMatching();
        insert(c, "passive_gadget_rules", "game_version_id," + PASSIVE_COLUMNS, m.versionId(), r.gadgetId(), r.effectId(),
            f.position(), r.kind(), r.subject(), r.requiredType(), yes.speed(), yes.acceleration(), yes.handling(), yes.power(), yes.boost(),
            no.speed(), no.acceleration(), no.handling(), no.power(), no.boost(), r.label(), r.explanation(), r.stackingGroup(), f.scenarioStatPotential());
      }
      for (var row : bundle.scenario()) {
        var f = row.value(); var r = f.rule(); var s = r.adjustment() == null ? BaseStats.UNKNOWN : r.adjustment();
        insert(c, "scenario_gadget_rules", "game_version_id," + SCENARIO_COLUMNS, m.versionId(), r.gadgetId(), r.effectId(),
            f.position(), r.condition(), s.speed(), s.acceleration(), s.handling(), s.power(), s.boost(), r.label(), r.explanation());
      }
      for (var row : bundle.sources()) {
        var s = row.value();
        insert(c, sourceTable(s.effectType()), "game_version_id,gadget_id,effect_id,position,url", m.versionId(), s.gadgetId(), s.effectId(), s.position(), s.url());
      }
    }
  }

  private static String sourceTable(EffectType type) { return type == EffectType.PASSIVE ? "passive_rule_sources" : "scenario_rule_sources"; }
  private static UUID uuid(ResultSet r, String field) throws SQLException { return r.getObject(field, UUID.class); }
  private static BaseStats stats(ResultSet r, String prefix) throws SQLException {
    return new BaseStats(r.getBigDecimal(prefix + "speed"), r.getBigDecimal(prefix + "acceleration"), r.getBigDecimal(prefix + "handling"),
        r.getBigDecimal(prefix + "power"), r.getBigDecimal(prefix + "boost"));
  }
  private <T> List<CatalogRow<T>> rows(Connection c, String table, UUID version, RowReader<T> reader) throws SQLException {
    // Table names are private constants; external values are always parameters.
    String order = table.equals("gadget_rule_sets") ? "game_version_id" : "game_version_id,gadget_id,effect_id" + (table.endsWith("sources") ? ",position" : "");
    try (var statement = c.prepareStatement("SELECT * FROM " + table + (version == null ? "" : " WHERE game_version_id=?") + " ORDER BY " + order)) {
      if (version != null) statement.setObject(1, version);
      try (var r = statement.executeQuery()) {
        var rows = new ArrayList<CatalogRow<T>>();
        while (r.next()) rows.add(new CatalogRow<>("database/" + table, r.getRow(), reader.read(r)));
        return rows;
      }
    }
  }
  private void insert(Connection c, String table, String columns, Object... values) throws SQLException {
    try (var statement = c.prepareStatement("INSERT INTO " + table + "(" + columns + ") VALUES ("
        + String.join(",", Collections.nCopies(values.length, "?")) + ")")) {
      for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i] instanceof Enum<?> e ? e.name() : values[i]);
      if (statement.executeUpdate() != 1) throw new SQLException("Unexpected rule insert count", "P0001");
    }
  }
  @FunctionalInterface private interface RowReader<T> { T read(ResultSet r) throws SQLException; }
}
