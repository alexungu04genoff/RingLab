package dev.ringlab.domain.gamedata;

import java.util.*;
import java.util.stream.Collectors;

/** Detached facts, ordered exactly as published. Calculators never load their own inputs. */
public record GadgetRuleSnapshot(GameVersion version, String passiveRuleset, String scenarioRuleset,
    Map<UUID, List<GadgetEffectRule>> passive, List<ScenarioEffectRule> scenario,
    Set<EffectKey> scenarioUtilities) {
  public record EffectKey(UUID gadgetId, String effectId) {}

  public GadgetRuleSnapshot {
    Objects.requireNonNull(version);
    passive = passive.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
        e -> List.copyOf(e.getValue())));
    scenario = List.copyOf(scenario);
    scenarioUtilities = Set.copyOf(scenarioUtilities);
  }

  public List<GadgetEffectRule> forGadget(UUID id) { return passive.getOrDefault(id, List.of()); }
  public ScenarioEffectRule scenarioRule(UUID gadget, String effect) {
    return scenario.stream().filter(r -> r.gadgetId().equals(gadget) && r.effectId().equals(effect)).findFirst().orElse(null);
  }
  public boolean unsupportedMayAffectStats(UUID gadget, String effect) {
    return !scenarioUtilities.contains(new EffectKey(gadget, effect));
  }
}
