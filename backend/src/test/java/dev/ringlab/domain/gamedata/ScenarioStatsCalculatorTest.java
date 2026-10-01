package dev.ringlab.domain.gamedata;

import static dev.ringlab.domain.gamedata.PassiveGadgetRules.*;
import static dev.ringlab.domain.gamedata.ScenarioStatsResult.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class ScenarioStatsCalculatorTest {
  private static final BaseStats BASE = points(58,65,29,54,34);
  private PassiveStatsResult passive(UUID... ids) {
    return PassiveStatsCalculator.calculate(new BaseStatsBreakdown(BASE, BASE, BaseStats.ZERO), "1.4.1",
        RacingType.POWER, RacingType.ACCELERATION,
        Arrays.stream(ids).map(id -> new Gadget(id,"Fixture " + id,null,1,null)).toList(),true);
  }
  private ScenarioContext lap(int lap) { return new ScenarioContext(lap,null,null,null,null); }
  private ScenarioContext form(ScenarioContext.VehicleForm form) { return new ScenarioContext(null,form,null,null,null); }

  @Test void everyReviewedLapBonusUsesTheSourceValueAndExactLap() {
    // SEGA 1.4.1 + Yoshister current table: ordinary +20; super +60, every stat.
    int[][] cases = {{45,1,20},{47,1,60},{46,3,20},{48,3,60}};
    for (int[] fixture : cases) {
      var baseline = passive(id(fixture[0]));
      var result = ScenarioStatsCalculator.calculate(baseline,lap(fixture[1]));
      assertEquals(new BigDecimal(58 + fixture[2]),result.total().speed());
      assertEquals(new BigDecimal(65 + fixture[2]),result.total().acceleration());
      assertEquals(new BigDecimal(29 + fixture[2]),result.total().handling());
      assertEquals(new BigDecimal(54 + fixture[2]),result.total().power());
      assertEquals(new BigDecimal(34 + fixture[2]),result.total().boost());
      assertSame(baseline,result.passive());
      assertEquals(BASE,ScenarioStatsCalculator.calculate(baseline,lap(2)).total());
      assertEquals(CONDITION_NOT_MET,ScenarioStatsCalculator.calculate(baseline,lap(2)).effects().getFirst().status());
    }
  }

  @Test void everyTransformationRuleUsesFormNotMapsOrMachineType() {
    // SEGA + original/builder mode rows: +20 to all five, no accumulation.
    for (int gadget : new int[]{15,17,18}) {
      for (var form : ScenarioContext.VehicleForm.values()) {
        boolean matches = gadget == 15 ? form == ScenarioContext.VehicleForm.WATER
            : gadget == 17 ? form == ScenarioContext.VehicleForm.FLIGHT : form != ScenarioContext.VehicleForm.NORMAL;
        var result = ScenarioStatsCalculator.calculate(passive(id(gadget)),form(form));
        assertEquals(matches ? points(78,85,49,74,54) : BASE,result.total());
      }
    }
  }

  @Test void regularRingEngineIsFixedAtPositiveHeldRingsNotAnAccumulatingCounter() {
    // Yoshister row 58 / builder gadget_099 burning row: fixed +12, not +rings.
    for (int rings : new int[]{0,1,50,100,999}) {
      var result = ScenarioStatsCalculator.calculate(passive(ScenarioGadgetRules.RING_ENGINE),
          new ScenarioContext(null,null,rings,null,null));
      assertEquals(rings == 0 ? BASE : points(70,77,41,66,46),result.total());
      assertEquals(rings == 0 ? CONDITION_NOT_MET : ACTIVE_NON_STAT,result.effects().getLast().status());
    }
  }

  @Test void missingConditionIsNotAZeroEffectAndNeverProducesAnExactTotal() {
    var result=ScenarioStatsCalculator.calculate(passive(id(45)),ScenarioContext.UNSPECIFIED);
    assertEquals(ScenarioStatsResult.Coverage.PARTIAL,result.coverage());
    assertNull(result.total());assertEquals(BASE,result.knownSubtotal());
    assertEquals(CONDITION_UNKNOWN,result.effects().getFirst().status());
    assertNull(result.effects().getFirst().adjustment());
  }

  @Test void descriptiveActiveEffectsNeverBecomeStatPoints() {
    var result=ScenarioStatsCalculator.calculate(passive(id(2),id(4)),new ScenarioContext(null,null,null,true,300));
    assertEquals(BASE,result.total());assertEquals(BaseStats.ZERO,result.adjustments());
    assertTrue(result.effects().stream().allMatch(e -> e.status()==ACTIVE_NON_STAT && e.adjustment()==null));
    var inactive=ScenarioStatsCalculator.calculate(passive(id(2),id(4)),new ScenarioContext(null,null,null,false,301));
    assertTrue(inactive.effects().stream().allMatch(e->e.status()==CONDITION_NOT_MET));
  }

  @Test void evolutionAndHyperRemainUnsupportedWithoutInventingCapsOrCounters() {
    for (UUID gadget : List.of(id(5),id(6),id(7),id(8),ScenarioGadgetRules.HYPER_RING_ENGINE)) {
      var result=ScenarioStatsCalculator.calculate(passive(gadget),new ScenarioContext(1,ScenarioContext.VehicleForm.FLIGHT,999,true,0));
      assertNull(result.total());assertNull(result.effects().getFirst().adjustment());
      assertEquals(UNSUPPORTED,result.effects().getFirst().status());
      assertEquals(BASE,result.knownSubtotal());
    }
  }

  @Test void simultaneousUnverifiedBonusesAreExcludedInEitherSavedOrder() {
    for (var ids : List.of(new UUID[]{id(45),id(47)},new UUID[]{id(47),id(45)})) {
      var result=ScenarioStatsCalculator.calculate(passive(ids),lap(1));
      assertNull(result.total());assertEquals(BASE,result.knownSubtotal());
      assertTrue(result.effects().stream().allMatch(e->e.status()==UNSUPPORTED && e.adjustment()==null));
    }
    // Non-stat effects can coexist, and opposite-lap bonuses never overlap.
    var exclusive=ScenarioStatsCalculator.calculate(passive(id(45),id(46),id(2)),new ScenarioContext(1,null,null,true,null));
    assertEquals(points(78,85,49,74,54),exclusive.total());
  }

  @Test void passivePenaltiesAndCoverageSurviveUnverifiedConditionalOverlap() {
    var baseline=passive(id(55),id(45));
    var result=ScenarioStatsCalculator.calculate(baseline,lap(1));
    assertSame(baseline,result.passive());assertEquals(points(58,85,27,54,32),result.knownSubtotal());
    assertNull(result.total());assertEquals(UNSUPPORTED,result.effects().getFirst().status());
    assertEquals(baseline.adjusted(),ScenarioStatsCalculator.calculate(baseline,lap(2)).total());
    var missing=new PassiveStatsResult(baseline.base(),baseline.adjustments(),baseline.adjusted(),
        PassiveStatsResult.Coverage.PARTIAL,baseline.ruleset(),baseline.supportedVersion(),baseline.note(),baseline.effects());
    assertNull(ScenarioStatsCalculator.calculate(missing,lap(2)).total());
  }

  @Test void assumedStarterAndSeaDogPairAddsBothBonusesInEitherOrderAndResets() {
    var starter = ScenarioGadgetRules.find(id(45), "other-0").adjustment();
    var water = ScenarioGadgetRules.find(id(15), "other-0").adjustment();
    for (var ids : List.of(new UUID[]{id(45),id(15)}, new UUID[]{id(15),id(45)})) {
      var baseline = passive(ids);
      var inactive = new ScenarioContext(2, ScenarioContext.VehicleForm.NORMAL, null, null, null);
      assertEquals(BASE, ScenarioStatsCalculator.calculate(baseline, inactive).total());
      var lapOnly = ScenarioStatsCalculator.calculate(baseline,
          new ScenarioContext(1, ScenarioContext.VehicleForm.NORMAL, null, null, null));
      assertEquals(BaseStats.sum(List.of(BASE, starter)), lapOnly.total());
      assertEquals(1, lapOnly.effects().stream().filter(e -> e.status() == ACTIVE_AND_APPLIED).count());
      var waterOnly = ScenarioStatsCalculator.calculate(baseline,
          new ScenarioContext(2, ScenarioContext.VehicleForm.WATER, null, null, null));
      assertEquals(BaseStats.sum(List.of(BASE, water)), waterOnly.total());
      assertEquals(1, waterOnly.effects().stream().filter(e -> e.status() == ACTIVE_AND_APPLIED).count());
      var both = ScenarioStatsCalculator.calculate(baseline,
          new ScenarioContext(1, ScenarioContext.VehicleForm.WATER, null, null, null));
      assertEquals(ScenarioStatsResult.Coverage.CALCULATED, both.coverage());
      assertEquals(points(40,40,40,40,40), both.adjustments());
      assertEquals(points(98,105,69,94,74), both.total());
      assertEquals(both.total(), both.knownSubtotal());
      assertTrue(both.effects().stream().allMatch(e -> e.status() == ACTIVE_AND_APPLIED
          && e.adjustment().equals(points(20,20,20,20,20))
          && e.explanation().contains("assumed additive")));
      assertEquals(BASE, ScenarioStatsCalculator.calculate(baseline, inactive).total());
    }
  }

  @Test void assumedPairDoesNotAuthorizeOtherEffectsOrTransitiveStacks() {
    assertFalse(ScenarioGadgetRules.assumedAdditivePair(id(45), "other-1", id(15), "other-0"));
    assertFalse(ScenarioGadgetRules.assumedAdditivePair(id(45), "other-0", id(15), "terrain"));
    assertFalse(ScenarioGadgetRules.assumedAdditivePair(id(45), "other-0", id(45), "other-0"));
    var context = new ScenarioContext(1, ScenarioContext.VehicleForm.WATER, null, null, null);
    for (var ids : List.of(new UUID[]{id(45),id(15),id(47)}, new UUID[]{id(47),id(15),id(45)},
        new UUID[]{id(45),id(15),id(55)}, new UUID[]{id(55),id(15),id(45)})) {
      var baseline = passive(ids);
      var result = ScenarioStatsCalculator.calculate(baseline, context);
      assertEquals(ScenarioStatsResult.Coverage.PARTIAL, result.coverage());
      assertNull(result.total());
      assertEquals(baseline.adjusted(), result.knownSubtotal());
      assertTrue(result.effects().stream().allMatch(e -> e.status() == UNSUPPORTED && e.adjustment() == null));
    }
    var otherPair = ScenarioStatsCalculator.calculate(passive(id(45),id(17)),
        new ScenarioContext(1, ScenarioContext.VehicleForm.FLIGHT, null, null, null));
    assertEquals(ScenarioStatsResult.Coverage.PARTIAL, otherPair.coverage());
    assertNull(otherPair.total());
  }

  @Test void unavailablePassiveCannotBecomeAvailableThroughScenario() {
    for (var coverage : List.of(PassiveStatsResult.Coverage.UNSUPPORTED_VERSION,PassiveStatsResult.Coverage.INVALID_LOADOUT)) {
      var p=passive(id(45));
      var result=ScenarioStatsCalculator.calculate(new PassiveStatsResult(p.base(),p.adjustments(),p.adjusted(),coverage,
          p.ruleset(),p.supportedVersion(),p.note(),p.effects()),lap(1));
      assertNull(result.total());assertEquals(ScenarioStatsResult.Coverage.UNAVAILABLE,result.coverage());
    }
  }

  @Test void contextBoundsAndDecimalPrecisionArePreserved() {
    assertThrows(IllegalArgumentException.class,()->lap(0));
    assertThrows(IllegalArgumentException.class,()->lap(4));
    assertThrows(IllegalArgumentException.class,()->new ScenarioContext(null,null,-1,null,null));
    assertThrows(IllegalArgumentException.class,()->new ScenarioContext(null,null,1000,null,null));
    assertThrows(IllegalArgumentException.class,()->new ScenarioContext(null,null,null,null,50001));
    var p=passive(id(45));var decimal=new BaseStats(new BigDecimal("99.125"),BASE.acceleration(),BASE.handling(),BASE.power(),BASE.boost());
    var result=ScenarioStatsCalculator.calculate(new PassiveStatsResult(p.base(),p.adjustments(),decimal,p.coverage(),
        p.ruleset(),p.supportedVersion(),p.note(),p.effects()),lap(1));
    assertEquals(new BigDecimal("119.125"),result.total().speed());
  }
}
