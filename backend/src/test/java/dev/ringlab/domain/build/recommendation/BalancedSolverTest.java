package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.build.GadgetPlate;
import dev.ringlab.domain.gamedata.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static dev.ringlab.domain.build.recommendation.StatPriority.*;
import static dev.ringlab.domain.build.recommendation.RecommendationResult.Outcome.*;
import static dev.ringlab.domain.gamedata.PassiveGadgetRules.points;
import static dev.ringlab.domain.build.recommendation.BalancedObjectiveTest.config;
import static org.junit.jupiter.api.Assertions.*;

class BalancedSolverTest {
  private static UUID id(int n) { return new UUID(0, n); }
  private static final BuildSelection EMPTY = new BuildSelection(null, null, null, null, List.of());
  private static final BuildRecommendationSolver.Budget BUDGET = new BuildRecommendationSolver.Budget(100_000, Duration.ofSeconds(10));
  private static class Fixture {
    GameVersion version = new GameVersion(id(1), "1.4.1", LocalDate.EPOCH);
    Map<UUID, Racer> racers = new HashMap<>(Map.of(id(2), new Racer(id(2), "Reference", RacingType.SPEED, null)));
    Map<UUID, Machine> machines = new HashMap<>(Map.of(id(3), new Machine(id(3), "Car", RacingType.SPEED, null)));
    Map<UUID, MachinePart> parts = new HashMap<>();
    Map<UUID, Gadget> gadgets = new HashMap<>();
    Map<UUID, BaseStats> racerStats = new HashMap<>(Map.of(id(2), points(80, 80, 80, 80, 80)));
    Map<UUID, BaseStats> partStats = new HashMap<>();
    Fixture() { part(10, MachinePartType.FRONT, BaseStats.ZERO); part(11, MachinePartType.REAR, BaseStats.ZERO); part(12, MachinePartType.TIRE, BaseStats.ZERO); }
    void part(int n, MachinePartType type, BaseStats stats) { parts.put(id(n), new MachinePart(id(n), id(3), type)); partStats.put(id(n), stats); }
    void gadget(int n, int cost) { var uuid = PassiveGadgetRules.id(n); gadgets.put(uuid, new Gadget(uuid, "Gadget " + n, null, cost, null)); }
    BuildSelection reference() { return new BuildSelection(id(2), id(10), id(11), id(12), List.of()); }
    RecommendationCatalog catalog() { return new RecommendationCatalog(version, racers, machines, parts, gadgets, racerStats, partStats, dev.ringlab.importing.RuleFixtures.snapshot()); }
    RecommendationRequest request(BuildSelection current, List<StatPriority> active, String... losses) {
      return new RecommendationRequest(id(1), RacingType.SPEED, active, current, EMPTY, RecommendationMode.BALANCED, config(active, losses));
    }
    RecommendationResult solve(RecommendationRequest request) { return new BuildRecommendationSolver(catalog(), request, BUDGET).solve(); }
  }

  @Test void jointSearchKeepsAWeakerPartNeededToMeetTheCompleteFloor() {
    var f = new Fixture(); f.part(20, MachinePartType.FRONT, points(30, 0, 0, 0, -6));
    f.part(21, MachinePartType.REAR, points(-5, 0, 0, 0, 2));
    var request = f.request(f.reference(), List.of(BOOST, SPEED), "5", "10");
    var result = f.solve(request);
    assertEquals(ESTABLISHED, result.outcome());
    assertEquals(id(20), result.selection().frontPartId()); assertEquals(id(21), result.selection().rearPartId());
    assertEquals(points(105, 80, 80, 80, 76), result.recommendedStats());
    assertEquals(exhaustive(f, request), result.selection());
    assertEquals(result.selection(), f.solve(request).selection());
    assertEquals(result.currentStats(), f.solve(request).currentStats());
    assertEquals(0, result.balanced().minimum().get(BOOST).compareTo(BigDecimal.valueOf(76)));
  }

  @Test void secondaryTotalWinsBeforeConvenienceAndClaimsOnlyObservedTies() {
    var f = new Fixture(); f.part(20, MachinePartType.FRONT, points(0, 0, 0, 65, 0));
    var request = f.request(f.reference(), List.of(BOOST, SPEED, ACCELERATION, HANDLING), "0", "0", "0", "0");
    var result = f.solve(request);
    assertEquals(id(20), result.selection().frontPartId());
    assertTrue(result.balanced().secondaryTieBreakDecided());
    f.part(20, MachinePartType.FRONT, points(0, 0, 0, 65, 1));
    assertFalse(f.solve(request).balanced().secondaryTieBreakDecided());
    f.part(20, MachinePartType.FRONT, BaseStats.ZERO);
    assertEquals(f.reference(), f.solve(request).selection()); assertTrue(f.solve(request).alreadyBest());
  }

  @Test void zeroUnsupportedIncompleteNegativeAndUnknownSecondaryAreExplicit() {
    var f = new Fixture(); f.racerStats.put(id(2), BaseStats.ZERO);
    assertEquals(ESTABLISHED, f.solve(f.request(f.reference(), List.of(BOOST), "100")).outcome());
    assertEquals(UNAVAILABLE, f.solve(f.request(EMPTY, List.of(BOOST), "0")).outcome());
    f.racerStats.put(id(2), points(0, 0, 0, -1, 80));
    assertEquals(UNAVAILABLE, f.solve(f.request(f.reference(), List.of(BOOST), "0")).outcome());
    f.racerStats.put(id(2), new BaseStats(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, BigDecimal.TEN));
    assertEquals(UNAVAILABLE, f.solve(f.request(f.reference(), List.of(BOOST), "0")).outcome());
    f.racerStats.put(id(2), points(80, 80, 80, 80, 80)); f.gadget(1, 1);
    var unsupported = new BuildSelection(id(2), id(10), id(11), id(12), List.of(PassiveGadgetRules.id(1)));
    f.gadgets.put(id(99), new Gadget(id(99), "Unreviewed", null, 1, null));
    unsupported = new BuildSelection(id(2), id(10), id(11), id(12), List.of(id(99)));
    assertEquals(UNAVAILABLE, f.solve(f.request(unsupported, List.of(BOOST), "0")).outcome());
    f.version = new GameVersion(id(1), "1.3.1", LocalDate.EPOCH);
    assertEquals(UNAVAILABLE, f.solve(f.request(f.reference(), List.of(BOOST), "0")).outcome());
  }

  @Test void charmyAncientThroneRemixCanUseBalancedWithUtilityGadgetsLockedOrUnlocked() {
    var f = new Fixture();
    f.racers.put(id(2),new Racer(id(2),"Charmy Bee",RacingType.ACCELERATION,null));
    f.machines.put(id(3),new Machine(id(3),"Ancient Throne",RacingType.HANDLING,null));
    f.racerStats.put(id(2),points(14,18,10,11,7));
    f.partStats.replaceAll((key, value) -> points(5,16,17,8,14));
    var gadgets = List.of(new Gadget(PassiveGadgetRules.id(7),"Damage Evolution",null,1,null),
        new Gadget(PassiveGadgetRules.id(4),"Invincible Finish",null,3,null),
        new Gadget(PassiveGadgetRules.id(2),"Perfect Landing",null,1,null));
    gadgets.forEach(gadget -> f.gadgets.put(gadget.id(),gadget));
    var current = new BuildSelection(id(2),id(10),id(11),id(12),gadgets.stream().map(Gadget::id).toList());
    var priorities = List.of(ACCELERATION,HANDLING,BOOST,POWER,SPEED);
    for (var locks : List.of(EMPTY,current)) {
      var request = new RecommendationRequest(id(1),RacingType.HANDLING,priorities,current,locks,
          RecommendationMode.BALANCED,config(priorities,"0","0","0","0","0"));
      var result = f.solve(request);
      assertEquals(ESTABLISHED,result.outcome());
      assertEquals(points(29,66,61,35,49),result.currentStats());
      assertEquals(result.currentStats(),result.recommendedStats());
      assertEquals(current,result.selection());
      assertTrue(result.alreadyBest());
    }
  }

  @Test void limitsKeepIncumbentAndNeverClaimInfeasibilityOrAnEstablishedSecondaryDecision() {
    var f = new Fixture(); var request = f.request(f.reference(), List.of(BOOST), "0");
    var result = new BuildRecommendationSolver(f.catalog(), request, new BuildRecommendationSolver.Budget(1, Duration.ofSeconds(10))).solve();
    assertEquals(BEST_FOUND, result.outcome()); assertEquals(f.reference(), result.selection()); assertFalse(result.alreadyBest());
    assertFalse(result.balanced().secondaryTieBreakDecided());
    var clock = new AtomicLong();
    result = new BuildRecommendationSolver(f.catalog(), request, new BuildRecommendationSolver.Budget(100, Duration.ofNanos(1)), clock::getAndIncrement).solve();
    assertEquals(LIMIT_WITHOUT_CANDIDATE, result.outcome());
    Thread.currentThread().interrupt();
    try { assertEquals(LIMIT_WITHOUT_CANDIDATE, f.solve(request).outcome()); } finally { Thread.interrupted(); }
    f.machines.put(id(4), new Machine(id(4), "Board", RacingType.BOOST, null));
    f.parts.put(id(30), new MachinePart(id(30), id(4), MachinePartType.FRONT)); f.partStats.put(id(30), points(0, 0, 0, 0, -1));
    f.parts.put(id(31), new MachinePart(id(31), id(4), MachinePartType.REAR)); f.partStats.put(id(31), BaseStats.ZERO);
    request = new RecommendationRequest(id(1), RacingType.BOOST, List.of(BOOST), f.reference(), EMPTY, RecommendationMode.BALANCED, config(List.of(BOOST), "0"));
    assertEquals(NO_FEASIBLE_CANDIDATE, f.solve(request).outcome());
    assertEquals(LIMIT_WITHOUT_CANDIDATE, new BuildRecommendationSolver(f.catalog(), request, new BuildRecommendationSolver.Budget(1, Duration.ofSeconds(10))).solve().outcome());
    var tireLock = new BuildSelection(null, null, null, id(12), List.of());
    var conflict = new RecommendationRequest(id(1), RacingType.BOOST, List.of(BOOST), f.reference(), tireLock, RecommendationMode.BALANCED, config(List.of(BOOST), "0"));
    assertThrows(IllegalArgumentException.class, () -> f.solve(conflict));
  }

  @Test void independentExhaustiveOracleMatchesAllComparatorStagesWithTypesGadgetsAndLocks() {
    var random = new Random(196);
    for (int trial = 0; trial < 24; trial++) {
      var f = new Fixture();
      f.racers.put(id(5), new Racer(id(5), "Other", RacingType.ACCELERATION, null));
      f.racerStats.put(id(5), points(80, 80, 80, 80, 80));
      for (int slot = 0; slot < 3; slot++) f.part(20 + slot, MachinePartType.values()[slot],
          trial % 6 == 0 ? BaseStats.ZERO : points(random.nextInt(11)-5, random.nextInt(11)-5, random.nextInt(11)-5, random.nextInt(11)-5, random.nextInt(11)-5));
      for (int gadget : new int[] {52, 53, 54, 20, 45, 46}) f.gadget(gadget, gadget == 46 ? 2 : 1);
      var current = new BuildSelection(id(2), id(10), id(11), id(12), List.of(PassiveGadgetRules.id(45)));
      var active = trial % 2 == 0 ? List.of(BOOST, SPEED, ACCELERATION) : List.of(SPEED, BOOST, HANDLING);
      var locks = trial % 3 == 0 ? new BuildSelection(id(2), id(10), null, null, current.gadgetIds()) : EMPTY;
      var request = new RecommendationRequest(id(1), RacingType.SPEED, active, current, locks, RecommendationMode.BALANCED, config(active, "5", "10", "25"));
      var result = f.solve(request);
      assertEquals(ESTABLISHED, result.outcome(), "trial " + trial);
      assertEquals(exhaustive(f, request), result.selection(), "trial " + trial);
    }
  }

  // Deliberately independent: enumerate the full Cartesian product and every gadget subset,
  // check complete loadout floors and use fractions via cross multiplication (no production objective).
  private static BuildSelection exhaustive(Fixture f, RecommendationRequest request) {
    BaseStats baseline = evaluate(f, request.current()); BaseStats winningStats = null; BuildSelection winner = null;
    var gadgets = f.gadgets.values().stream().sorted(Comparator.comparing(g -> g.id().toString())).toList();
    for (var racer : f.racers.values()) for (var front : f.parts.values()) if (front.type() == MachinePartType.FRONT)
      for (var rear : f.parts.values()) if (rear.type() == MachinePartType.REAR)
        for (var tire : f.parts.values()) if (tire.type() == MachinePartType.TIRE)
          for (int mask = 0; mask < (1 << gadgets.size()); mask++) {
            var ids = new ArrayList<UUID>();
            for (int i = 0; i < gadgets.size(); i++) if ((mask & (1 << i)) != 0) ids.add(gadgets.get(i).id());
            var ordered = new ArrayList<UUID>(); request.current().gadgetIds().stream().filter(ids::contains).forEach(ordered::add);
            ids.stream().filter(id -> !ordered.contains(id)).forEach(ordered::add);
            var candidate = new BuildSelection(racer.id(), front.id(), rear.id(), tire.id(), ordered);
            var locks = request.locked();
            if ((locks.racerId() != null && !locks.racerId().equals(racer.id())) || (locks.frontPartId() != null && !locks.frontPartId().equals(front.id()))
                || !ids.containsAll(locks.gadgetIds()) || !GadgetPlate.canFit(ids.stream().map(id -> f.gadgets.get(id).slotCost()).toList())) continue;
            BaseStats stats = evaluate(f, candidate); if (stats == null) continue;
            if (request.priorities().stream().anyMatch(s -> s.value(stats).multiply(BigDecimal.valueOf(100)).compareTo(
                s.value(baseline).multiply(BigDecimal.valueOf(100).subtract(request.balanced().maximumLossPercent().get(s)))) < 0)) continue;
            if (winner == null || oracleCompare(f, request, baseline, candidate, stats, winner, winningStats) > 0) { winner = candidate; winningStats = stats; }
          }
    return winner;
  }
  private static BaseStats evaluate(Fixture f, BuildSelection s) {
    var result = PassiveStatsCalculator.calculate(dev.ringlab.importing.RuleFixtures.snapshot(), BaseStatsBreakdown.calculate(s.racerId(), s.frontPartId(), s.rearPartId(), s.tirePartId(), f.racerStats, f.partStats),
        "1.4.1", f.racers.get(s.racerId()).racingType(), RacingType.SPEED, s.gadgetIds().stream().map(f.gadgets::get).toList(), true);
    return result.coverage() == PassiveStatsResult.Coverage.CALCULATED ? result.adjusted() : null;
  }
  private static int oracleCompare(Fixture f, RecommendationRequest r, BaseStats b, BuildSelection l, BaseStats ls, BuildSelection right, BaseStats rs) {
    BigDecimal numerator = BigDecimal.ZERO, denominator = BigDecimal.ONE;
    for (int i = 0; i < r.priorities().size(); i++) {
      var s = r.priorities().get(i); var d = s.value(b).signum() == 0 ? BigDecimal.ONE : s.value(b);
      numerator = numerator.multiply(d).add(s.value(ls).subtract(s.value(rs)).multiply(BigDecimal.valueOf(r.priorities().size()-i)).multiply(denominator));
      denominator = denominator.multiply(d);
    }
    if (numerator.signum() != 0) return numerator.signum();
    for (var s : r.priorities()) { int c = s.value(ls).compareTo(s.value(rs)); if (c != 0) return c; }
    int secondary = r.balanced().secondary().stream().map(s -> s.value(ls).subtract(s.value(rs))).reduce(BigDecimal.ZERO, BigDecimal::add).signum();
    if (secondary != 0) return secondary;
    int changes = Integer.compare(changes(right, r.current()), changes(l, r.current())); if (changes != 0) return changes;
    int additions = Long.compare(additions(right, r.current()), additions(l, r.current())); if (additions != 0) return additions;
    int cost = Integer.compare(right.gadgetIds().stream().mapToInt(id -> f.gadgets.get(id).slotCost()).sum(), l.gadgetIds().stream().mapToInt(id -> f.gadgets.get(id).slotCost()).sum());
    return cost != 0 ? cost : key(right).compareTo(key(l));
  }
  private static long additions(BuildSelection s, BuildSelection b) { return s.gadgetIds().stream().filter(id -> !b.gadgetIds().contains(id)).count(); }
  private static int changes(BuildSelection s, BuildSelection b) {
    return (Objects.equals(s.racerId(),b.racerId()) ? 0 : 1) + (Objects.equals(s.frontPartId(),b.frontPartId()) ? 0 : 1)
        + (Objects.equals(s.rearPartId(),b.rearPartId()) ? 0 : 1) + (Objects.equals(s.tirePartId(),b.tirePartId()) ? 0 : 1)
        + (int)additions(s,b) + (int)b.gadgetIds().stream().filter(id -> !s.gadgetIds().contains(id)).count();
  }
  private static String key(BuildSelection s) { return s.racerId()+"/"+s.frontPartId()+"/"+s.rearPartId()+"/"+s.tirePartId()+"/"+s.gadgetIds().stream().sorted(Comparator.comparing(UUID::toString)).toList(); }
}
