package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.collection.CollectionExclusions;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.importing.RuleFixtures;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.ringlab.domain.gamedata.PassiveGadgetRules.points;
import static dev.ringlab.domain.build.recommendation.RecommendationResult.Outcome.*;

class ScenarioRecommendationTest {
  private static final List<StatPriority> ORDER = List.of(StatPriority.values());
  private static final BuildSelection EMPTY = new BuildSelection(null, null, null, null, List.of());
  private static final BuildRecommendationSolver.Budget BUDGET = new BuildRecommendationSolver.Budget(1_000_000, Duration.ofSeconds(30));
  private static final ScenarioContext WATER = new ScenarioContext(1, ScenarioContext.VehicleForm.WATER, 1, true, 300);

  @Test void everyNumericRuleParticipatesInSearchDespiteZeroPassiveAdjustment() {
    int[][] cases = {{45,1,20},{47,1,60},{46,3,20},{48,3,60},{15,1,20},{17,1,20},{18,1,20}};
    for (var rule : cases) for (var form : ScenarioContext.VehicleForm.values()) {
      var f = new Fixture(); var gadget = f.add(rule[0], 1);
      boolean active = rule[0] < 20 ? rule[0] == 15 ? form == ScenarioContext.VehicleForm.WATER
          : rule[0] == 17 ? form == ScenarioContext.VehicleForm.FLIGHT : form != ScenarioContext.VehicleForm.NORMAL : true;
      var context = new ScenarioContext(rule[1], form, 0, false, 301);
      var result = solve(f, request(f, context, EMPTY, RecommendationMode.STRICT));
      assertEquals(ESTABLISHED, result.outcome());
      assertEquals(active ? List.of(gadget) : List.of(), result.selection().gadgetIds());
      assertEquals(points(active ? 4 + rule[2] : 4, active ? 4 + rule[2] : 4, active ? 4 + rule[2] : 4,
          active ? 4 + rule[2] : 4, active ? 4 + rule[2] : 4), result.recommendedStats());
    }
    for (int rings : new int[]{0, 1, 70, 999}) {
      var f = new Fixture(); f.add(ScenarioGadgetRules.RING_ENGINE, 1);
      var result = solve(f, request(f, new ScenarioContext(null, null, rings, null, null), EMPTY, RecommendationMode.STRICT));
      assertEquals(BigDecimal.valueOf(rings == 0 ? 4 : 16), result.recommendedStats().speed());
      assertEquals(rings == 0 ? 0 : 1, result.selection().gadgetIds().size());
    }
  }

  @Test void unknownAndUnsupportedNumericalEffectsAreExcludedNotZeroed() {
    for (var gadget : List.of(PassiveGadgetRules.id(45), PassiveGadgetRules.id(15), ScenarioGadgetRules.RING_ENGINE,
        PassiveGadgetRules.id(5), PassiveGadgetRules.id(6), PassiveGadgetRules.id(7), PassiveGadgetRules.id(8), ScenarioGadgetRules.HYPER_RING_ENGINE)) {
      var f = new Fixture(); f.add(gadget, 1); f.current = gadgets(f.current,List.of(gadget));
      var unlocked = solve(f,request(f,ScenarioContext.UNSPECIFIED,EMPTY,RecommendationMode.STRICT));
      assertEquals(ESTABLISHED,unlocked.outcome()); assertNull(unlocked.currentStats());
      assertEquals(List.of(),unlocked.selection().gadgetIds()); assertEquals(points(4,4,4,4,4),unlocked.recommendedStats());
      var locked = solve(f,request(f,ScenarioContext.UNSPECIFIED,f.current,RecommendationMode.STRICT));
      assertEquals(UNAVAILABLE,locked.outcome()); assertNull(locked.recommendedStats());
      assertEquals(ScenarioStatsResult.Coverage.PARTIAL,locked.scenario().current().coverage());
    }
  }

  @Test void approvedPairIsStructuredAndOtherStacksStayUnsupported() {
    for (var pair : List.of(List.of(45,15), List.of(15,45))) {
      var f = new Fixture(); var selected = pair.stream().map(n -> f.add(n, 1)).toList();
      f.current = gadgets(f.current, selected);
      var result = solve(f, request(f, WATER, f.current, RecommendationMode.STRICT));
      assertEquals(ESTABLISHED, result.outcome()); assertEquals(points(44,44,44,44,44), result.recommendedStats());
      assertEquals(selected, result.selection().gadgetIds());
      assertEquals(List.of(ScenarioAssumption.QUICK_STARTER_SEA_DOG_ASSUMED_ADDITIVE), result.scenario().recommended().assumptions());
    }
    for (var stack : List.of(List.of(47,15), List.of(45,15,18), List.of(15,18), List.of(45,52))) {
      var f = new Fixture(); f.current = gadgets(f.current, stack.stream().map(n -> f.add(n,1)).toList());
      var result = solve(f, request(f, WATER, f.current, RecommendationMode.STRICT));
      assertEquals(UNAVAILABLE, result.outcome()); assertNull(result.currentStats()); assertNull(result.selection());
      assertTrue(result.scenario().current().assumptions().isEmpty());
    }
    var f = new Fixture(); f.current = gadgets(f.current, List.of(f.add(45,1), f.add(52,1)));
    assertEquals(ESTABLISHED, solve(f, request(f, new ScenarioContext(2,null,null,null,null), f.current, RecommendationMode.STRICT)).outcome());
    f = new Fixture(); f.current = gadgets(f.current, List.of(f.add(17,1), f.add(18,1)));
    assertEquals(UNAVAILABLE, solve(f, request(f, new ScenarioContext(null,ScenarioContext.VehicleForm.FLIGHT,null,null,null), f.current, RecommendationMode.STRICT)).outcome());
  }

  @Test void utilityConditionsNeverContributePointsOrPreventCompleteCoverage() {
    var f = new Fixture(); f.current = gadgets(f.current, List.of(f.add(2,1), f.add(4,1)));
    for (var context : List.of(WATER, ScenarioContext.UNSPECIFIED, new ScenarioContext(2,null,null,false,301))) {
      var result = solve(f, request(f, context, f.current, RecommendationMode.STRICT));
      assertEquals(points(4,4,4,4,4), result.recommendedStats());
      assertEquals(ScenarioStatsResult.Coverage.CALCULATED, result.scenario().recommended().coverage());
      assertTrue(result.scenario().recommended().effects().stream().noneMatch(ScenarioStatsResult.Effect::statEffect));
    }
  }

  @Test void balancedFloorsUseScenarioReferenceAndNeverFallback() {
    var f = new Fixture(); f.current = gadgets(f.current, List.of(f.add(45,1)));
    var result = solve(f, request(f, WATER, EMPTY, RecommendationMode.BALANCED));
    for (var stat : ORDER) assertEquals(0, result.balanced().minimum().get(stat).compareTo(new BigDecimal("24")));
    assertEquals(points(24,24,24,24,24), result.currentStats());
    result = solve(f, request(f, ScenarioContext.UNSPECIFIED, EMPTY, RecommendationMode.BALANCED));
    assertEquals(UNAVAILABLE, result.outcome()); assertNull(result.currentStats()); assertNull(result.recommendedStats());
    assertEquals(ScenarioStatsResult.Coverage.PARTIAL, result.scenario().current().coverage());
  }

  @Test void searchLimitsReportOnlyFullyEvaluatedIncumbents() {
    var f = new Fixture(); f.add(45,1);
    var one = new BuildRecommendationSolver.Budget(1, Duration.ofSeconds(1));
    var result = new BuildRecommendationSolver(f.catalog(), request(f,WATER,EMPTY,RecommendationMode.STRICT),one, () -> 0).solve();
    assertEquals(BEST_FOUND,result.outcome()); assertEquals(points(4,4,4,4,4),result.recommendedStats());
    assertEquals(ScenarioStatsResult.Coverage.CALCULATED,result.scenario().recommended().coverage());
    f.current = EMPTY;
    result = new BuildRecommendationSolver(f.catalog(),request(f,WATER,EMPTY,RecommendationMode.STRICT),one, () -> 0).solve();
    assertEquals(LIMIT_WITHOUT_CANDIDATE,result.outcome()); assertNull(result.selection());
    var clock = new AtomicLong();
    result = new BuildRecommendationSolver(f.catalog(),request(f,WATER,EMPTY,RecommendationMode.STRICT),
        new BuildRecommendationSolver.Budget(100,Duration.ofMillis(1)), () -> clock.getAndAdd(1_000_000)).solve();
    assertEquals(LIMIT_WITHOUT_CANDIDATE,result.outcome()); assertEquals(0,result.work()); assertEquals(2,result.elapsedMillis());
  }

  @Test void incompleteOrNegativeReferenceCannotSupplyBalancedFloors() {
    var f = new Fixture();
    f.partStats.remove(id(20));
    var result = solve(f,request(f,WATER,EMPTY,RecommendationMode.BALANCED));
    assertEquals(UNAVAILABLE,result.outcome()); assertNull(result.currentStats()); assertNull(result.balanced());
    f.partStats.put(id(20),points(-10,1,1,1,1));
    result = solve(f,request(f,WATER,EMPTY,RecommendationMode.BALANCED));
    assertEquals(UNAVAILABLE,result.outcome()); assertNull(result.selection()); assertNull(result.balanced());
  }

  @Test void basisAndContextAreExplicitDomainInvariants() {
    var f = new Fixture();
    assertThrows(IllegalArgumentException.class,() -> new RecommendationRequest(id(99),f.type,ORDER,f.current,EMPTY,
        RecommendationMode.STRICT,null,null,null));
    assertThrows(IllegalArgumentException.class,() -> new RecommendationRequest(id(99),f.type,ORDER,f.current,EMPTY,
        RecommendationMode.STRICT,null,RecommendationBasis.PASSIVE,WATER));
    assertThrows(IllegalArgumentException.class,() -> request(f,null,EMPTY,RecommendationMode.STRICT));
    assertEquals(ESTABLISHED,solve(f,request(f,ScenarioContext.UNSPECIFIED,EMPTY,RecommendationMode.STRICT)).outcome());
  }

  @Test void equalScenarioTotalsKeepCurrentChoicesThenUseCostAndStableIds() {
    var f = new Fixture();
    f.racers.put(id(2),new Racer(id(2),"Equal racer",RacingType.SPEED,null));
    f.racerStats.put(id(2),f.racerStats.get(id(1)));
    var seaDog = f.add(15,1); var allRounder = f.add(18,1);
    f.current = new BuildSelection(id(2),id(20),id(21),id(22),List.of(allRounder));
    for (var mode : RecommendationMode.values()) {
      var kept = solve(f,request(f,WATER,EMPTY,mode));
      assertEquals(f.current,kept.selection()); assertTrue(kept.alreadyBest());
      assertEquals(points(24,24,24,24,24),kept.recommendedStats());
    }
    f.current = EMPTY;
    var stable = solve(f,request(f,WATER,EMPTY,RecommendationMode.STRICT));
    assertEquals(id(1),stable.selection().racerId()); assertEquals(List.of(seaDog),stable.selection().gadgetIds());
    f.add(seaDog,2);
    assertEquals(List.of(allRounder),solve(f,request(f,WATER,EMPTY,RecommendationMode.STRICT)).selection().gadgetIds());
  }

  @Test void optimizedSearchMatchesIndependentExhaustiveOracleAcrossContextsConstraintsAndOrders() {
    for (int trial = 0; trial < 12; trial++) {
      var f = new Fixture(); var random = new Random(710 + trial);
      f.type = trial / 4 == 0 ? RacingType.BOOST : trial / 4 == 1 ? RacingType.ACCELERATION : RacingType.SPEED;
      f.machines.replaceAll((id,m) -> new Machine(id,m.name(),f.type,null));
      f.racers.put(id(2),new Racer(id(2),"Other type",RacingType.POWER,null));
      f.racers.put(id(3),new Racer(id(3),"Equal contribution",RacingType.SPEED,null));
      f.racers.keySet().forEach(id -> f.racerStats.put(id, randomPoints(random)));
      f.machines.put(id(11),new Machine(id(11),"Other source",f.type,null));
      for (var slot : MachinePartType.values()) {
        var id = id(30 + slot.ordinal()); f.parts.put(id,new MachinePart(id,id(11),slot)); f.partStats.put(id,randomPoints(random));
      }
      f.partStats.replaceAll((id,s) -> randomPoints(random));
      if (f.type == RacingType.BOOST) f.current = new BuildSelection(id(1),id(20),id(21),null,List.of());
      for (int gadget : new int[]{45,15,18,52,2}) f.add(gadget, gadget == 18 ? 3 : 1);
      f.add(ScenarioGadgetRules.RING_ENGINE,2);
      var context = switch (trial % 4) {
        case 0 -> WATER;
        case 1 -> new ScenarioContext(3,ScenarioContext.VehicleForm.FLIGHT,0,false,301);
        case 2 -> new ScenarioContext(2,ScenarioContext.VehicleForm.NORMAL,99,null,null);
        default -> ScenarioContext.UNSPECIFIED;
      };
      // Current zero-point utilities must remain eligible for retention tie-breaks.
      f.current = gadgets(f.current,List.of(PassiveGadgetRules.id(2)));
      var locks = switch (trial % 6) {
        case 0 -> new BuildSelection(id(1),null,null,null,List.of());
        case 1 -> new BuildSelection(null,id(20),null,null,List.of());
        case 2 -> new BuildSelection(null,null,id(21),null,List.of());
        case 3 -> new BuildSelection(null,null,null,f.current.tirePartId(),List.of());
        case 4 -> gadgets(EMPTY,List.of(PassiveGadgetRules.id(2)));
        default -> EMPTY;
      };
      var owned = new CollectionExclusions(trial % 2 == 0 ? Set.of(id(3)) : Set.of(),
          trial % 5 == 0 ? Set.of(id(11)) : Set.of(), trial % 3 == 0 ? Set.of(PassiveGadgetRules.id(18)) : Set.of());
      for (var mode : RecommendationMode.values()) {
        var priorities = trial % 2 == 0 ? ORDER : ORDER.reversed();
        if (mode == RecommendationMode.BALANCED) priorities = priorities.subList(0,3);
        var losses = new EnumMap<StatPriority,BigDecimal>(StatPriority.class);
        priorities.forEach(s -> losses.put(s,BigDecimal.valueOf(trialLoss(context))));
        var secondary = ORDER.stream().filter(s -> !losses.containsKey(s)).toList();
        var request = new RecommendationRequest(id(99),f.type,priorities,f.current,locks,mode,
            mode == RecommendationMode.BALANCED ? new BalancedConfiguration(losses,secondary) : null,
            RecommendationBasis.CURRENT_SCENARIO,context);
        var expected = RecommendationExhaustiveOracle.solve(f.catalog(),request,owned);
        var actual = new BuildRecommendationSolver(f.catalog(),request,BUDGET,owned).solve();
        assertNotNull(expected); assertEquals(ESTABLISHED,actual.outcome());
        assertEquals(expected.selection(),actual.selection(),"Trial " + trial + " " + mode);
        assertEquals(expected.stats(),actual.recommendedStats());
        // Reverse every supplied map's iteration order without changing facts.
        var c = f.catalog();
        var reversed = new RecommendationCatalog(c.version(),reverse(c.racers()),reverse(c.machines()),reverse(c.parts()),
            reverse(c.gadgets()),reverse(c.racerStats()),reverse(c.partStats()),c.rules());
        var reordered = new BuildRecommendationSolver(reversed,request,BUDGET,owned).solve();
        assertEquals(actual.selection(),reordered.selection()); assertEquals(actual.recommendedStats(),reordered.recommendedStats());
        assertEquals(actual.work(),reordered.work());
      }
    }
  }

  private static int trialLoss(ScenarioContext context) { return context.lap() == null ? 100 : context.lap() == 1 ? 0 : 35; }
  private static <T> Map<UUID,T> reverse(Map<UUID,T> source) {
    var result = new LinkedHashMap<UUID,T>(); new ArrayList<>(source.keySet()).reversed().forEach(id -> result.put(id,source.get(id))); return result;
  }
  private static BaseStats randomPoints(Random random) { return points(random.nextInt(6),random.nextInt(6),random.nextInt(6),random.nextInt(6),random.nextInt(6)); }
  private static RecommendationRequest request(Fixture f, ScenarioContext context, BuildSelection locks, RecommendationMode mode) {
    var losses = new EnumMap<StatPriority,BigDecimal>(StatPriority.class); ORDER.forEach(s -> losses.put(s,BigDecimal.ZERO));
    return new RecommendationRequest(id(99),f.type,ORDER,f.current,locks,mode,
        mode == RecommendationMode.BALANCED ? new BalancedConfiguration(losses,List.of()) : null,RecommendationBasis.CURRENT_SCENARIO,context);
  }
  private static RecommendationResult solve(Fixture f, RecommendationRequest request) { return new BuildRecommendationSolver(f.catalog(),request,BUDGET, () -> 0).solve(); }
  private static BuildSelection gadgets(BuildSelection selection,List<UUID> ids) { return new BuildSelection(selection.racerId(),selection.frontPartId(),selection.rearPartId(),selection.tirePartId(),ids); }
  private static UUID id(int n) { return new UUID(0,n); }
  private static class Fixture {
    RacingType type = RacingType.SPEED;
    BuildSelection current = new BuildSelection(id(1),id(20),id(21),id(22),List.of());
    Map<UUID,Racer> racers = new LinkedHashMap<>(Map.of(id(1),new Racer(id(1),"Current",RacingType.SPEED,null)));
    Map<UUID,Machine> machines = new LinkedHashMap<>(Map.of(id(10),new Machine(id(10),"Source",type,null)));
    Map<UUID,MachinePart> parts = new LinkedHashMap<>();
    Map<UUID,BaseStats> racerStats = new LinkedHashMap<>(Map.of(id(1),points(1,1,1,1,1)));
    Map<UUID,BaseStats> partStats = new LinkedHashMap<>();
    Map<UUID,Gadget> gadgets = new LinkedHashMap<>();
    Fixture() { for (var slot : MachinePartType.values()) { var part = id(20 + slot.ordinal());
      parts.put(part,new MachinePart(part,id(10),slot)); partStats.put(part,points(1,1,1,1,1)); } }
    UUID add(int n,int cost) { return add(PassiveGadgetRules.id(n),cost); }
    UUID add(UUID id,int cost) { gadgets.put(id,new Gadget(id,"Gadget " + id,null,cost,null)); return id; }
    RecommendationCatalog catalog() { return new RecommendationCatalog(new GameVersion(id(99),"1.4.1",LocalDate.EPOCH),
        racers,machines,parts,gadgets,racerStats,partStats,RuleFixtures.snapshot()); }
  }
}
