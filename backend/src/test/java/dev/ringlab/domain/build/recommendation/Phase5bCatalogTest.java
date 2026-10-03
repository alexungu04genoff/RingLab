package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.build.GadgetPlate;
import dev.ringlab.domain.collection.CollectionExclusions;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.domain.gamedata.importing.*;
import dev.ringlab.importing.RuleFixtures;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.ringlab.domain.build.recommendation.RecommendationResult.Outcome.*;

/** Real canonical facts with a small bounded search, independent expected identities/costs. */
class Phase5bCatalogTest {
  private static final GameDataSet DATA = RuleFixtures.DATA;
  private static final UUID AMIGO = UUID.fromString("81000000-0000-4000-8000-000000000001");
  private static final UUID LOCOMOTIVE = UUID.fromString("82000000-0000-4000-8000-000000000001");
  private static final UUID SUBSTITUTE = PassiveGadgetRules.id(3);
  private static final BuildSelection EMPTY = new BuildSelection(null,null,null,null,List.of());
  private static final List<StatPriority> ORDER = List.of(StatPriority.HANDLING,StatPriority.BOOST,
      StatPriority.ACCELERATION,StatPriority.SPEED,StatPriority.POWER);
  private static final List<String> EXPECTED = List.of(
        "Air Trick Bounty|1|CONDITIONAL",
        "Item Attack Bounty|1|CONDITIONAL",
        "Runoff Bounty|1|CONDITIONAL",
        "Slipstream Bounty|1|CONDITIONAL",
        "Travel Ring Bounty|1|CONDITIONAL",
        "Morph Action Bounty|1|CONDITIONAL",
        "Perfect Charge Bounty|2|CONDITIONAL",
        "Dash Panel Bounty|2|CONDITIONAL",
        "Dash Panel Mini Bounty|1|CONDITIONAL",
        "Dash Panel Combo Bounty|2|CONDITIONAL",
        "200 Ring Limit|2|NON_STAT",
        "Ring Gain Mini Boost|1|CONDITIONAL",
        "Ring Gain Boost|2|CONDITIONAL",
        "Ring Range UP|1|NON_STAT",
        "Air Drift Mobility UP|1|CONDITIONAL",
        "Charge Jump Mobility UP|1|CONDITIONAL",
        "Air Trick Adept|1|NON_STAT",
        "Air Trick Expert|2|NON_STAT",
        "Lv1 Quick Charge|2|NON_STAT",
        "Lv2 Quick Charge|1|NON_STAT",
        "Lv3 Quick Charge|1|NON_STAT",
        "Technical Drift|2|CONDITIONAL",
        "Counter Quick Charge|3|CONDITIONAL",
        "Maximum Traction|1|CONDITIONAL",
        "Second Wind|1|CONDITIONAL",
        "Bumper Guard|1|CONDITIONAL",
        "Boost Starter|1|CONDITIONAL",
        "Double Boost Specialist|2|NON_STAT",
        "Dark Chao Starter|1|CONDITIONAL",
        "Giant Spiked Iron Ball|1|NON_STAT",
        "Short Fuse|1|NON_STAT",
        "Monster Truck Starter|3|CONDITIONAL",
        "Item Hoarder Kit|3|CONDITIONAL",
        "Damage Support Kit|3|CONDITIONAL",
        "Perfect Charge Kit|3|UNSUPPORTED",
        "Speedy Shortcut Kit|3|UNSUPPORTED",
        "Air Trick Action Kit|3|UNSUPPORTED",
        "4th Stage Charge Kit|3|UNSUPPORTED");

  @Test void identitiesCostsClassificationsAndClosedPassiveSetAreExplicit() {
    var names = DATA.gadgets().stream().map(CatalogRow::value).collect(Collectors.toMap(Gadget::name, Function.identity()));
    assertEquals(117, names.size());
    var newGadgets = DATA.gadgets().stream().map(CatalogRow::value)
        .filter(g -> g.id().toString().startsWith("84000000-")).toList();
    assertEquals(38, newGadgets.size());
    assertEquals(EXPECTED.stream().map(s -> s.split("\\|")[0]).collect(Collectors.toSet()),
        newGadgets.stream().map(Gadget::name).collect(Collectors.toSet()));
    var base = new BaseStatsBreakdown(PassiveGadgetRules.points(60,50,40,30,20),BaseStats.ZERO,BaseStats.ZERO);
    for (String entry : EXPECTED) {
      var fields = entry.split("\\|"); var gadget = names.get(fields[0]);
      assertEquals(Integer.valueOf(fields[1]), gadget.slotCost(), gadget.name());
      assertTrue(GadgetPlate.canFit(List.of(gadget.slotCost())));
      assertNull(gadget.imagePath());
      assertTrue(CollectionExclusions.NONE.gadgetAvailable(gadget.id()));
      var rule = RuleFixtures.snapshot().forGadget(gadget.id()).getFirst();
      assertEquals(fields[2],rule.kind().name(),gadget.name());
      assertFalse(PassiveGadgetRules.reviewedNumeric(rule));
      var result = PassiveStatsCalculator.calculate(RuleFixtures.snapshot(),base,"1.4.1",
          RacingType.HANDLING,RacingType.POWER,List.of(gadget),true);
      if (rule.kind() == GadgetEffectRule.Kind.UNSUPPORTED) {
        assertEquals(PassiveStatsResult.Coverage.PARTIAL,result.coverage());
        assertEquals(PassiveStatsResult.Status.UNSUPPORTED,result.effects().getFirst().status());
        // Presentation retains the known subtotal, explicitly PARTIAL; the solver must reject it.
        assertEquals(base.total(),result.adjusted());
      } else {
        assertEquals(PassiveStatsResult.Coverage.CALCULATED,result.coverage());
        assertEquals(base.total(),result.adjusted());
      }
    }
    assertEquals(23,DATA.ruleSets().getFirst().passive().stream()
        .filter(r -> PassiveGadgetRules.reviewedNumeric(r.value().rule())).count());
  }

  @Test void amigoHasOnlyReviewedCurrentStatsAndLocomotiveHasNoFabricatedStats() {
    var racer = DATA.racers().stream().map(CatalogRow::value).filter(r -> r.id().equals(AMIGO)).findFirst().orElseThrow();
    assertEquals("Amigo",racer.name()); assertEquals(RacingType.HANDLING,racer.racingType());
    assertFalse(DATA.racers().stream().anyMatch(r -> r.value().name().equals("Amigo Classic")));
    var machine = DATA.machines().stream().map(CatalogRow::value).filter(m -> m.id().equals(LOCOMOTIVE)).findFirst().orElseThrow();
    assertEquals("Locomotive de Amigo",machine.name()); assertEquals(RacingType.POWER,machine.racingType());
    var parts = DATA.parts().stream().map(CatalogRow::value).filter(p -> p.sourceMachineId().equals(LOCOMOTIVE)).toList();
    assertEquals(Set.of(MachinePartType.values()),parts.stream().map(MachinePart::type).collect(Collectors.toSet()));
    for (var snapshot : DATA.snapshots()) {
      var stats = snapshot.racers().stream().filter(r -> r.value().itemId().equals(AMIGO)).toList();
      if (snapshot.versionId().equals(RuleFixtures.snapshot().version().id()))
        assertEquals(List.of(PassiveGadgetRules.points(6,15,17,9,13)),stats.stream().map(r -> r.value().stats()).toList());
      else assertTrue(stats.isEmpty());
      for (var part : parts) assertFalse(snapshot.parts().stream().anyMatch(r -> r.value().itemId().equals(part.id())));
    }
    assertEquals(java.time.LocalDate.of(2025,12,18),DATA.versions().stream().map(CatalogRow::value)
        .filter(v -> v.version().equals("1.2.2")).findFirst().orElseThrow().releasedAt());
  }

  @Test void bothModesUseAmigoRespectOwnershipAndNeverTreatLocomotiveAsZero() {
    var catalog = catalog();
    var amigo = new BuildSelection(AMIGO,null,null,null,List.of());
    var unknownFront = catalog.parts().values().stream()
        .filter(p -> p.sourceMachineId().equals(LOCOMOTIVE) && p.type() == MachinePartType.FRONT).findFirst().orElseThrow();
    var locked = new BuildSelection(AMIGO,unknownFront.id(),null,null,List.of());
    for (var mode : RecommendationMode.values()) {
      var result = solve(catalog,request(mode,EMPTY,EMPTY,GadgetRecommendationScope.KEEP_CURRENT),CollectionExclusions.NONE);
      assertEquals(ESTABLISHED,result.outcome()); assertEquals(AMIGO,result.selection().racerId());
      for (UUID id : List.of(result.selection().frontPartId(),result.selection().rearPartId(),result.selection().tirePartId()))
        assertNotEquals(LOCOMOTIVE,catalog.parts().get(id).sourceMachineId());
      assertEquals(UNAVAILABLE,solve(catalog,request(mode,locked,locked,GadgetRecommendationScope.KEEP_CURRENT),CollectionExclusions.NONE).outcome());
      var excluded = new CollectionExclusions(Set.of(AMIGO),Set.of(),Set.of());
      assertEquals(NO_LEGAL_COMPLETION,solve(catalog,request(mode,EMPTY,EMPTY,GadgetRecommendationScope.KEEP_CURRENT),excluded).outcome());
      assertThrows(IllegalArgumentException.class,() -> solve(catalog,request(mode,amigo,amigo,GadgetRecommendationScope.KEEP_CURRENT),excluded));
      var machineExcluded = new CollectionExclusions(Set.of(),Set.of(LOCOMOTIVE),Set.of());
      assertThrows(IllegalArgumentException.class,() -> solve(catalog,request(mode,locked,locked,GadgetRecommendationScope.KEEP_CURRENT),machineExcluded));
    }
  }

  @Test void correctedPlateRejectsKeptOrLockedLegacySelectionsAndOptimizationFits() {
    var catalog = catalog();
    assertEquals(2,catalog.gadgets().get(SUBSTITUTE).slotCost());
    var twoCost = EXPECTED.stream().filter(s -> s.contains("|2|")).limit(2)
        .map(s -> DATA.gadgets().stream().map(CatalogRow::value).filter(g -> g.name().equals(s.split("\\|")[0])).findFirst().orElseThrow().id()).toList();
    var ids = List.of(SUBSTITUTE,twoCost.get(0),twoCost.get(1));
    assertTrue(GadgetPlate.canFit(List.of(1,2,2)));
    assertFalse(GadgetPlate.canFit(ids.stream().map(id -> catalog.gadgets().get(id).slotCost()).toList()));
    assertFalse(GadgetPlate.canFit(List.of(2,1,1,1,1,1)));
    var legacy = new BuildSelection(null,null,null,null,ids);
    for (var mode : RecommendationMode.values()) {
      assertTrue(assertThrows(IllegalArgumentException.class,() -> solve(catalog,
          request(mode,legacy,EMPTY,GadgetRecommendationScope.KEEP_CURRENT),CollectionExclusions.NONE)).getMessage().contains("Current gadgets"));
      assertTrue(assertThrows(IllegalArgumentException.class,() -> solve(catalog,
          request(mode,legacy,legacy,GadgetRecommendationScope.OPTIMIZE_UNLOCKED),CollectionExclusions.NONE)).getMessage().contains("Locked gadgets"));
      var lock = new BuildSelection(null,null,null,null,List.of(SUBSTITUTE));
      var optimized = solve(catalog,request(mode,legacy,lock,GadgetRecommendationScope.OPTIMIZE_UNLOCKED),CollectionExclusions.NONE);
      assertEquals(ESTABLISHED,optimized.outcome());
      assertTrue(optimized.selection().gadgetIds().contains(SUBSTITUTE));
      assertTrue(GadgetPlate.canFit(optimized.selection().gadgetIds().stream().map(id -> catalog.gadgets().get(id).slotCost()).toList()));
      assertEquals(ids,legacy.gadgetIds()); // Requests and saved identities are never repaired in place.
    }
  }

  @Test void newGadgetExclusionsAndUnsupportedLocksRemainHonest() {
    var catalog = catalog();
    for (var gadget : catalog.gadgets().values().stream().filter(g -> g.id().toString().startsWith("84000000-")).toList()) {
      var current = new BuildSelection(null,null,null,null,List.of(gadget.id()));
      for (var scope : GadgetRecommendationScope.values()) {
        var locked = scope == GadgetRecommendationScope.KEEP_CURRENT ? EMPTY : current;
        var excluded = new CollectionExclusions(Set.of(),Set.of(),Set.of(gadget.id()));
        assertThrows(IllegalArgumentException.class,() -> solve(catalog,
            request(RecommendationMode.STRICT,current,locked,scope),excluded));
        var result = solve(catalog,request(RecommendationMode.STRICT,current,locked,scope),CollectionExclusions.NONE);
        boolean unsupported = RuleFixtures.snapshot().forGadget(gadget.id()).getFirst().kind() == GadgetEffectRule.Kind.UNSUPPORTED;
        assertEquals(unsupported ? UNAVAILABLE : ESTABLISHED,result.outcome(),gadget.name());
        if (!unsupported) assertTrue(result.selection().gadgetIds().contains(gadget.id()));
      }
    }
  }

  private static RecommendationCatalog catalog() {
    var version = RuleFixtures.snapshot().version();
    var snapshot = DATA.snapshots().stream().filter(s -> s.versionId().equals(version.id())).findFirst().orElseThrow();
    var supported = DATA.machines().stream().map(CatalogRow::value)
        .filter(m -> m.racingType() == RacingType.POWER && !m.id().equals(LOCOMOTIVE)).findFirst().orElseThrow();
    var machines = DATA.machines().stream().map(CatalogRow::value)
        .filter(m -> m.id().equals(supported.id()) || m.id().equals(LOCOMOTIVE)).collect(Collectors.toMap(Machine::id,Function.identity()));
    return new RecommendationCatalog(version,
        DATA.racers().stream().map(CatalogRow::value).filter(r -> r.id().equals(AMIGO)).collect(Collectors.toMap(Racer::id,Function.identity())),
        machines,DATA.parts().stream().map(CatalogRow::value).filter(p -> machines.containsKey(p.sourceMachineId())).collect(Collectors.toMap(MachinePart::id,Function.identity())),
        DATA.gadgets().stream().map(CatalogRow::value).filter(g -> g.id().toString().startsWith("84000000-")
            || Set.of(SUBSTITUTE,PassiveGadgetRules.id(52),PassiveGadgetRules.id(54)).contains(g.id())).collect(Collectors.toMap(Gadget::id,Function.identity())),
        snapshot.racers().stream().map(CatalogRow::value).collect(Collectors.toMap(GameDataSet.StatRow::itemId,GameDataSet.StatRow::stats)),
        snapshot.parts().stream().map(CatalogRow::value).collect(Collectors.toMap(GameDataSet.StatRow::itemId,GameDataSet.StatRow::stats)),RuleFixtures.snapshot());
  }

  private static RecommendationRequest request(RecommendationMode mode,BuildSelection current,BuildSelection locked,GadgetRecommendationScope scope) {
    var losses = new EnumMap<StatPriority,BigDecimal>(StatPriority.class);
    ORDER.forEach(s -> losses.put(s,BigDecimal.ZERO));
    return new RecommendationRequest(RuleFixtures.snapshot().version().id(),RacingType.POWER,ORDER,current,locked,
        mode,mode == RecommendationMode.BALANCED ? new BalancedConfiguration(losses,List.of()) : null,scope);
  }
  private static RecommendationResult solve(RecommendationCatalog catalog,RecommendationRequest request,CollectionExclusions exclusions) {
    return new BuildRecommendationSolver(catalog,request,new BuildRecommendationSolver.Budget(100_000,Duration.ofSeconds(2)),exclusions).solve();
  }
}
