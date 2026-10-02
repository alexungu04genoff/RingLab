package dev.ringlab.domain.gamedata;

import static dev.ringlab.domain.gamedata.PassiveGadgetRules.id;
import static dev.ringlab.domain.gamedata.PassiveGadgetRules.points;
import static dev.ringlab.domain.gamedata.RacingType.*;
import static dev.ringlab.domain.gamedata.PassiveStatsResult.Status.*;
import static dev.ringlab.domain.gamedata.PassiveStatsResult.Coverage.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class PassiveStatsCalculatorTest {
  private static final BaseStats TOTAL = new BaseStats(new BigDecimal("85.25"),new BigDecimal("30.75"),
      new BigDecimal("42.125"),new BigDecimal("12"),new BigDecimal("54.5"));
  private static final BaseStatsBreakdown BASE = new BaseStatsBreakdown(TOTAL,points(20,20,20,20,20),points(65,10,22,-8,34));
  private Gadget gadget(int n) { return new Gadget(id(n),"Gadget " + n,null,1,null); }
  private PassiveStatsResult calculate(RacingType racer, RacingType machine, int... ids) {
    return PassiveStatsCalculator.calculate(dev.ringlab.importing.RuleFixtures.snapshot(), BASE,"1.4.1",racer,machine,Arrays.stream(ids).mapToObj(this::gadget).toList(),true);
  }

  @Test void noGadgetsPreservesTheExactBaseAndPrecision() {
    var result = calculate(BOOST,POWER);
    assertEquals(BASE,result.base()); assertEquals(TOTAL,result.adjusted());
    assertEquals(points(0,0,0,0,0),result.adjustments()); assertEquals(CALCULATED,result.coverage());
    assertTrue(result.effects().isEmpty());
  }

  @Test void driftSpinnerAndInventorySwapDoNotBlockTheFiveStatBaseline() {
    var inventorySwap = new Gadget(UUID.fromString("174ea0a3-43bb-5001-bbd4-8b598482fe59"), "Inventory Swap", null, 3, null);
    var spinner = new Gadget(id(22), "Drift Spinner Kit", null, 3, null);
    var result = PassiveStatsCalculator.calculate(dev.ringlab.importing.RuleFixtures.snapshot(), BASE, "1.4.1", BOOST, ACCELERATION,
        List.of(spinner, inventorySwap), true);
    assertEquals(CALCULATED, result.coverage());
    assertEquals(TOTAL, result.adjusted());
    assertEquals(points(0,0,0,0,0), result.adjustments());
    assertEquals(2, result.effects().size());
    assertTrue(result.effects().stream().allMatch(effect -> effect.status() == NON_STAT));
  }

  @Test void all56ZeroPassiveGadgetsWorkAcrossEveryRacerAndMachineType() {
    var utilityIds = new ArrayList<UUID>();
    // Catalog identities reviewed against the Gadget List; includes conditional utility
    // and All-Rounder's transformation-only stats, never an active race-state bonus.
    for (int n : new int[] {1,2,3,4,5,6,7,8,9,10,14,15,17,18,19,21,22,27,28,29,30,31,32,33,35,36,39,40,41,42,43,44,45,46,47,48,51})
      utilityIds.add(id(n));
    for (String value : List.of("82005d0f-575b-5350-934f-fb108d306a21", "966f8792-a6af-576f-885c-697a33064497",
        "83284989-3174-5cf8-a5ef-3d443b559b55", "ae3d98e8-321c-5e19-b442-a2da09463467",
        "b3002fe1-32a2-5a06-8c07-64d0511811ad", "97147799-cf1d-5f50-b133-3ff6091574d3",
        "c857021b-d746-5d73-b54d-ccd594064c80", "f3b3dd31-9912-5a84-a8db-a76d1f93dcbe",
        "238359e7-ba2a-582d-bc82-6ab1c14b0284", "180a09fc-0034-5f56-be95-2938ce9ed61d",
        "84c606c3-0540-5a3e-bb92-27185533a89b", "3130026a-75cc-50c9-94ba-4748263acbfd",
        "b5f42975-23b3-5a1d-9173-c18d2e584244", "175c2797-0be5-571a-99ac-af4bfdbf58a4",
        "9735d3ab-d53b-52a1-a80d-ed0cb89429c2", "f8a1e5bd-b342-5a88-87e1-908adb2e165f",
        "174ea0a3-43bb-5001-bbd4-8b598482fe59", "5a000a58-7d7a-581c-80e3-6ae8661215b7",
        "182bdfa8-44d3-5de8-941b-d525381dd0a3"))
      utilityIds.add(UUID.fromString(value));
    assertEquals(56,new HashSet<>(utilityIds).size());
    for (var utilityId : utilityIds) {
      var utility = new Gadget(utilityId, "Utility", null, 3, null);
      var result = PassiveStatsCalculator.calculate(dev.ringlab.importing.RuleFixtures.snapshot(), BASE,"1.4.1",null,null,List.of(utility),true);
      assertEquals(CALCULATED,result.coverage(),utilityId.toString());
      assertEquals(TOTAL,result.adjusted());
      assertEquals(BaseStats.ZERO,result.adjustments());
      assertFalse(result.effects().isEmpty());
      assertTrue(result.effects().stream().allMatch(effect -> effect.status() == CONDITIONAL || effect.status() == NON_STAT));

      for (var racer : RacingType.values()) for (var machine : RacingType.values()) {
        var context = utilityId + " racer=" + racer + " machine=" + machine;
        var alone = PassiveStatsCalculator.calculate(dev.ringlab.importing.RuleFixtures.snapshot(), BASE,"1.4.1",racer,machine,List.of(utility),true);
        assertEquals(CALCULATED,alone.coverage(),context);
        assertEquals(TOTAL,alone.adjusted(),context);
        assertEquals(BaseStats.ZERO,alone.adjustments(),context);
        var withTuner = PassiveStatsCalculator.calculate(dev.ringlab.importing.RuleFixtures.snapshot(), BASE,"1.4.1",racer,machine,List.of(utility,gadget(60)),true);
        var expected = machine == BOOST ? points(-4,0,0,0,20) : points(0,0,0,0,8);
        assertEquals(CALCULATED,withTuner.coverage(),context);
        assertEquals(expected,withTuner.adjustments(),context);
        assertEquals(BaseStats.sum(List.of(TOTAL,expected)),withTuner.adjusted(),context);
      }
    }
  }

  @Test void charmyAncientThroneRemixHasACompleteBaselineWithItsThreeRaceTimeGadgets() {
    var total = points(29,66,61,35,49);
    var base = new BaseStatsBreakdown(total,points(14,18,10,11,7),points(15,48,51,24,42));
    var gadgets = List.of(new Gadget(id(7),"Damage Evolution",null,1,null),
        new Gadget(id(4),"Invincible Finish",null,3,null), new Gadget(id(2),"Perfect Landing",null,1,null));
    var result = PassiveStatsCalculator.calculate(dev.ringlab.importing.RuleFixtures.snapshot(), base,"1.4.1",ACCELERATION,HANDLING,gadgets,true);
    assertEquals(CALCULATED,result.coverage());
    assertEquals(total,result.adjusted());
    assertEquals(BaseStats.ZERO,result.adjustments());
    assertTrue(result.effects().stream().allMatch(effect -> effect.status() == CONDITIONAL));
  }

  @Test void everyTunerUsesMachineTypeIncludingAllVerifiedPenaltiesAndNonMatchingValues() {
    // Independent fixtures transcribed from the original Gadget List (1.4), corroborated by atwiki /45.
    int[][] expected = {{20,-4,0,0,0},{20,-2,0,0,-2},{0,20,-4,0,0},{0,20,-2,0,-2},
        {0,0,20,-4,0},{-2,0,20,-2,0},{0,-4,0,20,0},{0,-2,-2,20,0},{-4,0,0,0,20},{-2,0,0,-2,20}};
    var types = new RacingType[] {SPEED,ACCELERATION,HANDLING,POWER,BOOST};
    for (int index=0; index<10; index++) {
      int[] e = expected[index];
      var type = types[index/2];
      int[] nonMatch = new int[5]; nonMatch[index/2]=8;
      for (var racer : types) for (var machine : types) {
        var result = calculate(racer,machine,52+index);
        var expectedStats = machine == type ? points(e[0],e[1],e[2],e[3],e[4])
            : points(nonMatch[0],nonMatch[1],nonMatch[2],nonMatch[3],nonMatch[4]);
        assertEquals(CALCULATED,result.coverage());
        assertEquals(expectedStats,result.adjustments(),"tuner=" + index + " racer=" + racer + " machine=" + machine);
      }
    }
  }

  @Test void machineKitsHaveTwentyForMatchingMachineAndNoOldPenalty() {
    int[] ids = {49,34,37,38,62};
    var types = new RacingType[] {SPEED,ACCELERATION,HANDLING,POWER,BOOST};
    for (int i=0;i<ids.length;i++) {
      int[] expected = new int[5];expected[i]=20;
      for (var racer : types) for (var machine : types) {
        var result = calculate(racer,machine,ids[i]);
        var expectedStats = machine == types[i] ? points(expected[0],expected[1],expected[2],expected[3],expected[4]) : BaseStats.ZERO;
        assertEquals(CALCULATED,result.coverage());
        assertEquals(expectedStats,result.adjustments(),"kit=" + ids[i] + " racer=" + racer + " machine=" + machine);
        assertEquals(machine == types[i] ? APPLIED : NOT_MATCHED,result.effects().getFirst().status());
        assertTrue(result.effects().stream().anyMatch(effect -> effect.status() == CONDITIONAL));
      }
    }
  }

  @Test void characterKitsCheckRacerNotMachineAndIncludePenalties() {
    int[] ids={20,16,13,12,11};
    var types=new RacingType[]{SPEED,ACCELERATION,HANDLING,POWER,BOOST};
    int[][] expected={{7,-5,0,0,0},{0,7,0,0,-5},{-5,0,7,0,0},{0,0,-5,7,0},{0,0,0,-5,7}};
    for(int i=0;i<ids.length;i++) {
      int[] e=expected[i];
      for (var racer : types) for (var machine : types) {
        var result = calculate(racer,machine,ids[i]);
        var expectedStats = racer == types[i] ? points(e[0],e[1],e[2],e[3],e[4]) : BaseStats.ZERO;
        assertEquals(CALCULATED,result.coverage());
        assertEquals(expectedStats,result.adjustments(),"kit=" + ids[i] + " racer=" + racer + " machine=" + machine);
        assertEquals(racer == types[i] ? APPLIED : NOT_MATCHED,result.effects().getFirst().status());
      }
    }
    assertEquals(points(0,0,0,-5,7),calculate(BOOST,POWER,11).adjustments());
    // The ring-theft variant does not affect the kit's known +7 Handling / -5 Speed.
    assertEquals(CONDITIONAL,calculate(HANDLING,POWER,13).effects().getLast().status());
  }

  @Test void sameTypeTunersStackWithPenaltiesWithoutClampingOrRounding() {
    var result=calculate(BOOST,SPEED,52,53);
    assertEquals(points(40,-6,0,0,-2),result.adjustments());
    assertEquals(new BigDecimal("125.25"),result.adjusted().speed());
    assertEquals(new BigDecimal("24.75"),result.adjusted().acceleration());
    assertEquals(new BigDecimal("42.125"),result.adjusted().handling());
    assertEquals(result.adjusted(),calculate(BOOST,SPEED,53,52).adjusted());
    assertEquals(points(16,0,0,0,0),calculate(SPEED,BOOST,52,53).adjustments());
  }

  @Test void mixedTunersOffsetPenaltiesAndKeepEveryIndependentBonus() {
    var base = new BaseStatsBreakdown(points(62,30,43,62,43), points(11,9,7,20,13), points(51,21,36,42,30));
    var gadgets = List.of(gadget(54), gadget(55), gadget(52), gadget(53), gadget(58), gadget(59));
    var result = PassiveStatsCalculator.calculate(dev.ringlab.importing.RuleFixtures.snapshot(), base,"1.4.1",POWER,SPEED,gadgets,true);
    assertEquals(CALCULATED,result.coverage());
    assertEquals(points(40,10,0,16,-2),result.adjustments());
    assertEquals(points(102,40,43,78,41),result.adjusted());
    assertTrue(result.effects().stream().allMatch(effect -> effect.status() == APPLIED));
    assertEquals(result.adjusted(),PassiveStatsCalculator.calculate(dev.ringlab.importing.RuleFixtures.snapshot(), base,"1.4.1",POWER,SPEED,gadgets.reversed(),true).adjusted());
    // The same gadgets on an Acceleration machine use different bonuses and penalties.
    var acceleration = PassiveStatsCalculator.calculate(dev.ringlab.importing.RuleFixtures.snapshot(), base,"1.4.1",POWER,ACCELERATION,gadgets,true);
    assertEquals(CALCULATED,acceleration.coverage());
    assertEquals(points(16,40,-6,16,-2),acceleration.adjustments());
  }

  @Test void unresolvedStackingIsExplicitAndDoesNotPickAnOrderDependentWinner() {
    var result=calculate(BOOST,POWER,38,11);
    assertEquals(PARTIAL,result.coverage());
    assertEquals(TOTAL,result.adjusted());
    assertTrue(result.effects().stream().filter(e -> e.effectId().equals("stats")).allMatch(e -> e.status()==UNSUPPORTED));
    assertEquals(result.adjusted(),calculate(BOOST,POWER,11,38).adjusted());
  }

  @Test void accelerationTunerTwoAndMachineKitMatchTheExecutedReferenceCalculator() {
    var base = new BaseStatsBreakdown(points(58,65,29,54,34),points(13,8,5,18,16),points(45,57,24,36,18));
    assertEquals(points(0,20,-2,0,-2),calculate(POWER,ACCELERATION,55).adjustments());
    assertEquals(points(0,20,0,0,0),calculate(POWER,ACCELERATION,34).adjustments());
    for (var machine : RacingType.values()) for (var ids : List.of(List.of(55,34),List.of(34,55))) {
      var result = PassiveStatsCalculator.calculate(dev.ringlab.importing.RuleFixtures.snapshot(), base,"1.4.1",POWER,machine,ids.stream().map(this::gadget).toList(),true);
      assertEquals(CALCULATED,result.coverage());
      assertEquals(machine == ACCELERATION ? points(0,40,-2,0,-2) : points(0,8,0,0,0),result.adjustments());
      assertEquals(machine == ACCELERATION ? points(58,105,27,54,32) : points(58,73,29,54,34),result.adjusted());
      assertEquals(CONDITIONAL,result.effects().stream().filter(e -> e.gadgetId().equals(id(34)) && !e.effectId().equals("stats")).findFirst().orElseThrow().status());
    }
    // Pair evidence is not transitive and does not authorize other tuner/kit pairs.
    assertEquals(PARTIAL,calculate(POWER,ACCELERATION,54,34).coverage());
    assertEquals(PARTIAL,calculate(POWER,ACCELERATION,55,34,54).coverage());
    assertEquals(PARTIAL,calculate(POWER,SPEED,52,49).coverage());
    assertEquals(points(0,40,-2,8,-2),calculate(POWER,ACCELERATION,55,34,58).adjustments());
  }

  @Test void alwaysActiveKitEffectsAreSeparateFromExcludedCapabilities() {
    assertEquals(points(0,0,3,0,0),calculate(null,null,50).adjustments());
    assertEquals(NON_STAT,calculate(null,null,50).effects().getLast().status());
    assertEquals(points(0,0,8,8,0),calculate(null,null,23).adjustments());
    assertEquals(CONDITIONAL,calculate(null,null,23).effects().getLast().status());
    var doubleDown=new Gadget(UUID.fromString("f6f75d95-16dd-538d-89b5-49c71cc8a346"),"Double Down",null,3,null);
    var result=PassiveStatsCalculator.calculate(dev.ringlab.importing.RuleFixtures.snapshot(), BASE,"1.4.1",null,null,List.of(doubleDown),true);
    assertEquals(points(-10,-10,-10,-10,-10),result.adjustments());
    assertEquals(NON_STAT,result.effects().getLast().status());
    for (var racer : RacingType.values()) for (var machine : RacingType.values()) {
      var drift = calculate(racer,machine,50);
      var panel = calculate(racer,machine,23);
      var penalty = PassiveStatsCalculator.calculate(dev.ringlab.importing.RuleFixtures.snapshot(), BASE,"1.4.1",racer,machine,List.of(doubleDown),true);
      assertEquals(CALCULATED,drift.coverage());
      assertEquals(CALCULATED,panel.coverage());
      assertEquals(CALCULATED,penalty.coverage());
      assertEquals(points(0,0,3,0,0),drift.adjustments());
      assertEquals(points(0,0,8,8,0),panel.adjustments());
      assertEquals(points(-10,-10,-10,-10,-10),penalty.adjustments());
    }
  }

  @Test void independentModifiersAreCalculatedTogetherRegardlessOfSelectionOrder() {
    var result = calculate(POWER,HANDLING,50,55,61);
    assertEquals(points(0,8,3,0,8),result.adjustments());
    assertEquals(CALCULATED,result.coverage());
    assertEquals(result.adjusted(),calculate(POWER,HANDLING,61,50,55).adjusted());
  }

  @Test void unrelatedKnownEffectsSurviveAnUnresolvedOverlappingPair() {
    var result = calculate(BOOST,POWER,38,11,50);
    assertEquals(PARTIAL,result.coverage());
    assertEquals(points(0,0,3,0,0),result.adjustments());
    assertEquals(APPLIED,result.effects().stream().filter(e -> e.gadgetId().equals(id(50))
        && e.effectId().equals("stats")).findFirst().orElseThrow().status());
  }

  @Test void nonStatAndRaceEffectsNeverTurnIntoFiveStatBonuses() {
    for(int n:new int[]{5,6,7,8,15,17,45,46,47,48}) {
      var result=calculate(BOOST,POWER,n);
      assertEquals(TOTAL,result.adjusted());
      assertTrue(result.effects().stream().anyMatch(e -> e.status()==CONDITIONAL));
    }
    for(String id:List.of("174ea0a3-43bb-5001-bbd4-8b598482fe59","5a000a58-7d7a-581c-80e3-6ae8661215b7","182bdfa8-44d3-5de8-941b-d525381dd0a3")) {
      var result=PassiveStatsCalculator.calculate(dev.ringlab.importing.RuleFixtures.snapshot(), BASE,"1.4.1",BOOST,POWER,List.of(new Gadget(UUID.fromString(id),"Other",null,1,null)),true);
      assertEquals(TOTAL,result.adjusted());assertEquals(CALCULATED,result.coverage());
    }
  }

  @Test void unknownContextRulesAndBaseFieldsStayExplicitlyUnknown() {
    assertEquals(REQUIRES_SELECTION,calculate(SPEED,null,52).effects().getFirst().status());
    assertEquals(REQUIRES_SELECTION,calculate(null,POWER,11).effects().getFirst().status());
    assertEquals(PARTIAL,calculate(BOOST,POWER,999).coverage());
    var unknown=new BaseStatsBreakdown(new BaseStats(null,BigDecimal.ONE,null,null,null),BaseStats.UNKNOWN,BaseStats.UNKNOWN);
    var result=PassiveStatsCalculator.calculate(dev.ringlab.importing.RuleFixtures.snapshot(), unknown,"1.4.1",BOOST,POWER,List.of(gadget(23)),true);
    assertNull(result.adjusted().speed());assertNull(result.adjusted().handling());
    assertEquals(BigDecimal.ONE,result.adjusted().acceleration());assertEquals(PARTIAL,result.coverage());
  }

  @Test void oldOrMissingPatchDoesNotFallBackAndInvalidLegacyLoadoutsKeepBase() {
    for(String version:Arrays.asList(null,"1.3.1","1.5.0")) {
      var result=PassiveStatsCalculator.calculate(dev.ringlab.importing.RuleFixtures.snapshot(), BASE,version,BOOST,POWER,List.of(gadget(38)),true);
      assertEquals(UNSUPPORTED_VERSION,result.coverage());assertEquals(BASE,result.base());assertEquals(TOTAL,result.adjusted());
    }
    var result=PassiveStatsCalculator.calculate(dev.ringlab.importing.RuleFixtures.snapshot(), BASE,"1.4.1",BOOST,POWER,List.of(gadget(38)),false);
    assertEquals(INVALID_LOADOUT,result.coverage());assertEquals(TOTAL,result.adjusted());
  }
}
