package dev.ringlab.application.gamedata;

import dev.ringlab.domain.gamedata.*;
import dev.ringlab.domain.gamedata.importing.*;
import dev.ringlab.domain.gamedata.importing.GameDataRuleSet.*;
import java.net.URI;
import java.util.*;
import java.util.function.Function;

/** Validates the relational rule bundle without interpreting race conditions or arithmetic. */
final class GameDataRuleValidator {
  void validate(GameDataSet data, List<String> errors) {
    var versions = new HashSet<UUID>();
    data.versions().forEach(r -> versions.add(r.value().id()));
    var gadgets = new HashSet<UUID>();
    data.gadgets().forEach(r -> gadgets.add(r.value().id()));
    var seen = new HashSet<UUID>();
    for (var rules : data.ruleSets()) {
      if (!versions.contains(rules.versionId()) || !seen.add(rules.versionId()))
        errors.add(rules.metadata().problem("game_version_id", rules.versionId(), "Unknown or duplicate rule snapshot version"));
      unique(rules.passive(), f -> key(f.rule().gadgetId(), f.rule().effectId()), "gadget_id/effect_id", errors);
      unique(rules.scenario(), f -> key(f.rule().gadgetId(), f.rule().effectId()), "gadget_id/effect_id", errors);
      positions(rules.passive(), f -> f.rule().gadgetId().toString(), PassiveFact::position, errors);
      positions(rules.scenario(), f -> "scenario", ScenarioFact::position, errors);
      var passive = new HashMap<String, PassiveFact>();
      for (var row : rules.passive()) {
        var f = row.value(); var r = f.rule();
        passive.put(key(r.gadgetId(), r.effectId()), f);
        if (!gadgets.contains(r.gadgetId())) errors.add(row.problem("gadget_id", r.gadgetId(), "Referenced gadget does not exist"));
        if (!complete(r.matching()) || !complete(r.nonMatching()))
          errors.add(row.problem("stat_vectors", r.matching() + "/" + r.nonMatching(), "Passive vectors require five known decimal values; unknown effects use kind UNSUPPORTED"));
        if ((r.subject() == GadgetEffectRule.Subject.ANY) != (r.requiredType() == null))
          errors.add(row.problem("required_racing_type", r.requiredType(), "ANY requires blank type; MACHINE/RACER require a racing type"));
        if ((r.kind() == GadgetEffectRule.Kind.CONDITIONAL) != (f.scenarioStatPotential() != null))
          errors.add(row.problem("scenario_stat_potential", f.scenarioStatPotential(), "Required true/false only for CONDITIONAL effects"));
        if (r.kind() != GadgetEffectRule.Kind.PASSIVE && (!zero(r.matching()) || !zero(r.nonMatching())
            || r.subject() != GadgetEffectRule.Subject.ANY || r.stackingGroup() != null))
          errors.add(row.problem("kind", r.kind(), "Non-passive effects require zero vectors, ANY subject and no stacking group"));
        // Preserve the historical CSV label contract; this metadata no longer grants arithmetic permission.
        if (r.stackingGroup() != null && !("machine-tuners".equals(r.stackingGroup())
            && PassiveGadgetRules.reviewedNumeric(r) && r.subject() == GadgetEffectRule.Subject.MACHINE
            && java.util.stream.IntStream.rangeClosed(52,61).anyMatch(n -> PassiveGadgetRules.id(n).equals(r.gadgetId()))))
          errors.add(row.problem("stacking_group", r.stackingGroup(), "Unrecognized historical tuner group identity"));
      }
      var scenario = new HashSet<String>();
      for (var row : rules.scenario()) {
        var r = row.value().rule(); var effect = key(r.gadgetId(), r.effectId());
        scenario.add(effect);
        var original = passive.get(effect);
        if (original == null || original.rule().kind() != GadgetEffectRule.Kind.CONDITIONAL)
          errors.add(row.problem("gadget_id/effect_id", effect, "Scenario effect must reference a CONDITIONAL passive effect"));
        if (r.adjustment() != null && !complete(r.adjustment()))
          errors.add(row.problem("stat_adjustment", r.adjustment(), "Supply five known decimals, or five blanks for a utility-only effect"));
      }
      unique(rules.sources(), s -> s.effectType() + "/" + key(s.gadgetId(), s.effectId()) + "/" + s.position(), "source_key", errors);
      positions(rules.sources(), s -> s.effectType() + "/" + key(s.gadgetId(), s.effectId()), Source::position, errors);
      var sourced = new HashSet<String>();
      for (var row : rules.sources()) {
        var s = row.value(); var effect = key(s.gadgetId(), s.effectId());
        sourced.add(s.effectType() + "/" + effect);
        if (s.effectType() == EffectType.PASSIVE ? !passive.containsKey(effect) : !scenario.contains(effect))
          errors.add(row.problem("gadget_id/effect_id", effect, "Source references an unknown " + s.effectType() + " effect"));
        try {
          var uri = URI.create(s.url());
          if (uri.getHost() == null || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getUserInfo() != null)
            throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) { errors.add(row.problem("url", s.url(), "Expected an absolute HTTP(S) source URL without credentials")); }
      }
      for (var row : rules.passive()) if (!sourced.contains("PASSIVE/" + key(row.value().rule().gadgetId(), row.value().rule().effectId())))
        errors.add(row.problem("sources", row.value().rule().effectId(), "At least one source is required"));
      for (var row : rules.scenario()) if (!sourced.contains("SCENARIO/" + key(row.value().rule().gadgetId(), row.value().rule().effectId())))
        errors.add(row.problem("sources", row.value().rule().effectId(), "At least one source is required"));
      if (rules.passive().isEmpty()) errors.add(rules.metadata().problem("passive_ruleset", rules.metadata().value().passiveRuleset(), "A reviewed snapshot must contain passive rules"));
    }
  }

  private static String key(UUID gadget, String effect) { return gadget + "/" + effect; }
  private static boolean complete(BaseStats s) {
    return s != null && s.speed() != null && s.acceleration() != null && s.handling() != null && s.power() != null && s.boost() != null;
  }
  private static boolean zero(BaseStats s) {
    return complete(s) && List.of(s.speed(), s.acceleration(), s.handling(), s.power(), s.boost()).stream().allMatch(n -> n.signum() == 0);
  }
  private <T> void unique(List<CatalogRow<T>> rows, Function<T, String> key, String field, List<String> errors) {
    var seen = new HashSet<String>();
    for (var row : rows) if (!seen.add(key.apply(row.value()))) errors.add(row.problem(field, key.apply(row.value()), "Duplicate value"));
  }
  private <T> void positions(List<CatalogRow<T>> rows, Function<T, String> group, Function<T, Integer> position, List<String> errors) {
    var groups = new HashMap<String, List<CatalogRow<T>>>();
    rows.forEach(r -> groups.computeIfAbsent(group.apply(r.value()), ignored -> new ArrayList<>()).add(r));
    for (var values : groups.values()) {
      values.sort(Comparator.comparing(r -> position.apply(r.value())));
      for (int i = 0; i < values.size(); i++) {
        var row = values.get(i);
        if (position.apply(row.value()) != i) errors.add(row.problem("position", position.apply(row.value()), "Positions must be unique and contiguous from 0 within their parent"));
      }
    }
  }
}
