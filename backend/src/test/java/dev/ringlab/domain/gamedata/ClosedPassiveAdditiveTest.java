package dev.ringlab.domain.gamedata;

import dev.ringlab.domain.build.GadgetPlate;
import dev.ringlab.importing.RuleFixtures;
import java.util.*;
import org.junit.jupiter.api.Test;
import static dev.ringlab.domain.gamedata.PassiveGadgetRules.*;
import static org.junit.jupiter.api.Assertions.*;

class ClosedPassiveAdditiveTest {
  private static final GadgetRuleSnapshot RULES = RuleFixtures.snapshot();
  private static final BaseStatsBreakdown BASE = new BaseStatsBreakdown(BaseStats.ZERO,BaseStats.ZERO,BaseStats.ZERO);
  private static final List<Gadget> NUMERIC = RuleFixtures.DATA.gadgets().stream().map(row -> row.value())
      .filter(g -> RULES.forGadget(g.id()).stream().anyMatch(r -> r.kind() == GadgetEffectRule.Kind.PASSIVE)).toList();

  @Test void everyLegalSubsetAcrossAllTwentyFiveTypePairsEqualsIndependentSignedSum() {
    assertEquals(23,NUMERIC.size());
    assertTrue(NUMERIC.stream().allMatch(g -> RULES.forGadget(g.id()).stream()
        .filter(r -> r.kind() == GadgetEffectRule.Kind.PASSIVE).allMatch(PassiveGadgetRules::reviewedNumeric)));
    long subsets = subsets(0,new ArrayList<>());
    assertTrue(subsets > 1000);
    System.out.println("Closed passive additive subsets: " + subsets + "; type comparisons: " + subsets * 25);
  }

  private long subsets(int start, List<Gadget> selected) {
    long count = 1;
    for (var racer : RacingType.values()) for (var machine : RacingType.values()) {
      var vectors = new ArrayList<BaseStats>(); vectors.add(BaseStats.ZERO);
      for (var gadget : selected) for (var rule : RULES.forGadget(gadget.id())) {
        if (rule.kind() != GadgetEffectRule.Kind.PASSIVE) continue;
        var type = rule.subject() == GadgetEffectRule.Subject.RACER ? racer : machine;
        vectors.add(rule.subject() == GadgetEffectRule.Subject.ANY || type == rule.requiredType()
            ? rule.matching() : rule.nonMatching());
      }
      var result = PassiveStatsCalculator.calculate(RULES,BASE,VERSION,racer,machine,selected,true);
      assertEquals(PassiveStatsResult.Coverage.CALCULATED,result.coverage());
      assertEquals(BaseStats.sum(vectors),result.adjustments());
      assertEquals(result.adjustments(),PassiveStatsCalculator.calculate(RULES,BASE,VERSION,racer,machine,selected.reversed(),true).adjustments());
    }
    if (selected.size() == 6) return count;
    for (int i = start; i < NUMERIC.size(); i++) {
      selected.add(NUMERIC.get(i));
      if (GadgetPlate.canFit(selected.stream().map(Gadget::slotCost).toList())) count += subsets(i+1,selected);
      selected.removeLast();
    }
    return count;
  }

  @Test void representativeOverlapsAndPenaltiesRemainExact() {
    assertVector(points(-6,0,0,-2,60),RacingType.BOOST,60,61,62);
    assertVector(points(-4,0,0,0,40),RacingType.BOOST,60,62);
    assertVector(points(0,0,23,-4,0),RacingType.HANDLING,50,56);
    assertVector(points(0,0,28,4,0),RacingType.HANDLING,23,56);
    assertVector(points(-4,0,0,-5,27),RacingType.BOOST,11,60);
    var doubleDown = NUMERIC.stream().filter(g -> g.name().equals("Double Down")).findFirst().orElseThrow();
    var tuner = NUMERIC.stream().filter(g -> g.id().equals(id(52))).findFirst().orElseThrow();
    assertEquals(points(10,-14,-10,-10,-10),PassiveStatsCalculator.calculate(RULES,BASE,VERSION,
        RacingType.SPEED,RacingType.SPEED,List.of(doubleDown,tuner),true).adjustments());
  }
  private void assertVector(BaseStats expected,RacingType machine,int... ids) {
    var gadgets = Arrays.stream(ids).mapToObj(n -> NUMERIC.stream().filter(g -> g.id().equals(id(n))).findFirst().orElseThrow()).toList();
    assertEquals(expected,PassiveStatsCalculator.calculate(RULES,BASE,VERSION,RacingType.BOOST,machine,gadgets,true).adjustments());
  }

  @Test void newPassiveIdentityOrExtraEffectCannotInheritPermissionFromCsv() {
    for (var identity : List.of(id(999),id(52))) {
      var rule = new GadgetEffectRule(identity,identity.equals(id(52)) ? "new-effect" : "stats","Future",
          GadgetEffectRule.Kind.PASSIVE,GadgetEffectRule.Subject.ANY,null,points(999,0,0,0,0),ZERO,"Future",List.of(),"machine-tuners");
      var snapshot = new GadgetRuleSnapshot(RULES.version(),RULES.passiveRuleset(),RULES.scenarioRuleset(),Map.of(identity,List.of(rule)),List.of(),Set.of());
      var result = PassiveStatsCalculator.calculate(snapshot,BASE,VERSION,RacingType.SPEED,RacingType.SPEED,
          List.of(new Gadget(identity,"Future",null,1,null)),true);
      assertEquals(PassiveStatsResult.Coverage.PARTIAL,result.coverage());
      assertEquals(PassiveStatsResult.Status.UNSUPPORTED,result.effects().getFirst().status());
      assertEquals(ZERO,result.adjustments());
    }
  }
}
