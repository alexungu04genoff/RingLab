package dev.ringlab.domain.gamedata.importing;

import dev.ringlab.domain.gamedata.*;
import dev.ringlab.domain.gamedata.GadgetRuleSnapshot.EffectKey;
import java.util.*;
import java.util.stream.Collectors;

/** Source-aware import rows; URLs remain separate relational rows until snapshot assembly. */
public record GameDataRuleSet(CatalogRow<Metadata> metadata, List<CatalogRow<PassiveFact>> passive,
    List<CatalogRow<ScenarioFact>> scenario, List<CatalogRow<Source>> sources) {
  public enum EffectType { PASSIVE, SCENARIO }
  public record Metadata(UUID versionId, String passiveRuleset, String scenarioRuleset) {}
  public record PassiveFact(int position, GadgetEffectRule rule, Boolean scenarioStatPotential) {}
  public record ScenarioFact(int position, ScenarioEffectRule rule) {}
  public record Source(EffectType effectType, UUID gadgetId, String effectId, int position, String url) {}

  public GameDataRuleSet {
    passive = List.copyOf(passive);
    scenario = List.copyOf(scenario);
    sources = List.copyOf(sources);
  }
  public UUID versionId() { return metadata.value().versionId(); }
  public int rowCount() { return 1 + passive.size() + scenario.size() + sources.size(); }

  public GadgetRuleSnapshot snapshot(GameVersion version) {
    if (!version.id().equals(versionId())) throw new IllegalArgumentException("Rule snapshot version mismatch");
    var effects = passive.stream().map(CatalogRow::value).sorted(Comparator.comparingInt(PassiveFact::position))
        .map(f -> {
          var r = f.rule();
          return new GadgetEffectRule(r.gadgetId(), r.effectId(), r.label(), r.kind(), r.subject(), r.requiredType(),
              r.matching(), r.nonMatching(), r.explanation(), urls(EffectType.PASSIVE, r.gadgetId(), r.effectId()), r.stackingGroup());
        }).collect(Collectors.groupingBy(GadgetEffectRule::gadgetId));
    var scenarios = scenario.stream().map(CatalogRow::value).sorted(Comparator.comparingInt(ScenarioFact::position))
        .map(f -> {
          var r = f.rule();
          return new ScenarioEffectRule(r.gadgetId(), r.effectId(), r.label(), r.condition(), r.adjustment(),
              r.explanation(), urls(EffectType.SCENARIO, r.gadgetId(), r.effectId()));
        }).toList();
    var utilities = passive.stream().map(CatalogRow::value).filter(f -> Boolean.FALSE.equals(f.scenarioStatPotential()))
        .map(f -> new EffectKey(f.rule().gadgetId(), f.rule().effectId())).collect(Collectors.toSet());
    return new GadgetRuleSnapshot(version, metadata.value().passiveRuleset(), metadata.value().scenarioRuleset(), effects, scenarios, utilities);
  }

  private List<String> urls(EffectType type, UUID gadget, String effect) {
    return sources.stream().map(CatalogRow::value).filter(s -> s.effectType() == type && s.gadgetId().equals(gadget) && s.effectId().equals(effect))
        .sorted(Comparator.comparingInt(Source::position)).map(Source::url).toList();
  }
}
