package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.collection.CollectionExclusions;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.importing.RuleFixtures;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import static dev.ringlab.domain.build.recommendation.StatPriority.*;
import static dev.ringlab.domain.build.recommendation.RecommendationResult.Outcome.*;
import static dev.ringlab.domain.gamedata.PassiveGadgetRules.points;
import static dev.ringlab.domain.build.recommendation.BalancedConfigurationTest.config;
import static org.junit.jupiter.api.Assertions.*;

class BalancedSolverTest {
  private static UUID id(int n) { return new UUID(0,n); }
  private static final BuildSelection EMPTY = new BuildSelection(null,null,null,null,List.of());
  private static final List<StatPriority> ORDER = List.of(BOOST,SPEED,ACCELERATION,HANDLING,POWER);
  private static final BuildRecommendationSolver.Budget BUDGET = new BuildRecommendationSolver.Budget(100_000,Duration.ofSeconds(2));
  private static class Fixture {
    RacingType type = RacingType.SPEED;
    GameVersion version = new GameVersion(id(1),"1.4.1",LocalDate.EPOCH);
    Map<UUID,Racer> racers = new LinkedHashMap<>();
    Map<UUID,Machine> machines = new LinkedHashMap<>();
    Map<UUID,MachinePart> parts = new LinkedHashMap<>();
    Map<UUID,Gadget> gadgets = new LinkedHashMap<>();
    Map<UUID,BaseStats> racerStats = new LinkedHashMap<>(), partStats = new LinkedHashMap<>();
    Fixture() {
      machines.put(id(3),new Machine(id(3),"Machine",type,null));
      racer(2,RacingType.SPEED,BaseStats.ZERO);
      part(10,MachinePartType.FRONT,BaseStats.ZERO); part(11,MachinePartType.REAR,BaseStats.ZERO); part(12,MachinePartType.TIRE,BaseStats.ZERO);
    }
    void racer(int n,RacingType t,BaseStats stats) { racers.put(id(n),new Racer(id(n),"Racer "+n,t,null)); racerStats.put(id(n),stats); }
    void part(int n,MachinePartType slot,BaseStats stats) { parts.put(id(n),new MachinePart(id(n),id(3),slot)); partStats.put(id(n),stats); }
    void gadget(int n,int cost) { var key=PassiveGadgetRules.id(n); gadgets.put(key,new Gadget(key,"Gadget "+n,null,cost,null)); }
    BuildSelection current() { return new BuildSelection(id(2),id(10),id(11),type==RacingType.BOOST?null:id(12),List.of()); }
    RecommendationCatalog catalog() { return new RecommendationCatalog(version,racers,machines,parts,gadgets,racerStats,partStats,RuleFixtures.snapshot()); }
    RecommendationRequest request(BuildSelection current,List<StatPriority> active,String... loss) {
      return new RecommendationRequest(id(1),type,active,current,EMPTY,RecommendationMode.BALANCED,config(active,loss));
    }
    RecommendationResult solve(RecommendationRequest request) { return new BuildRecommendationSolver(catalog(),request,BUDGET).solve(); }
  }

  @Test void orderedSacrificeUsesOnlySurvivorsAndReportsEveryStage() {
    var f=new Fixture();
    f.racerStats.put(id(2),points(80,60,50,0,100));
    f.racer(4,RacingType.SPEED,points(100,70,40,9999,98));
    f.racer(5,RacingType.SPEED,points(92,100,20,0,95));
    f.racer(6,RacingType.SPEED,points(91,80,60,-9999,95));
    f.racer(7,RacingType.SPEED,points(1000,1000,1000,1000,94));
    var result=f.solve(f.request(EMPTY,ORDER.subList(0,4),"5","10","25","50"));
    assertEquals(ESTABLISHED,result.outcome()); assertEquals(id(6),result.selection().racerId());
    assertNull(result.currentStats()); assertTrue(result.balanced().proven());
    var stages=result.balanced().stages(); assertEquals(4,stages.size());
    String[] best={"100","100","100","60"}, floor={"95","90","75","30"};
    for(int i=0;i<4;i++) {
      assertEquals(ORDER.get(i),stages.get(i).stat());
      assertEquals(0,new BigDecimal(best[i]).compareTo(stages.get(i).best()));
      assertEquals(0,new BigDecimal(floor[i]).compareTo(stages.get(i).threshold()));
      assertEquals(5-i,stages.get(i).candidatesBefore()); assertEquals(4-i,stages.get(i).candidatesAfter());
    }
    // Presentation changes cannot change thresholds or the numerical winner.
    assertEquals(result.selection(),f.solve(f.request(f.current(),ORDER.subList(0,4),"5","10","25","50")).selection());
  }

  @Test void exactBoundariesAndSignedBestAreNotRoundedOrClamped() {
    for(var best : List.of("100","-10","0")) {
      var f=new Fixture();
      var loss=best.equals("100")?"5":"20";
      var boundary=best.equals("100")?new BigDecimal("95"):best.equals("-10")?new BigDecimal("-12"):BigDecimal.ZERO;
      f.racerStats.put(id(2),new BaseStats(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,new BigDecimal(best)));
      f.racer(4,RacingType.SPEED,new BaseStats(BigDecimal.TEN,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,boundary));
      f.racer(5,RacingType.SPEED,new BaseStats(new BigDecimal("999"),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,boundary.subtract(new BigDecimal("0.01"))));
      var result=f.solve(f.request(EMPTY,List.of(BOOST,SPEED),loss,"0"));
      assertEquals(id(4),result.selection().racerId());
      assertEquals(2,result.balanced().stages().getFirst().candidatesAfter());
      assertEquals(0,boundary.compareTo(result.balanced().stages().getFirst().threshold()));
    }
  }

  @Test void finalSurvivorsUseActiveOrderAndIgnoreNeverBreaksTies() {
    var f=new Fixture(); f.racerStats.put(id(2),points(90,0,0,-9999,100));
    f.racer(4,RacingType.SPEED,points(100,0,0,9999,98));
    assertEquals(id(2),f.solve(f.request(EMPTY,List.of(BOOST,SPEED),"5","20")).selection().racerId());
    assertEquals(id(4),f.solve(f.request(EMPTY,List.of(SPEED,BOOST),"20","5")).selection().racerId());
    f.racerStats.put(id(4),points(90,0,0,9999,100));
    assertEquals(id(2),f.solve(f.request(EMPTY,List.of(BOOST,SPEED),"5","20")).selection().racerId());
    var current=new BuildSelection(id(4),id(10),id(11),id(12),List.of());
    assertEquals(current,f.solve(f.request(current,List.of())).selection());
    assertTrue(f.solve(f.request(current,List.of())).balanced().stages().isEmpty());
  }

  @Test void emptyPartialLockedAndOwnershipConstrainedDraftsWorkForEveryMachineType() {
    for(var type : RacingType.values()) {
      var f=new Fixture(); f.type=type; f.machines.put(id(3),new Machine(id(3),"Machine",type,null));
      f.racer(4,RacingType.BOOST,points(100,100,100,100,100));
      for(var locks : List.of(EMPTY,new BuildSelection(id(2),null,null,null,List.of()),new BuildSelection(null,id(10),null,null,List.of()))) {
        var request=new RecommendationRequest(id(1),type,ORDER,locks,locks,RecommendationMode.BALANCED,config(ORDER,"0","0","0","0","0"));
        var excluded=new CollectionExclusions(Set.of(id(4)),Set.of(),Set.of());
        var result=new BuildRecommendationSolver(f.catalog(),request,BUDGET,excluded).solve();
        assertEquals(ESTABLISHED,result.outcome()); assertEquals(f.current(),result.selection()); assertNull(result.currentStats());
        assertEquals(0,result.balanced().stages().getFirst().best().signum());
        assertEquals(type==RacingType.BOOST,result.selection().tirePartId()==null);
      }
    }
  }

  @Test void incompleteDataIsUnavailableAndStructuralAbsenceIsDistinct() {
    var f=new Fixture(); f.racerStats.put(id(2),BaseStats.UNKNOWN);
    assertEquals(UNAVAILABLE,f.solve(f.request(EMPTY,List.of(BOOST),"0")).outcome());
    f.parts.clear(); assertEquals(NO_LEGAL_COMPLETION,f.solve(f.request(EMPTY,List.of(BOOST),"0")).outcome());
    f=new Fixture(); f.version=new GameVersion(id(1),"1.3.1",LocalDate.EPOCH);
    assertEquals(UNAVAILABLE,f.solve(f.request(EMPTY,List.of(BOOST),"0")).outcome());
  }

  @Test void everyWorkCutoffWithholdsUnprovenThresholdsAndKeepsLegalCandidates() {
    var f=new Fixture(); f.racer(4,RacingType.BOOST,points(100,100,100,100,100));
    var request=f.request(f.current(),ORDER,"5","10","25","50","0");
    var complete=f.solve(request); assertEquals(ESTABLISHED,complete.outcome());
    boolean sawBest=false;
    for(int limit=1;limit<=complete.work();limit++) {
      var budget=new BuildRecommendationSolver.Budget(limit,Duration.ofSeconds(2));
      var result=new BuildRecommendationSolver(f.catalog(),request,budget).solve();
      var repeated=new BuildRecommendationSolver(f.catalog(),request,budget).solve();
      assertEquals(result.selection(),repeated.selection()); assertEquals(result.outcome(),repeated.outcome());
      if(result.outcome()==ESTABLISHED) continue;
      sawBest|=result.outcome()==BEST_FOUND;
      assertTrue(result.outcome()==BEST_FOUND||result.outcome()==LIMIT_WITHOUT_CANDIDATE);
      assertFalse(result.balanced().proven()); assertTrue(result.balanced().stages().isEmpty());
      if(result.selection()!=null) assertNotNull(result.selection().frontPartId());
    }
    assertTrue(sawBest);
    var clock=new AtomicLong();
    var timed=new BuildRecommendationSolver(f.catalog(),request,new BuildRecommendationSolver.Budget(100_000,Duration.ofNanos(1)),clock::getAndIncrement).solve();
    assertEquals(LIMIT_WITHOUT_CANDIDATE,timed.outcome()); assertFalse(timed.balanced().proven());
    Thread.currentThread().interrupt();
    try { assertEquals(LIMIT_WITHOUT_CANDIDATE,f.solve(request).outcome()); } finally { Thread.interrupted(); }
  }

  @Test void deterministicCatalogsMatchIndependentOracleAndStrictAtZeroLoss() {
    var random=new Random(310196);
    for(int trial=0;trial<240;trial++) {
      var f=new Fixture(); f.type=RacingType.values()[trial%5];
      f.machines.put(id(3),new Machine(id(3),"Machine",f.type,null));
      f.racerStats.put(id(2),randomStats(random));
      for(int r=0;r<1+trial%3;r++) f.racer(4+r,RacingType.values()[(trial+r)%5],randomStats(random));
      for(int slot=0;slot<3;slot++) for(int n=0;n<1+trial%2;n++)
        f.part(20+slot*4+n,MachinePartType.values()[slot],randomStats(random));
      f.machines.put(id(8),new Machine(id(8),"Optional source",f.type,null));
      f.parts.put(id(32),new MachinePart(id(32),id(8),MachinePartType.FRONT));
      f.partStats.put(id(32),randomStats(random));
      for(int g:new int[]{52,53,54,11,60}) f.gadget(g,g==11?2:1);
      var ids=trial%3==0?List.of(PassiveGadgetRules.id(52)):List.<UUID>of();
      var current=trial%4==0?EMPTY:new BuildSelection(id(2),id(10),id(11),f.type==RacingType.BOOST?null:id(12),ids);
      var locks=new BuildSelection(trial%7==0?id(2):null,trial%4==0?id(10):null,
          trial%6==0?id(11):null,trial%8==0&&f.type!=RacingType.BOOST?id(12):null,trial%3==0?current.gadgetIds():List.of());
      if (current == EMPTY) current=locks;
      var owned=new CollectionExclusions(trial%4==1?Set.of(id(4)):Set.of(),trial%3==1?Set.of(id(8)):Set.of(),trial%6==0?Set.of(PassiveGadgetRules.id(53)):Set.of());
      var scope=GadgetRecommendationScope.values()[trial%2];
      var priorities=new ArrayList<>(ORDER); Collections.shuffle(priorities,random);
      var losses=new String[5]; String[] settings={"0","0.1","5","10","25","50","99.99","100"};
      for(int i=0;i<5;i++) losses[i]=trial%10==0?"100":settings[random.nextInt(settings.length)];
      var request=new RecommendationRequest(id(1),f.type,priorities,current,locks,RecommendationMode.BALANCED,config(priorities,losses),scope);
      var expected=RecommendationExhaustiveOracle.solve(f.catalog(),request,owned);
      var actual=new BuildRecommendationSolver(f.catalog(),request,BUDGET,owned).solve();
      assertEquals(ESTABLISHED,actual.outcome(),"trial "+trial);
      assertEquals(expected.selection(),actual.selection(),"oracle trial "+trial);
      assertEquals(expected.stats(),actual.recommendedStats(),"stats trial "+trial);
      var zero=new RecommendationRequest(id(1),f.type,priorities,current,locks,RecommendationMode.BALANCED,config(priorities,"0","0","0","0","0"),scope);
      var strict=new RecommendationRequest(id(1),f.type,priorities,current,locks,RecommendationMode.STRICT,null,scope);
      var z=new BuildRecommendationSolver(f.catalog(),zero,BUDGET,owned).solve();
      var s=new BuildRecommendationSolver(f.catalog(),strict,BUDGET,owned).solve();
      assertEquals(ESTABLISHED,z.outcome()); assertEquals(ESTABLISHED,s.outcome());
      assertEquals(s.recommendedStats(),z.recommendedStats(),"zero vector "+trial);
      assertEquals(s.selection(),z.selection(),"zero convenience "+trial);
      assertEquals(RecommendationExhaustiveOracle.solve(f.catalog(),zero,owned).selection(),z.selection());
      // Reverse every catalog's input order; maxima, counts and the winner must be identical.
      f.racers=reversed(f.racers); f.parts=reversed(f.parts); f.gadgets=reversed(f.gadgets);
      var reordered=new BuildRecommendationSolver(f.catalog(),request,BUDGET,owned).solve();
      assertEquals(actual.selection(),reordered.selection()); assertEquals(actual.balanced(),reordered.balanced());
    }
  }
  private static BaseStats randomStats(Random random) {
    var values=new BigDecimal[5];
    for(int i=0;i<5;i++) values[i]=BigDecimal.valueOf(random.nextInt(401)-200,2);
    return new BaseStats(values[0],values[1],values[2],values[3],values[4]);
  }
  private static <T> Map<UUID,T> reversed(Map<UUID,T> source) {
    var result=new LinkedHashMap<UUID,T>(); var keys=new ArrayList<>(source.keySet()); Collections.reverse(keys);
    keys.forEach(key -> result.put(key,source.get(key))); return result;
  }
}
