package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.build.GadgetPlate;
import dev.ringlab.domain.gamedata.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.ringlab.domain.build.recommendation.RecommendationResult.Outcome.*;
import static dev.ringlab.domain.gamedata.PassiveGadgetRules.points;

class BuildRecommendationSolverTest {
  @Test void verifiedTunerKitPairIsRetainedAndDiscoveredInStrictAndBalancedSearch() {
    var f = new Fixture(); f.addGadget(55,1); f.addGadget(34,3);
    f.machines.put(id(5),new Machine(id(5),"Acceleration source",RacingType.ACCELERATION,null));
    f.racerStats.put(RACER,points(55,62,26,51,31));
    var pair = List.of(PassiveGadgetRules.id(55),PassiveGadgetRules.id(34));
    var losses = new EnumMap<StatPriority,BigDecimal>(StatPriority.class);
    ORDER.forEach(stat -> losses.put(stat,BigDecimal.valueOf(100)));
    for (var mode : RecommendationMode.values()) for (var order : List.of(pair,pair.reversed())) {
      var current = selection(order);
      var config = mode == RecommendationMode.BALANCED ? new BalancedConfiguration(losses,List.of()) : null;
      var locked = new RecommendationRequest(VERSION,RacingType.ACCELERATION,ORDER,current,current,mode,config);
      var retained = solve(f,locked);
      assertEquals(ESTABLISHED,retained.outcome());
      assertEquals(current,retained.selection());
      assertEquals(points(58,105,27,54,32),retained.currentStats());
      assertEquals(retained.currentStats(),retained.recommendedStats());
      // Starting from no gadgets exercises search-prefix pruning, not just the existing baseline.
      var discovered = solve(f,new RecommendationRequest(VERSION,RacingType.ACCELERATION,ORDER,
          selection(List.of()),selection(List.of()),mode,config));
      assertEquals(ESTABLISHED,discovered.outcome());
      assertEquals(new HashSet<>(pair),new HashSet<>(discovered.selection().gadgetIds()));
      assertEquals(points(58,105,27,54,32),discovered.recommendedStats());
    }
    f.addGadget(54,1);
    var unresolved = selection(List.of(PassiveGadgetRules.id(54),PassiveGadgetRules.id(34)));
    for (var mode : RecommendationMode.values()) {
      var result = solve(f,new RecommendationRequest(VERSION,RacingType.ACCELERATION,ORDER,unresolved,unresolved,mode,
          mode == RecommendationMode.BALANCED ? new BalancedConfiguration(losses,List.of()) : null));
      assertEquals(UNAVAILABLE,result.outcome());
      assertNull(result.selection());
    }
  }
  @Test void exclusionsFilterEveryPoolInBothModesWithoutChangingReferenceStats() {
    var f = new Fixture(); f.addGadget(45, 1);
    f.machines.put(id(6), new Machine(id(6), "Alternative", RacingType.SPEED, null));
    for (var type : MachinePartType.values()) {
      var partId = id(30 + type.ordinal());
      f.parts.put(partId, new MachinePart(partId, id(6), type));
      f.partStats.put(partId, points(1, 1, 1, 1, 1));
    }
    var current = selection(List.of(PassiveGadgetRules.id(45)));
    var losses = new EnumMap<StatPriority, BigDecimal>(StatPriority.class);
    ORDER.forEach(stat -> losses.put(stat, BigDecimal.valueOf(100)));
    for (var mode : RecommendationMode.values()) {
      var request = new RecommendationRequest(VERSION, RacingType.SPEED, ORDER, current, EMPTY, mode,
          mode == RecommendationMode.BALANCED ? new BalancedConfiguration(losses, List.of()) : null);
      var allOwned = new BuildRecommendationSolver(f.snapshot(), request, BUDGET).solve();
      for (var excluded : List.of(
          new dev.ringlab.domain.collection.CollectionExclusions(Set.of(RACER), Set.of(), Set.of()),
          new dev.ringlab.domain.collection.CollectionExclusions(Set.of(), Set.of(id(5)), Set.of()),
          new dev.ringlab.domain.collection.CollectionExclusions(Set.of(), Set.of(), Set.of(PassiveGadgetRules.id(45))))) {
        var result = new BuildRecommendationSolver(f.snapshot(), request, BUDGET, excluded).solve();
        assertEquals(ESTABLISHED, result.outcome());
        assertEquals(allOwned.currentStats(), result.currentStats());
        assertFalse(result.alreadyBest());
        assertTrue(excluded.racerAvailable(result.selection().racerId()));
        for (var part : List.of(result.selection().frontPartId(), result.selection().rearPartId(), result.selection().tirePartId()))
          assertTrue(excluded.machineAvailable(f.parts.get(part).sourceMachineId()));
        assertTrue(result.selection().gadgetIds().stream().allMatch(excluded::gadgetAvailable));
        var locked = new RecommendationRequest(VERSION, RacingType.SPEED, ORDER, current, current, mode,
            mode == RecommendationMode.BALANCED ? new BalancedConfiguration(losses, List.of()) : null);
        var conflict = assertThrows(IllegalArgumentException.class,
            () -> new BuildRecommendationSolver(f.snapshot(), locked, BUDGET, excluded).solve());
        assertTrue(conflict.getMessage().contains("Not owned — locked:"));
        assertTrue(conflict.getMessage().contains("Racer") || conflict.getMessage().contains("Speed source") || conflict.getMessage().contains("Gadget 45"));
      }
      assertEquals(allOwned.selection(), new BuildRecommendationSolver(f.snapshot(), request, BUDGET,
          dev.ringlab.domain.collection.CollectionExclusions.NONE).solve().selection());
    }
  }

  @Test void unavailableMixedSourceAndBalancedZeroLossReferenceCannotBypassFeasibility() {
    var f = new Fixture();
    f.machines.put(id(6), new Machine(id(6), "Mixed rear", RacingType.SPEED, null));
    f.parts.put(REAR, new MachinePart(REAR, id(6), MachinePartType.REAR));
    var excluded = new dev.ringlab.domain.collection.CollectionExclusions(Set.of(), Set.of(id(6)), Set.of());
    var current = selection(List.of());
    assertEquals(NO_LEGAL_COMPLETION, new BuildRecommendationSolver(f.snapshot(), request(current, EMPTY, RacingType.SPEED), BUDGET, excluded).solve().outcome());
    var losses = new EnumMap<StatPriority, BigDecimal>(StatPriority.class);
    ORDER.forEach(stat -> losses.put(stat, BigDecimal.ZERO));
    var request = new RecommendationRequest(VERSION, RacingType.SPEED, ORDER, current, EMPTY,
        RecommendationMode.BALANCED, new BalancedConfiguration(losses, List.of()));
    var racerExcluded = new dev.ringlab.domain.collection.CollectionExclusions(Set.of(RACER), Set.of(), Set.of());
    var result = new BuildRecommendationSolver(f.snapshot(), request, BUDGET, racerExcluded).solve();
    assertEquals(NO_FEASIBLE_CANDIDATE, result.outcome());
    assertNotNull(result.currentStats()); assertNull(result.selection());
  }
  static final UUID VERSION = id(1), RACER = id(2), OTHER = id(3), FRONT = id(10), REAR = id(11), TIRE = id(12);
  static final List<StatPriority> ORDER = List.of(StatPriority.ACCELERATION, StatPriority.SPEED,
      StatPriority.HANDLING, StatPriority.BOOST, StatPriority.POWER);
  static final BuildSelection EMPTY = new BuildSelection(null, null, null, null, List.of());
  static final BuildRecommendationSolver.Budget BUDGET = new BuildRecommendationSolver.Budget(1_000_000, Duration.ofSeconds(20));
  private static UUID id(int id) { return new UUID(0, id); }
  private static BuildSelection selection(List<UUID> gadgets) { return new BuildSelection(RACER, FRONT, REAR, TIRE, gadgets); }
  private static RecommendationRequest request(BuildSelection current, BuildSelection locks, RacingType type) {
    return new RecommendationRequest(VERSION, type, ORDER, current, locks);
  }
  private static RecommendationResult solve(Fixture fixture, RecommendationRequest request) {
    return new BuildRecommendationSolver(fixture.snapshot(), request, BUDGET).solve();
  }

  @Test void strictOrderUsesExactDecimalsAndNeverCompensatesAnEarlierLoss() {
    assertTrue(StatPriority.compare(points(1, 2, 0, 0, 0), points(10000, 1, 1000, 1000, 1000), ORDER) > 0);
    assertTrue(StatPriority.compare(points(2, 2, 0, 0, 0), points(1, 2, 1000, 1000, 1000), ORDER) > 0);
    assertTrue(StatPriority.compare(points(0, 0, 0, 10000, 1), points(0, 0, 0, 0, 2), ORDER) < 0);
    var precise = new BaseStats(new BigDecimal("1.0001"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    assertTrue(StatPriority.compare(precise, points(1, 0, 0, 0, 0), ORDER) > 0);
    assertEquals(0, StatPriority.compare(new BaseStats(new BigDecimal("1.0"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO), points(1, 0, 0, 0, 0), ORDER));
    assertFalse(StatPriority.complete(null)); assertFalse(StatPriority.complete(BaseStats.UNKNOWN));
    var f = new Fixture(); f.racerStats.put(RACER, points(10000, 1, 0, 0, 0)); f.racerStats.put(OTHER, points(1, 2, 0, 0, 0));
    var result = solve(f, request(selection(List.of()), EMPTY, RacingType.SPEED));
    assertEquals(OTHER, result.selection().racerId()); assertTrue(result.reason().contains("ACCELERATION"));
  }

  @Test void deterministicTiesKeepBaselineAndDecimalScaleDoesNotChangeWinner() {
    var f = new Fixture();
    f.parts.put(id(9), new MachinePart(id(9), id(5), MachinePartType.FRONT)); f.partStats.put(id(9), points(1, 1, 1, 1, 1));
    f.racerStats.put(OTHER, points(1, 1, 1, 1, 1));
    var baseline = selection(List.of());
    var result = solve(f, request(baseline, EMPTY, RacingType.SPEED));
    assertEquals(baseline, result.selection()); assertTrue(result.alreadyBest());
    assertTrue(result.reason().contains("already best")); assertEquals(result.currentStats(), result.recommendedStats());
    var empty = solve(f, request(EMPTY, EMPTY, RacingType.SPEED));
    assertEquals(id(9), empty.selection().frontPartId()); assertEquals(RACER, empty.selection().racerId());
    for (int repeat = 0; repeat < 5; repeat++) assertEquals(empty.selection(), solve(f, request(EMPTY, EMPTY, RacingType.SPEED)).selection());
  }

  @Test void everyLockSurvivesAndMixedSourcesAndDifferentRacerTypesAreLegal() {
    var f = new Fixture(); f.addGadget(52, 1); f.addGadget(53, 1);
    f.racers.put(RACER, new Racer(RACER, "Boost racer", RacingType.BOOST, null));
    f.machines.put(id(6), new Machine(id(6), "Other speed source", RacingType.SPEED, null));
    f.parts.put(REAR, new MachinePart(REAR, id(6), MachinePartType.REAR));
    var current = selection(List.of(PassiveGadgetRules.id(53), PassiveGadgetRules.id(52)));
    var result = solve(f, request(current, current, RacingType.SPEED));
    assertEquals(current, result.selection()); assertTrue(result.alreadyBest());
    assertEquals(points(44, -2, 4, 4, 2), result.recommendedStats());
  }

  @Test void boostUsesTwoPartsAndTireLocksCannotBeDiscarded() {
    var f = new Fixture(); f.addBoard();
    var boost = solve(f, request(selection(List.of()), new BuildSelection(RACER, null, null, null, List.of()), RacingType.BOOST));
    assertEquals(id(20), boost.selection().frontPartId()); assertEquals(id(21), boost.selection().rearPartId());
    assertNull(boost.selection().tirePartId()); assertEquals(points(3, 3, 3, 3, 3), boost.recommendedStats());
    assertNotNull(boost.currentStats());
    var current = boost.selection(); assertTrue(solve(f, request(current, EMPTY, RacingType.BOOST)).alreadyBest());
    assertInvalid(f, request(selection(List.of()), new BuildSelection(null, null, null, TIRE, List.of()), RacingType.BOOST), "Unlock the Tire");
    assertInvalid(f, request(selection(List.of()), new BuildSelection(null, FRONT, null, null, List.of()), RacingType.POWER), "Locked FRONT");
  }

  @Test void duplicateWrongSlotUnknownAndNonSelectedLocksAreRejected() {
    var f = new Fixture(); f.addGadget(52, 1);
    assertInvalid(f, request(new BuildSelection(id(999), FRONT, REAR, TIRE, List.of()), EMPTY, RacingType.SPEED), "Unknown racer");
    assertInvalid(f, request(new BuildSelection(RACER, id(999), REAR, TIRE, List.of()), EMPTY, RacingType.SPEED), "Unknown FRONT");
    assertInvalid(f, request(new BuildSelection(RACER, REAR, FRONT, TIRE, List.of()), EMPTY, RacingType.SPEED), "FRONT slot");
    assertInvalid(f, request(selection(List.of(id(999))), EMPTY, RacingType.SPEED), "Unknown gadget");
    assertInvalid(f, request(selection(List.of(PassiveGadgetRules.id(52), PassiveGadgetRules.id(52))), EMPTY, RacingType.SPEED), "distinct gadget");
    assertInvalid(f, request(EMPTY, new BuildSelection(RACER, null, null, null, List.of()), RacingType.SPEED), "currently selected");
    assertInvalid(f, request(EMPTY, new BuildSelection(null, null, null, null, List.of(PassiveGadgetRules.id(52))), RacingType.SPEED), "currently selected");
    assertInvalid(f, request(selection(Collections.nCopies(7, PassiveGadgetRules.id(52))), EMPTY, RacingType.SPEED), "distinct gadget");
  }

  @Test void plateUsesTwoRowsAndRetainsUtilityWithoutAddingUselessGadgets() {
    var f = new Fixture(); f.addGadget(45, 2); f.addGadget(46, 2); f.addGadget(47, 2);
    var ids = List.of(PassiveGadgetRules.id(45), PassiveGadgetRules.id(46), PassiveGadgetRules.id(47));
    assertInvalid(f, request(selection(ids), selection(ids), RacingType.SPEED), "two-row");
    assertTrue(solve(f, request(EMPTY, EMPTY, RacingType.SPEED)).selection().gadgetIds().isEmpty());
    var retained = selection(ids.subList(0, 2));
    assertEquals(retained, solve(f, request(retained, EMPTY, RacingType.SPEED)).selection());
    var reduced = solve(f, request(selection(ids), EMPTY, RacingType.SPEED));
    assertEquals(2, reduced.selection().gadgetIds().size()); assertNull(reduced.currentStats());
    // Enumerate all feasible subsets independently: same values and change count, then stable ID order.
    var currentOrder = List.of(ids.get(2), ids.get(1), ids.get(0));
    var tied = solve(f, request(selection(currentOrder), EMPTY, RacingType.SPEED));
    var feasible = new ArrayList<List<UUID>>();
    for (int mask = 0; mask < 8; mask++) {
      var subset = new ArrayList<UUID>();
      for (int index = 0; index < 3; index++) if ((mask & (1 << index)) != 0) subset.add(ids.get(index));
      if (GadgetPlate.canFit(subset.stream().map(id -> f.gadgets.get(id).slotCost()).toList())) feasible.add(subset);
    }
    feasible.sort(Comparator.<List<UUID>>comparingInt(List::size).reversed().thenComparing(Object::toString));
    var expectedOrder = currentOrder.stream().filter(feasible.getFirst()::contains).toList();
    assertEquals(selection(expectedOrder), tied.selection());
    f.gadgets.put(ids.getFirst(), new Gadget(ids.getFirst(), "Unknown cost", null, null, null));
    assertInvalid(f, request(selection(List.of(ids.getFirst())), selection(List.of(ids.getFirst())), RacingType.SPEED), "unknown/invalid costs");
  }

  @Test void lockedUnknownEffectsAndUnresolvedStacksAreUnavailableNeverBaseOnly() {
    var f = new Fixture(); f.gadgets.put(id(99), new Gadget(id(99), "Unreviewed", null, 1, null));
    var current = selection(List.of(id(99)));
    var result = solve(f, request(current, current, RacingType.SPEED));
    assertEquals(UNAVAILABLE, result.outcome()); assertNull(result.selection()); assertNull(result.currentStats());
    assertTrue(result.restrictions().stream().anyMatch(s -> s.contains("Unreviewed")));
    f.addGadget(52, 1); f.addGadget(49, 1);
    current = selection(List.of(PassiveGadgetRules.id(52), PassiveGadgetRules.id(49)));
    result = solve(f, request(current, current, RacingType.SPEED));
    assertEquals(UNAVAILABLE, result.outcome()); assertTrue(result.restrictions().stream().anyMatch(s -> s.contains("stacking")));
    f.addGadget(50, 1);
    current = selection(List.of(PassiveGadgetRules.id(52), PassiveGadgetRules.id(50)));
    result = solve(f, request(current, current, RacingType.SPEED));
    assertEquals(ESTABLISHED, result.outcome());
    assertEquals(points(24, 0, 7, 4, 4), result.recommendedStats());
    f.version = new GameVersion(VERSION, "1.3.1", LocalDate.EPOCH);
    assertEquals(UNAVAILABLE, solve(f, request(EMPTY, EMPTY, RacingType.SPEED)).outcome());
  }

  @Test void mixedTunersRemainACompleteLockedReferenceInBothModes() {
    var f = new Fixture();
    var ids = new ArrayList<UUID>();
    for (int number : new int[] {52,53,54,55,58,59}) { f.addGadget(number,1); ids.add(PassiveGadgetRules.id(number)); }
    var current = selection(ids);
    var zeroLosses = new EnumMap<StatPriority,BigDecimal>(StatPriority.class);
    ORDER.forEach(stat -> zeroLosses.put(stat,BigDecimal.ZERO));
    for (var mode : RecommendationMode.values()) {
      var request = new RecommendationRequest(VERSION,RacingType.SPEED,ORDER,current,current,mode,
          mode == RecommendationMode.BALANCED ? new BalancedConfiguration(zeroLosses,List.of()) : null);
      var result = solve(f,request);
      assertEquals(ESTABLISHED,result.outcome());
      assertEquals(current,result.selection());
      assertEquals(points(44,14,4,20,2),result.currentStats());
      assertEquals(result.currentStats(),result.recommendedStats());
    }
  }

  @Test void unknownTypeDoesNotBecomeAZeroEffectAndPenaltiesRemainPenalties() {
    var f = new Fixture(); f.racers.put(RACER, new Racer(RACER, "Unknown type", null, null)); f.addGadget(20, 1);
    var current = selection(List.of(PassiveGadgetRules.id(20)));
    var result = solve(f, request(current, current, RacingType.SPEED));
    assertEquals(UNAVAILABLE, result.outcome()); assertTrue(result.restrictions().stream().anyMatch(s -> s.contains("unknown")));
    var doubleDown = UUID.fromString("f6f75d95-16dd-538d-89b5-49c71cc8a346");
    f.gadgets.put(doubleDown, new Gadget(doubleDown, "Double items", null, 1, null));
    assertFalse(solve(f, request(EMPTY, EMPTY, RacingType.SPEED)).selection().gadgetIds().contains(doubleDown));
    current = selection(List.of(doubleDown));
    result = solve(f, request(current, current, RacingType.SPEED));
    assertEquals(points(-6, -6, -6, -6, -6), result.recommendedStats());
  }

  @Test void missingVerifiedDataAndNoLegalCompletionAreDifferent() {
    var f = new Fixture(); f.partStats.remove(FRONT);
    assertEquals(UNAVAILABLE, solve(f, request(EMPTY, EMPTY, RacingType.SPEED)).outcome());
    f.parts.remove(FRONT);
    assertEquals(NO_LEGAL_COMPLETION, solve(f, request(EMPTY, EMPTY, RacingType.SPEED)).outcome());
    f = new Fixture(); f.racerStats.clear();
    assertEquals(UNAVAILABLE, solve(f, request(EMPTY, EMPTY, RacingType.SPEED)).outcome());
    f.racers.clear(); assertEquals(NO_LEGAL_COMPLETION, solve(f, request(EMPTY, EMPTY, RacingType.SPEED)).outcome());
    f = new Fixture(); f.machines.put(id(5), new Machine(id(5), "Unknown type", null, null));
    assertNull(solve(f, request(selection(List.of()), EMPTY, RacingType.SPEED)).currentStats());
    assertInvalid(f, request(selection(List.of()), selection(List.of()), RacingType.SPEED), "Locked FRONT");
  }

  @Test void workTimeAndInterruptionBudgetsAreHonestAndBaselineIsAnIncumbent() {
    var f = new Fixture(); var one = new BuildRecommendationSolver.Budget(1, Duration.ofSeconds(20));
    var limited = new BuildRecommendationSolver(f.snapshot(), request(EMPTY, EMPTY, RacingType.SPEED), one).solve();
    assertEquals(LIMIT_WITHOUT_CANDIDATE, limited.outcome()); assertEquals(1, limited.work());
    limited = new BuildRecommendationSolver(f.snapshot(), request(selection(List.of()), EMPTY, RacingType.SPEED), one).solve();
    assertEquals(BEST_FOUND, limited.outcome()); assertEquals(selection(List.of()), limited.selection()); assertFalse(limited.alreadyBest());
    var clock = new AtomicLong();
    limited = new BuildRecommendationSolver(f.snapshot(), request(EMPTY, EMPTY, RacingType.SPEED),
        new BuildRecommendationSolver.Budget(10000, Duration.ofNanos(1)), clock::getAndIncrement).solve();
    assertEquals(LIMIT_WITHOUT_CANDIDATE, limited.outcome()); assertEquals(0, limited.work());
    Thread.currentThread().interrupt();
    try { assertEquals(LIMIT_WITHOUT_CANDIDATE, solve(f, request(EMPTY, EMPTY, RacingType.SPEED)).outcome()); }
    finally { Thread.interrupted(); }
  }

  @Test void immutableRequestsRejectMalformedPriorityPermutationsAndBudgets() {
    assertThrows(IllegalArgumentException.class, () -> new RecommendationRequest(null, RacingType.SPEED, ORDER, EMPTY, EMPTY));
    assertThrows(IllegalArgumentException.class, () -> request(EMPTY, EMPTY, null));
    for (List<StatPriority> order : Arrays.asList(null, List.<StatPriority>of(), Collections.nCopies(5, StatPriority.SPEED),
        Arrays.asList(StatPriority.SPEED, StatPriority.ACCELERATION, StatPriority.HANDLING, StatPriority.POWER, null)))
      assertThrows(IllegalArgumentException.class, () -> new RecommendationRequest(VERSION, RacingType.SPEED, order, EMPTY, EMPTY));
    assertThrows(IllegalArgumentException.class, () -> request(null, EMPTY, RacingType.SPEED));
    assertThrows(IllegalArgumentException.class, () -> request(EMPTY, null, RacingType.SPEED));
    assertThrows(IllegalArgumentException.class, () -> new BuildSelection(null, null, null, null, null));
    assertThrows(IllegalArgumentException.class, () -> new BuildSelection(null, null, null, null, Arrays.asList((UUID)null)));
    for (Duration time : Arrays.asList(null, Duration.ZERO, Duration.ofSeconds(-1)))
      assertThrows(IllegalArgumentException.class, () -> new BuildRecommendationSolver.Budget(1, time));
    assertThrows(IllegalArgumentException.class, () -> new BuildRecommendationSolver.Budget(0, Duration.ofSeconds(1)));
    var ids = new ArrayList<UUID>(); var selected = new BuildSelection(null, null, null, null, ids); ids.add(id(99));
    assertTrue(selected.gadgetIds().isEmpty());
    var f = new Fixture(); f.version = new GameVersion(id(99), "1.4.1", LocalDate.EPOCH);
    assertInvalid(f, request(EMPTY, EMPTY, RacingType.SPEED), "Patch does not match");
  }

  @Test void optimizedSearchMatchesIndependentExhaustiveCartesianSearch() {
    Random random = new Random(142);
    for (int trial = 0; trial < 20; trial++) {
      var f = new Fixture();
      f.racers.put(OTHER, new Racer(OTHER, "Other", RacingType.ACCELERATION, null));
      f.racerStats.replaceAll((id, ignored) -> randomStats(random));
      f.partStats.replaceAll((id, ignored) -> randomStats(random));
      for (int slot = 0; slot < 3; slot++) {
        UUID id = id(30 + slot); f.parts.put(id, new MachinePart(id, id(5), MachinePartType.values()[slot]));
        f.partStats.put(id, randomStats(random));
      }
      for (int gadget : new int[] {52, 53, 54, 20, 16, 45}) f.addGadget(gadget, 1);
      var result = solve(f, request(EMPTY, EMPTY, RacingType.SPEED));
      assertEquals(ESTABLISHED, result.outcome());
      BaseStats exhaustiveBest = null;
      var gadgets = new ArrayList<>(f.gadgets.values());
      for (var racer : f.racers.values()) for (var front : f.parts.values()) if (front.type() == MachinePartType.FRONT)
        for (var rear : f.parts.values()) if (rear.type() == MachinePartType.REAR)
          for (var tire : f.parts.values()) if (tire.type() == MachinePartType.TIRE)
            for (int mask = 0; mask < (1 << gadgets.size()); mask++) {
              var selected = new ArrayList<Gadget>();
              for (int index = 0; index < gadgets.size(); index++) if ((mask & (1 << index)) != 0) selected.add(gadgets.get(index));
              if (!GadgetPlate.canFit(selected.stream().map(Gadget::slotCost).toList())) continue;
              var base = BaseStatsBreakdown.calculate(racer.id(), front.id(), rear.id(), tire.id(), f.racerStats, f.partStats);
              var evaluated = PassiveStatsCalculator.calculate(base, "1.4.1", racer.racingType(), RacingType.SPEED, selected, true);
              if (evaluated.coverage() != PassiveStatsResult.Coverage.CALCULATED) continue;
              if (exhaustiveBest == null || StatPriority.compare(evaluated.adjusted(), exhaustiveBest, ORDER) > 0) exhaustiveBest = evaluated.adjusted();
            }
      assertEquals(0, StatPriority.compare(result.recommendedStats(), exhaustiveBest, ORDER), "Trial " + trial);
    }
  }

  private static BaseStats randomStats(Random random) { return points(random.nextInt(12), random.nextInt(12), random.nextInt(12), random.nextInt(12), random.nextInt(12)); }
  private static void assertInvalid(Fixture fixture, RecommendationRequest request, String message) {
    assertTrue(assertThrows(IllegalArgumentException.class, () -> solve(fixture, request)).getMessage().contains(message));
  }

  private static class Fixture {
    GameVersion version = new GameVersion(VERSION, "1.4.1", LocalDate.EPOCH);
    Map<UUID, Racer> racers = new HashMap<>(Map.of(RACER, new Racer(RACER, "Racer", RacingType.SPEED, null), OTHER, new Racer(OTHER, "Other", RacingType.SPEED, null)));
    Map<UUID, Machine> machines = new HashMap<>(Map.of(id(5), new Machine(id(5), "Speed source", RacingType.SPEED, null)));
    Map<UUID, MachinePart> parts = new HashMap<>(Map.of(FRONT, new MachinePart(FRONT, id(5), MachinePartType.FRONT),
        REAR, new MachinePart(REAR, id(5), MachinePartType.REAR), TIRE, new MachinePart(TIRE, id(5), MachinePartType.TIRE)));
    Map<UUID, Gadget> gadgets = new HashMap<>();
    Map<UUID, BaseStats> racerStats = new HashMap<>(Map.of(RACER, points(1, 1, 1, 1, 1), OTHER, points(0, 0, 0, 0, 0)));
    Map<UUID, BaseStats> partStats = new HashMap<>(Map.of(FRONT, points(1, 1, 1, 1, 1), REAR, points(1, 1, 1, 1, 1), TIRE, points(1, 1, 1, 1, 1)));
    void addGadget(int number, Integer cost) { var id = PassiveGadgetRules.id(number); gadgets.put(id, new Gadget(id, "Gadget " + number, null, cost, null)); }
    void addBoard() {
      machines.put(id(7), new Machine(id(7), "Board", RacingType.BOOST, null));
      parts.put(id(20), new MachinePart(id(20), id(7), MachinePartType.FRONT));
      parts.put(id(21), new MachinePart(id(21), id(7), MachinePartType.REAR));
      partStats.put(id(20), points(1, 1, 1, 1, 1)); partStats.put(id(21), points(1, 1, 1, 1, 1));
    }
    RecommendationCatalog snapshot() { return new RecommendationCatalog(version, racers, machines, parts, gadgets, racerStats, partStats); }
  }
}
