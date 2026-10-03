package dev.ringlab.application;

import dev.ringlab.application.gamedata.*;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.domain.gamedata.importing.*;
import dev.ringlab.domain.gamedata.importing.GameDataRuleSet.*;
import dev.ringlab.importing.*;
import dev.ringlab.port.out.GameDataImportRepository;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static dev.ringlab.importing.ImportFixture.row;
import static org.junit.jupiter.api.Assertions.*;

class GameDataRuleImportTest {
  private final GameDataSet canonical = RuleFixtures.DATA;
  private final Memory repository = new Memory(canonical);
  private final GameDataImportService service = new GameDataImportService(repository);

  @Test void firstRuleSnapshotOnAnUnreviewedVersionIsAnExplicitAddition() {
    var edit = new ImportFixture.Edit(canonical); edit.ruleSets.clear(); repository.current = edit.build();
    var plan = service.plan(canonical);
    assertTrue(plan.safe()); assertEquals(384, plan.inserts());
    assertTrue(plan.changes().getFirst().contains("99 passive, 10 scenario, 274 sources"));
    assertFalse(repository.wrote);
    assertEquals(plan, service.apply(canonical, plan.approvalToken())); assertTrue(repository.wrote);
  }
  @Test void anExactSnapshotIsANoOpAndReorderingDoesNotChangeTheApproval() {
    var first = service.plan(canonical); var original = canonical.ruleSets().getFirst();
    var passive = new ArrayList<>(original.passive()); var scenario = new ArrayList<>(original.scenario());
    var sources = new ArrayList<>(original.sources()); Collections.reverse(passive); Collections.reverse(scenario); Collections.reverse(sources);
    var reordered = with(new GameDataRuleSet(original.metadata(), passive, scenario, sources));
    var second = service.plan(reordered);
    assertEquals(0, second.inserts()); assertEquals(0, second.updateCount());
    assertEquals(first.approvalToken(), second.approvalToken()); assertEquals(first.changes(), second.changes());
  }
  @Test void publishedRulesetIdentityCannotChangeAndItsRemovalIsUnsafe() {
    var r = canonical.ruleSets().getFirst();
    reject(with(new GameDataRuleSet(row(new Metadata(r.versionId(), "new-passive-id", "new-scenario-id")), r.passive(), r.scenario(), r.sources())), "passive_ruleset");
    var removed = new ImportFixture.Edit(canonical); removed.ruleSets.clear(); reject(removed.build(), "snapshot omitted");
  }
  @Test void publishedPassiveLabelsExplanationsAndNumericValuesAreImmutable() {
    var set = canonical.ruleSets().getFirst(); var rows = new ArrayList<>(set.passive());
    int index = java.util.stream.IntStream.range(0, rows.size()).filter(i -> rows.get(i).value().rule().kind() == GadgetEffectRule.Kind.PASSIVE).findFirst().orElseThrow();
    var f = rows.get(index).value(); var r = f.rule();
    var changed = new GadgetEffectRule(r.gadgetId(), r.effectId(), "Changed label", r.kind(), r.subject(), r.requiredType(),
        PassiveGadgetRules.points(123, 0, 0, 0, 0), r.nonMatching(), "Changed explanation", List.of(), r.stackingGroup());
    rows.set(index, row(new PassiveFact(f.position(), changed, f.scenarioStatPotential())));
    reject(with(new GameDataRuleSet(set.metadata(), rows, set.scenario(), set.sources())), "matching_speed");
  }
  @Test void publishedConditionsAdjustmentsAndSourcesCannotBeChanged() {
    var set = canonical.ruleSets().getFirst(); var scenario = new ArrayList<>(set.scenario()); var f = scenario.getFirst().value(); var r = f.rule();
    scenario.set(0, row(new ScenarioFact(f.position(), new ScenarioEffectRule(r.gadgetId(), r.effectId(), r.label(),
        ScenarioEffectRule.Condition.LAP_THREE, BaseStats.ZERO, r.explanation(), List.of()))));
    reject(with(new GameDataRuleSet(set.metadata(), set.passive(), scenario, set.sources())), "condition");
    var sources = new ArrayList<>(set.sources()); var s = sources.getFirst().value();
    sources.set(0, row(new Source(s.effectType(), s.gadgetId(), s.effectId(), s.position(), "https://example.test/changed")));
    reject(with(new GameDataRuleSet(set.metadata(), set.passive(), set.scenario(), sources)), "url");
  }
  @Test void removingOrAddingPublishedScenarioRowsIsRejectedEvenWhenReferencesRemainValid() {
    var set = canonical.ruleSets().getFirst(); var scenario = new ArrayList<>(set.scenario()); var removed = scenario.removeLast().value().rule();
    var sources = set.sources().stream().filter(r -> !(r.value().effectType() == EffectType.SCENARIO
        && r.value().gadgetId().equals(removed.gadgetId()) && r.value().effectId().equals(removed.effectId()))).toList();
    var reduced = with(new GameDataRuleSet(set.metadata(), set.passive(), scenario, sources));
    reject(reduced, "row omitted");
    repository.current = reduced; reject(canonical, "Cannot add rows");
  }
  @Test void changingFactsInvalidatesApprovalBeforeTheRepositoryWrites() {
    var oldToken = service.plan(canonical).approvalToken();
    var edit = new ImportFixture.Edit(canonical); edit.ruleSets.clear(); repository.current = edit.build();
    assertThrows(ImportValidationException.class, () -> service.apply(canonical, oldToken)); assertFalse(repository.wrote);
  }
  @Test void arbitraryCsvStackingLabelsCannotGrantPermissions() {
    var original = RuleFixtures.snapshot().forGadget(PassiveGadgetRules.id(52)).getFirst();
    var forged = new GadgetEffectRule(PassiveGadgetRules.id(999), "stats", original.label(), original.kind(), original.subject(),
        original.requiredType(), original.matching(), original.nonMatching(), original.explanation(), original.sources(), "machine-tuners");
    assertFalse(PassiveGadgetRules.reviewedNumeric(forged));
    assertFalse(PassiveGadgetRules.reviewedNumeric(new GadgetEffectRule(original.gadgetId(), "other-0", original.label(),
        original.kind(), original.subject(), original.requiredType(), original.matching(), original.nonMatching(), original.explanation(), List.of(), "machine-tuners")));
  }
  @Test void reviewedRuntimeSnapshotMustExist() {
    var loader = new ReviewedGadgetRules(version -> Optional.empty());
    assertThrows(IllegalStateException.class, loader::snapshot);
  }
  private GameDataSet with(GameDataRuleSet rules) {
    var edit = new ImportFixture.Edit(canonical); edit.ruleSets.clear(); edit.ruleSets.add(rules); return edit.build();
  }
  private void reject(GameDataSet changed, String diagnostic) {
    var plan = service.plan(changed); assertFalse(plan.safe());
    assertTrue(plan.unsafeChanges().toString().contains(diagnostic), plan.unsafeChanges().toString());
    assertThrows(ImportValidationException.class, () -> service.apply(changed, plan.approvalToken())); assertFalse(repository.wrote);
  }
  private static class Memory implements GameDataImportRepository {
    GameDataSet current; boolean wrote;
    Memory(GameDataSet current) { this.current = current; }
    public GameDataSet read() { return current; }
    public GameDataImportPlan apply(Function<GameDataSet, GameDataImportPlan> prepare) {
      var plan = prepare.apply(current); wrote = true; return plan;
    }
  }
}
