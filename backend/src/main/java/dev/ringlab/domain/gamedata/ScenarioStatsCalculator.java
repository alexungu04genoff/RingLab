package dev.ringlab.domain.gamedata;

import static dev.ringlab.domain.gamedata.ScenarioStatsResult.Status.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Adds a reviewed scenario layer to the existing passive result, without recomputing it. */
public final class ScenarioStatsCalculator {
  private ScenarioStatsCalculator() {}

  public static ScenarioStatsResult calculate(GadgetRuleSnapshot snapshot, PassiveStatsResult passive, ScenarioContext context) {
    Objects.requireNonNull(passive);
    Objects.requireNonNull(context);
    boolean unavailable = passive.coverage() == PassiveStatsResult.Coverage.UNSUPPORTED_VERSION
        || passive.coverage() == PassiveStatsResult.Coverage.INVALID_LOADOUT;
    var effects = new ArrayList<ScenarioStatsResult.Effect>();
    for (var original : passive.effects()) {
      boolean conditional = snapshot.forGadget(original.gadgetId()).stream()
          .anyMatch(r -> r.effectId().equals(original.effectId()) && r.kind() == GadgetEffectRule.Kind.CONDITIONAL);
      if (!conditional) continue;
      var rule = snapshot.scenarioRule(original.gadgetId(), original.effectId());
      boolean stat = rule == null
          ? snapshot.unsupportedMayAffectStats(original.gadgetId(), original.effectId()) : rule.statEffect();
      Boolean active = rule == null ? null : rule.condition().matches(context);
      var status = unavailable || rule == null ? UNSUPPORTED : active == null ? CONDITION_UNKNOWN
          : !active ? CONDITION_NOT_MET : stat ? ACTIVE_AND_APPLIED : ACTIVE_NON_STAT;
      String explanation = unavailable ? "Preview is unavailable for this patch or loadout. See the passive coverage details."
          : rule == null ? (stat ? "The condition, accumulation limit or interaction is not fully verified; no adjustment is calculated."
              : original.explanation() + " This utility is not modeled by Scenario Preview and is separate from five-stat points.")
          : active == null ? "Specify the condition to evaluate this effect."
          : !active ? "The specified condition does not activate this effect." : rule.explanation();
      effects.add(new ScenarioStatsResult.Effect(original.gadgetId(), original.gadgetName(), original.effectId(),
          rule == null ? original.label() : rule.label(), status, stat,
          status == ACTIVE_AND_APPLIED ? rule.adjustment() : status == CONDITION_NOT_MET && stat ? BaseStats.ZERO : null,
          explanation, rule == null ? original.sources() : rule.sources()));
      if (original.gadgetId().equals(PassiveGadgetRules.id(18))) {
        effects.add(new ScenarioStatsResult.Effect(original.gadgetId(), original.gadgetName(), "terrain",
            "Terrain effects", UNSUPPORTED, false, null,
            "Offroad speed and traction are separate from this kit's transformation stat bonus and are not modeled.", original.sources()));
      }
      if (original.gadgetId().equals(ScenarioGadgetRules.RING_ENGINE)) {
        effects.add(new ScenarioStatsResult.Effect(original.gadgetId(), original.gadgetName(), "speed-drain",
            "Physical speed and ring drain", unavailable ? UNSUPPORTED : active == null ? CONDITION_UNKNOWN
                : active ? ACTIVE_NON_STAT : CONDITION_NOT_MET, false, null,
            "The engine also changes physical speed and consumes rings over time. Neither is converted to stat points or simulated.", original.sources()));
      }
    }

    // Snapshot the candidates before rejecting any, so saved order cannot change the outcome.
    var active = effects.stream().filter(e -> e.status() == ACTIVE_AND_APPLIED).toList();
    boolean unresolvedNumeric = effects.stream().anyMatch(e -> e.statEffect() && e.status() == UNSUPPORTED);
    boolean passiveModifier = passive.effects().stream().anyMatch(e -> e.status() == PassiveStatsResult.Status.APPLIED
        && !BaseStats.ZERO.equals(e.adjustment()));
    boolean unresolvedPassive = passive.effects().stream().anyMatch(e -> e.status() == PassiveStatsResult.Status.UNSUPPORTED);
    for (int i = 0; i < effects.size(); i++) {
      var e = effects.get(i);
      if (e.status() != ACTIVE_AND_APPLIED) continue;
      boolean unresolvedPair = active.stream().anyMatch(other -> other != e
          && !ScenarioGadgetRules.assumedAdditivePair(e.gadgetId(), e.effectId(), other.gadgetId(), other.effectId()));
      if (unresolvedPair || unresolvedNumeric || passiveModifier || unresolvedPassive) {
        effects.set(i, new ScenarioStatsResult.Effect(e.gadgetId(), e.gadgetName(),
            e.effectId(), e.label(), UNSUPPORTED, true, null,
            "The individual bonus is known, but its combination with another selected stat effect is not verified. The passive result remains visible.", e.sources()));
      } else if (active.stream().anyMatch(other -> other != e
          && ScenarioGadgetRules.assumedAdditivePair(e.gadgetId(), e.effectId(), other.gadgetId(), other.effectId()))) {
        effects.set(i, new ScenarioStatsResult.Effect(e.gadgetId(), e.gadgetName(),
            e.effectId(), e.label(), ACTIVE_AND_APPLIED, true, e.adjustment(),
            e.explanation() + " Quick Starter + Sea Dog are assumed additive in this preview; their combined in-game behavior is not verified.",
            e.sources()));
      }
    }
    var adjustments = new ArrayList<BaseStats>();
    adjustments.add(BaseStats.ZERO);
    effects.stream().filter(e -> e.status() == ACTIVE_AND_APPLIED).forEach(e -> adjustments.add(e.adjustment()));
    var delta = BaseStats.sum(adjustments);
    var subtotal = BaseStats.sum(List.of(passive.adjusted(), delta));
    boolean partial = passive.coverage() != PassiveStatsResult.Coverage.CALCULATED
        || effects.stream().anyMatch(e -> e.statEffect() && (e.status() == CONDITION_UNKNOWN || e.status() == UNSUPPORTED));
    var coverage = unavailable ? ScenarioStatsResult.Coverage.UNAVAILABLE
        : partial ? ScenarioStatsResult.Coverage.PARTIAL : ScenarioStatsResult.Coverage.CALCULATED;
    return new ScenarioStatsResult(passive, delta, subtotal, partial ? null : subtotal, coverage,
        snapshot.scenarioRuleset(), ScenarioGadgetRules.VERSION,
        "Raw stat-point preview, not a prediction of race performance. Unknown effects are not zero; physical speed, timing and effective in-game caps are not calculated.", effects);
  }
}
