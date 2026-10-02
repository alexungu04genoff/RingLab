package dev.ringlab.characterization;

import dev.ringlab.domain.gamedata.*;

import static dev.ringlab.characterization.PassiveGadgetRules.id;
import static dev.ringlab.domain.gamedata.ScenarioEffectRule.Condition.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Exact scope, identity mapping and combination limits: docs/scenario-preview-evidence.md. */
public final class ScenarioGadgetRules {
  public static final String VERSION = "1.4.1";
  public static final String RULESET = "crossworlds-1.4.1-scenario-2026-10-01.1";
  public static final UUID RING_ENGINE = UUID.fromString("5a000a58-7d7a-581c-80e3-6ae8661215b7");
  public static final UUID HYPER_RING_ENGINE = UUID.fromString("182bdfa8-44d3-5de8-941b-d525381dd0a3");
  private static final List<String> SOURCES = List.of(PassiveGadgetRules.ORIGINAL, PassiveGadgetRules.SEGA,
      "https://www.srcgadgetbuilder.com/build");
  private static final List<ScenarioEffectRule> RULES = List.of(
      points(id(45), LAP_ONE, 20, "Lap 1 bonus"), points(id(47), LAP_ONE, 60, "Lap 1 bonus"),
      points(id(46), LAP_THREE, 20, "Lap 3 bonus"), points(id(48), LAP_THREE, 60, "Lap 3 bonus"),
      points(id(15), WATER, 20, "Water-form bonus"), points(id(17), FLIGHT, 20, "Flight-form bonus"),
      points(id(18), TRANSFORMED, 20, "Transformation bonus"),
      points(RING_ENGINE, HAS_RINGS, 12, "Held-ring bonus"),
      new ScenarioEffectRule(id(2), "other-0", "Landing boost", LANDING_BOOST, null,
          "The successful landing boost is active. Its speed and duration are not five-stat points.", SOURCES),
      new ScenarioEffectRule(id(4), "other-0", "Finish-line invincibility", FINISH_ZONE, null,
          "Invincibility applies within 300 metres of the finish. It adds no five-stat points.", SOURCES));
  // These reviewed conditional effects are utility-only. Future identities default to unknown.
  private static final Set<UUID> UTILITY = Set.of(id(1),id(3),id(11),id(12),id(13),id(16),id(20),
      id(23),id(27),id(29),id(31),id(32),id(34),id(36),id(37),id(38),id(43),id(49),id(62),
      UUID.fromString("b3002fe1-32a2-5a06-8c07-64d0511811ad"),
      UUID.fromString("97147799-cf1d-5f50-b133-3ff6091574d3"),
      UUID.fromString("f3b3dd31-9912-5a84-a8db-a76d1f93dcbe"),
      UUID.fromString("180a09fc-0034-5f56-be95-2938ce9ed61d"),
      UUID.fromString("b5f42975-23b3-5a1d-9173-c18d2e584244"),
      UUID.fromString("175c2797-0be5-571a-99ac-af4bfdbf58a4"),
      UUID.fromString("9735d3ab-d53b-52a1-a80d-ed0cb89429c2"));

  private ScenarioGadgetRules() {}
  public static List<ScenarioEffectRule> all() { return RULES; }
  public static ScenarioEffectRule find(UUID gadget, String effect) {
    return RULES.stream().filter(r -> r.gadgetId().equals(gadget) && r.effectId().equals(effect))
        .findFirst().orElse(null);
  }
  public static boolean unsupportedMayAffectStats(UUID gadget, String effect) {
    return !UTILITY.contains(gadget) && !(gadget.equals(id(17)) && effect.equals("other-1"));
  }
  /** User-approved modeling assumption, not verified game behavior. Applies only to this effect pair. */
  public static boolean assumedAdditivePair(UUID first, String firstEffect, UUID second, String secondEffect) {
    if (!"other-0".equals(firstEffect) || !"other-0".equals(secondEffect)) return false;
    return (first.equals(id(45)) && second.equals(id(15)))
        || (first.equals(id(15)) && second.equals(id(45)));
  }
  private static ScenarioEffectRule points(UUID id, ScenarioEffectRule.Condition condition, int value, String label) {
    return new ScenarioEffectRule(id, "other-0", label, condition,
        PassiveGadgetRules.points(value,value,value,value,value),
        "Fixed +" + value + " to each stat while the condition holds; does not accumulate. "
            + "Concurrent nonzero stat modifiers require an explicit stacking permission.", SOURCES);
  }
}
