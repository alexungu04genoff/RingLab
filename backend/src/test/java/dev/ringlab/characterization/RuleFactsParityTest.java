package dev.ringlab.characterization;

import dev.ringlab.adapter.in.catalog.GameDataCsvReader;
import dev.ringlab.domain.gamedata.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Frozen main 5655220 oracle. Never update its tables/calculators to match new imported facts. */
class RuleFactsParityTest {
  private static final List<Gadget> GADGETS = new GameDataCsvReader().read(Path.of("../game-data"))
      .gadgets().stream().map(r -> r.value())
      // Phase 5B adds identities outside this frozen 79-gadget characterization.
      // Phase5bCatalogTest independently checks every new classification.
      .filter(g -> !g.id().toString().startsWith("84000000-")).toList();
  private static final BaseStats KNOWN = PassiveGadgetRules.points(60, 50, 40, 30, 20);
  private static final List<RacingType> TYPES = Arrays.asList(null, RacingType.SPEED,
      RacingType.ACCELERATION, RacingType.HANDLING, RacingType.POWER, RacingType.BOOST);

  @Test void complete_rule_metadata_matches_the_frozen_tables() {
    for (var gadget : GADGETS)
      assertEquals(PassiveGadgetRules.forGadget(gadget.id()),
          dev.ringlab.importing.RuleFixtures.snapshot().forGadget(gadget.id()), gadget.name());
    assertEquals(ScenarioGadgetRules.all(), dev.ringlab.importing.RuleFixtures.snapshot().scenario());
    for (var gadget : GADGETS) for (var rule : PassiveGadgetRules.forGadget(gadget.id()))
      if (rule.kind() == GadgetEffectRule.Kind.CONDITIONAL)
        assertEquals(ScenarioGadgetRules.unsupportedMayAffectStats(gadget.id(), rule.effectId()),
            dev.ringlab.importing.RuleFixtures.snapshot().unsupportedMayAffectStats(gadget.id(), rule.effectId()));
  }

  @Test void every_gadget_and_racer_machine_type_including_unknown_selections_has_identical_passive_results() {
    var all = new ArrayList<>(GADGETS);
    all.add(new Gadget(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"), "Unreviewed", null, 1, null));
    for (var gadget : all) for (var racer : TYPES) for (var machine : TYPES) {
      passive(KNOWN, "1.4.1", racer, machine, List.of(gadget), true);
      passive(BaseStats.UNKNOWN, "1.4.1", racer, machine, List.of(gadget), true);
    }
    for (var gadget : all) for (String version : Arrays.asList(null, "1.3.1", "future"))
      passive(KNOWN, version, RacingType.SPEED, RacingType.SPEED, List.of(gadget), true);
    for (var gadget : all) passive(KNOWN, "1.4.1", RacingType.SPEED, RacingType.SPEED, List.of(gadget), false);
    passive(KNOWN, "1.4.1", null, null, List.of(), true);
  }

  // Overlapping passive pairs deliberately changed in additive-v1. ClosedPassiveAdditiveTest
  // checks every legal subset against independent canonical-vector sums; frozen tables stay intact.

  @Test void every_scenario_condition_and_utility_classification_matches_for_true_false_and_unknown() {
    for (var gadget : GADGETS) for (var context : contexts())
      scenario(KNOWN, "1.4.1", List.of(gadget), true, context);
    for (var gadget : GADGETS) {
      scenario(BaseStats.UNKNOWN, "1.4.1", List.of(gadget), true, ScenarioContext.UNSPECIFIED);
      scenario(KNOWN, "1.3.1", List.of(gadget), true, ScenarioContext.UNSPECIFIED);
      scenario(KNOWN, "1.4.1", List.of(gadget), false, ScenarioContext.UNSPECIFIED);
    }
  }

  @Test void scenario_pairs_preserve_assumption_and_passive_interaction_rejections_in_both_orders() {
    var ids = new HashSet<UUID>();
    for (int n : new int[] {2,4,5,15,17,18,34,45,46,47,48,52,55}) ids.add(PassiveGadgetRules.id(n));
    ids.add(ScenarioGadgetRules.RING_ENGINE);
    ids.add(ScenarioGadgetRules.HYPER_RING_ENGINE);
    var selected = GADGETS.stream().filter(g -> ids.contains(g.id())).toList();
    for (var first : selected) for (var second : selected) for (var context : contexts())
      scenario(KNOWN, "1.4.1", List.of(first, second), true, context);
  }

  private static void passive(BaseStats total, String version, RacingType racer, RacingType machine,
      List<Gadget> gadgets, boolean valid) {
    var base = new BaseStatsBreakdown(total, BaseStats.ZERO, BaseStats.ZERO);
    var expected = PassiveStatsCalculator.calculate(base, version, racer, machine, gadgets, valid);
    var actual = dev.ringlab.domain.gamedata.PassiveStatsCalculator.calculate(dev.ringlab.importing.RuleFixtures.snapshot(), base, version, racer, machine, gadgets, valid);
    assertEquals(new PassiveStatsResult(expected.base(),expected.adjustments(),expected.adjusted(),expected.coverage(),
            actual.ruleset(),expected.supportedVersion(),actual.note(),expected.effects()), actual,
        () -> gadgets.stream().map(Gadget::name).toList() + " " + version + " " + racer + "/" + machine);
  }

  private static void scenario(BaseStats total, String version, List<Gadget> gadgets, boolean valid, ScenarioContext context) {
    var base = new BaseStatsBreakdown(total, BaseStats.ZERO, BaseStats.ZERO);
    var newPassive = dev.ringlab.domain.gamedata.PassiveStatsCalculator.calculate(dev.ringlab.importing.RuleFixtures.snapshot(), base, version,
        RacingType.ACCELERATION, RacingType.ACCELERATION, gadgets, valid);
    // Feed both scenario policies the same passive result: only the passive model changed.
    assertEquals(ScenarioStatsCalculator.calculate(newPassive, context),
        dev.ringlab.domain.gamedata.ScenarioStatsCalculator.calculate(dev.ringlab.importing.RuleFixtures.snapshot(), newPassive, context),
        () -> gadgets.stream().map(Gadget::name).toList() + " " + context);
  }

  private static List<ScenarioContext> contexts() {
    var result = new ArrayList<ScenarioContext>();
    result.add(ScenarioContext.UNSPECIFIED);
    for (Integer lap : Arrays.asList(null, 1, 2, 3))
      for (var form : Arrays.asList(null, ScenarioContext.VehicleForm.NORMAL, ScenarioContext.VehicleForm.WATER, ScenarioContext.VehicleForm.FLIGHT))
        result.add(new ScenarioContext(lap, form, null, null, null));
    for (Integer rings : Arrays.asList(null, 0, 1, 999))
      for (Boolean landing : Arrays.asList(null, false, true))
        for (Integer distance : Arrays.asList(null, 0, 300, 301))
          result.add(new ScenarioContext(1, ScenarioContext.VehicleForm.WATER, rings, landing, distance));
    return result;
  }
}
