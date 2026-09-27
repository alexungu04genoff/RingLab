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
    return PassiveStatsCalculator.calculate(BASE,"1.4.1",racer,machine,Arrays.stream(ids).mapToObj(this::gadget).toList(),true);
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
    var result = PassiveStatsCalculator.calculate(BASE, "1.4.1", BOOST, ACCELERATION,
        List.of(spinner, inventorySwap), true);
    assertEquals(CALCULATED, result.coverage());
    assertEquals(TOTAL, result.adjusted());
    assertEquals(points(0,0,0,0,0), result.adjustments());
    assertEquals(2, result.effects().size());
    assertTrue(result.effects().stream().allMatch(effect -> effect.status() == NON_STAT));
  }

  @Test void everyTunerUsesMachineTypeIncludingAllVerifiedPenaltiesAndNonMatchingValues() {
    // Independent fixtures transcribed from the original Gadget List (1.4), corroborated by atwiki /45.
    int[][] expected = {{20,-4,0,0,0},{20,-2,0,0,-2},{0,20,-4,0,0},{0,20,-2,0,-2},
        {0,0,20,-4,0},{-2,0,20,-2,0},{0,-4,0,20,0},{0,-2,-2,20,0},{-4,0,0,0,20},{-2,0,0,-2,20}};
    var types = new RacingType[] {SPEED,ACCELERATION,HANDLING,POWER,BOOST};
    for (int index=0; index<10; index++) {
      int[] e = expected[index];
      var type = types[index/2];
      var other = types[(index/2+1)%5];
      assertEquals(points(e[0],e[1],e[2],e[3],e[4]),calculate(other,type,52+index).adjustments());
      int[] nonMatch = new int[5]; nonMatch[index/2]=8;
      assertEquals(points(nonMatch[0],nonMatch[1],nonMatch[2],nonMatch[3],nonMatch[4]),
          calculate(type,other,52+index).adjustments());
    }
  }

  @Test void machineKitsHaveTwentyForMatchingMachineAndNoOldPenalty() {
    int[] ids = {49,34,37,38,62};
    var types = new RacingType[] {SPEED,ACCELERATION,HANDLING,POWER,BOOST};
    for (int i=0;i<ids.length;i++) {
      int[] expected = new int[5];expected[i]=20;
      var match = calculate(types[(i+1)%5],types[i],ids[i]);
      assertEquals(points(expected[0],expected[1],expected[2],expected[3],expected[4]),match.adjustments());
      assertTrue(match.effects().stream().anyMatch(e -> e.status()==CONDITIONAL));
      var nonMatch = calculate(types[i],types[(i+1)%5],ids[i]);
      assertEquals(points(0,0,0,0,0),nonMatch.adjustments());
      assertEquals(NOT_MATCHED,nonMatch.effects().getFirst().status());
    }
  }

  @Test void characterKitsCheckRacerNotMachineAndIncludePenalties() {
    int[] ids={20,16,13,12,11};
    var types=new RacingType[]{SPEED,ACCELERATION,HANDLING,POWER,BOOST};
    int[][] expected={{7,-5,0,0,0},{0,7,0,0,-5},{-5,0,7,0,0},{0,0,-5,7,0},{0,0,0,-5,7}};
    for(int i=0;i<ids.length;i++) {
      int[] e=expected[i];
      assertEquals(points(e[0],e[1],e[2],e[3],e[4]),calculate(types[i],types[(i+1)%5],ids[i]).adjustments());
      assertEquals(points(0,0,0,0,0),calculate(types[(i+1)%5],types[i],ids[i]).adjustments());
    }
    assertEquals(points(0,0,0,-5,7),calculate(BOOST,POWER,11).adjustments());
    assertEquals(PARTIAL,calculate(HANDLING,POWER,13).coverage()); // Thief bundle remains disputed.
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
    var result = PassiveStatsCalculator.calculate(base,"1.4.1",POWER,SPEED,gadgets,true);
    assertEquals(CALCULATED,result.coverage());
    assertEquals(points(40,10,0,16,-2),result.adjustments());
    assertEquals(points(102,40,43,78,41),result.adjusted());
    assertTrue(result.effects().stream().allMatch(effect -> effect.status() == APPLIED));
    assertEquals(result.adjusted(),PassiveStatsCalculator.calculate(base,"1.4.1",POWER,SPEED,gadgets.reversed(),true).adjusted());
    // The same gadgets on an Acceleration machine use different bonuses and penalties.
    var acceleration = PassiveStatsCalculator.calculate(base,"1.4.1",POWER,ACCELERATION,gadgets,true);
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

  @Test void alwaysActiveKitEffectsAreSeparateFromExcludedCapabilities() {
    assertEquals(points(0,0,3,0,0),calculate(null,null,50).adjustments());
    assertEquals(NON_STAT,calculate(null,null,50).effects().getLast().status());
    assertEquals(points(0,0,8,8,0),calculate(null,null,23).adjustments());
    assertEquals(CONDITIONAL,calculate(null,null,23).effects().getLast().status());
    var doubleDown=new Gadget(UUID.fromString("f6f75d95-16dd-538d-89b5-49c71cc8a346"),"Double Down",null,3,null);
    var result=PassiveStatsCalculator.calculate(BASE,"1.4.1",null,null,List.of(doubleDown),true);
    assertEquals(points(-10,-10,-10,-10,-10),result.adjustments());
    assertEquals(NON_STAT,result.effects().getLast().status());
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
      var result=PassiveStatsCalculator.calculate(BASE,"1.4.1",BOOST,POWER,List.of(new Gadget(UUID.fromString(id),"Other",null,1,null)),true);
      assertEquals(TOTAL,result.adjusted());assertEquals(CALCULATED,result.coverage());
    }
  }

  @Test void unknownContextRulesAndBaseFieldsStayExplicitlyUnknown() {
    assertEquals(REQUIRES_SELECTION,calculate(SPEED,null,52).effects().getFirst().status());
    assertEquals(REQUIRES_SELECTION,calculate(null,POWER,11).effects().getFirst().status());
    assertEquals(PARTIAL,calculate(BOOST,POWER,999).coverage());
    var unknown=new BaseStatsBreakdown(new BaseStats(null,BigDecimal.ONE,null,null,null),BaseStats.UNKNOWN,BaseStats.UNKNOWN);
    var result=PassiveStatsCalculator.calculate(unknown,"1.4.1",BOOST,POWER,List.of(gadget(23)),true);
    assertNull(result.adjusted().speed());assertNull(result.adjusted().handling());
    assertEquals(BigDecimal.ONE,result.adjusted().acceleration());assertEquals(PARTIAL,result.coverage());
  }

  @Test void oldOrMissingPatchDoesNotFallBackAndInvalidLegacyLoadoutsKeepBase() {
    for(String version:Arrays.asList(null,"1.3.1","1.5.0")) {
      var result=PassiveStatsCalculator.calculate(BASE,version,BOOST,POWER,List.of(gadget(38)),true);
      assertEquals(UNSUPPORTED_VERSION,result.coverage());assertEquals(BASE,result.base());assertEquals(TOTAL,result.adjusted());
    }
    var result=PassiveStatsCalculator.calculate(BASE,"1.4.1",BOOST,POWER,List.of(gadget(38)),false);
    assertEquals(INVALID_LOADOUT,result.coverage());assertEquals(TOTAL,result.adjusted());
  }
}
