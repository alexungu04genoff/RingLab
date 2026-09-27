package dev.ringlab.domain.gamedata;

import static dev.ringlab.domain.gamedata.PassiveStatsResult.Status.*;
import static dev.ringlab.domain.gamedata.PassiveStatsResult.Coverage.*;

import java.util.ArrayList;
import java.util.List;

/** Pure arithmetic over reviewed rules. No ranking, map, race-state or persistence inputs. */
public final class PassiveStatsCalculator {
  private PassiveStatsCalculator() {}
  public static final String ARITHMETIC_NOTE = "Known passive stat-point arithmetic; effective in-game caps are not established. Race-time effects are excluded.";

  public static PassiveStatsResult calculate(BaseStatsBreakdown base, String version,
      RacingType racerType, RacingType machineType, List<Gadget> gadgets, boolean validLoadout) {
    var effects = new ArrayList<PassiveStatsResult.Effect>();
    boolean supported = PassiveGadgetRules.VERSION.equals(version);
    for (var gadget : gadgets) {
      var rules = PassiveGadgetRules.forGadget(gadget.id());
      if (rules.isEmpty()) {
        effects.add(new PassiveStatsResult.Effect(gadget.id(),gadget.name(),"unreviewed","Unverified effects",
            UNSUPPORTED,PassiveGadgetRules.ZERO,"No reviewed rule for this gadget; an unknown effect is not zero.",List.of()));
      }
      for (var rule : rules) {
        var status = switch (rule.kind()) {
          case CONDITIONAL -> CONDITIONAL;
          case NON_STAT -> NON_STAT;
          case UNSUPPORTED -> UNSUPPORTED;
          case PASSIVE -> APPLIED;
        };
        var adjustment = PassiveGadgetRules.ZERO;
        String explanation = rule.explanation();
        if (!supported || !validLoadout) {
          status = UNSUPPORTED;
          explanation = !supported ? "Gadget rules are available only for Ver. " + PassiveGadgetRules.VERSION + "."
              : "Invalid saved loadout: base stats remain available, but gadget adjustments are not calculated.";
        } else if (rule.kind() == GadgetEffectRule.Kind.PASSIVE) {
          var actual = rule.subject() == GadgetEffectRule.Subject.RACER ? racerType : machineType;
          if (rule.subject() != GadgetEffectRule.Subject.ANY && actual == null) {
            status = REQUIRES_SELECTION;
            explanation = "Requires a known, coherent " + rule.subject().name().toLowerCase() + " selection.";
          } else {
            boolean matches = rule.subject() == GadgetEffectRule.Subject.ANY || actual == rule.requiredType();
            adjustment = matches ? rule.matching() : rule.nonMatching();
            if (!matches && adjustment.equals(PassiveGadgetRules.ZERO)) status = NOT_MATCHED;
            explanation += matches ? " Condition satisfied." : " Selected type is " + actual + "; matching-type bonuses and penalties do not apply.";
          }
        }
        effects.add(new PassiveStatsResult.Effect(gadget.id(),gadget.name(),rule.effectId(),rule.label(),
            status,adjustment,explanation,rule.sources()));
      }
    }

    // The reviewed evidence establishes Tuner 1 + Tuner 2 of the same type.
    // Other multi-modifier interactions remain explicitly unsupported, never guessed additive.
    var applied = effects.stream().filter(e -> e.status() == APPLIED).toList();
    if (applied.size() > 1 && !verifiedStack(applied)) {
      for (int i = 0; i < effects.size(); i++) {
        var effect = effects.get(i);
        if (effect.status() == APPLIED) effects.set(i,new PassiveStatsResult.Effect(effect.gadgetId(),effect.gadgetName(),
            effect.effectId(),effect.label(),UNSUPPORTED,PassiveGadgetRules.ZERO,
            "Individual modifier is verified, but stacking with the other selected passive modifiers is unresolved. This combination is not added.",effect.sources()));
      }
    }
    var values = new ArrayList<BaseStats>();
    values.add(PassiveGadgetRules.ZERO);
    effects.stream().filter(e -> e.status() == APPLIED).forEach(e -> values.add(e.adjustment()));
    var adjustments = BaseStats.sum(values);
    var adjusted = BaseStats.sum(List.of(base.total(),adjustments));
    boolean incomplete = effects.stream().anyMatch(e -> e.status() == UNSUPPORTED || e.status() == REQUIRES_SELECTION)
        || adjusted.speed() == null || adjusted.acceleration() == null || adjusted.handling() == null
        || adjusted.power() == null || adjusted.boost() == null;
    var coverage = !validLoadout ? INVALID_LOADOUT : !supported ? UNSUPPORTED_VERSION : incomplete ? PARTIAL : CALCULATED;
    return new PassiveStatsResult(base,adjustments,adjusted,coverage,PassiveGadgetRules.RULESET,
        PassiveGadgetRules.VERSION,ARITHMETIC_NOTE,effects);
  }

  private static boolean verifiedStack(List<PassiveStatsResult.Effect> effects) {
    if (effects.size() != 2 || effects.get(0).gadgetId().equals(effects.get(1).gadgetId())) return false;
    var first = PassiveGadgetRules.forGadget(effects.get(0).gadgetId()).stream()
        .filter(r -> r.effectId().equals(effects.get(0).effectId())).findFirst().orElseThrow();
    var second = PassiveGadgetRules.forGadget(effects.get(1).gadgetId()).stream()
        .filter(r -> r.effectId().equals(effects.get(1).effectId())).findFirst().orElseThrow();
    return first.stackingGroup() != null && first.stackingGroup().equals(second.stackingGroup());
  }
}
