package dev.ringlab.application;

import static org.junit.jupiter.api.Assertions.*;
import dev.ringlab.application.gamedata.*;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.port.out.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PassiveStatsServiceTest {
  private final UUID version=UUID.randomUUID(), racer=UUID.randomUUID(), car=UUID.randomUUID(), board=UUID.randomUUID();
  private final UUID front=UUID.randomUUID(), rear=UUID.randomUUID(), tire=UUID.randomUUID(), boardFront=UUID.randomUUID(), boardRear=UUID.randomUUID();
  private final Catalog game=new Catalog();
  private int baseReads;
  private final BaseStatsRepository values=new BaseStatsRepository() {
    public Map<UUID,BaseStats> racerStats(UUID id) { baseReads++;return Map.of(racer,PassiveGadgetRules.points(10,10,10,10,10)); }
    public Map<UUID,BaseStats> machinePartStats(UUID id) {
      baseReads++;
      var value=new BigDecimal("1.25");var stats=new BaseStats(value,value,value,value,value);
      return Map.of(front,stats,rear,stats,tire,stats,boardFront,stats,boardRear,stats);
    }
  };
  private final PassiveStatsService service=new PassiveStatsService(new BaseStatsService(values,game),game);
  private Build build(UUID f,UUID r,UUID t,List<UUID> gadgets) {
    return new Build(UUID.randomUUID(),"Example","",UUID.randomUUID(),racer,f,r,t,version,null,gadgets,Instant.EPOCH,Instant.EPOCH);
  }

  @Test void draftPageAndSnapshotUseTheSameValuesAndCatalogReadsStayBounded() {
    var ids=List.of(PassiveGadgetRules.id(11));
    var draft=service.draft(version,racer,front,rear,tire,ids);
    assertEquals(new BigDecimal("13.75"),draft.base().total().boost());
    assertEquals(new BigDecimal("20.75"),draft.adjusted().boost());
    assertEquals(new BigDecimal("8.75"),draft.adjusted().power());
    baseReads=0; game.catalogReads=0;
    var carBuild=build(front,rear,tire,ids);var boardBuild=build(boardFront,boardRear,null,ids);
    var page=service.buildPage(List.of(carBuild,boardBuild));
    assertEquals(2,baseReads);assertEquals(5,game.catalogReads);
    assertEquals(draft,page.get(carBuild.id()));
    assertEquals(new BigDecimal("12.50"),page.get(boardBuild.id()).base().total().speed());
    assertEquals(new BigDecimal("19.50"),page.get(boardBuild.id()).adjusted().boost());
    var snapshot=PassiveStatsService.resolved(draft.base(),game.findGameVersion(version).orElseThrow(),game.findRacer(racer).orElseThrow(),
        game.findMachinePart(front).orElseThrow(),game.findMachinePart(rear).orElseThrow(),game.findMachinePart(tire).orElseThrow(),
        Map.of(car,game.findMachine(car).orElseThrow()),List.of(game.findGadget(ids.getFirst()).orElseThrow()),true);
    assertEquals(draft,snapshot);
    assertTrue(service.buildPage(List.of()).isEmpty());
  }

  @Test void invalidGadgetIdsDuplicatesAndPlateViolationsAreRejectedForDrafts() {
    assertThrows(NotFoundException.class,()->service.draft(version,racer,front,rear,tire,List.of(UUID.randomUUID())));
    assertThrows(ValidationException.class,()->service.draft(version,racer,front,rear,tire,null));
    assertThrows(ValidationException.class,()->service.draft(version,racer,front,rear,tire,Arrays.asList((UUID)null)));
    assertThrows(ValidationException.class,()->service.draft(version,racer,front,rear,tire,Collections.nCopies(7,PassiveGadgetRules.id(11))));
    assertThrows(ValidationException.class,()->service.draft(version,racer,front,rear,tire,List.of(PassiveGadgetRules.id(11),PassiveGadgetRules.id(11))));
    assertThrows(ValidationException.class,()->service.draft(version,racer,front,rear,tire,List.of(PassiveGadgetRules.id(11),PassiveGadgetRules.id(38),PassiveGadgetRules.id(23))));
  }

  @Test void incompleteAndIncoherentContextsDoNotGuessMachineBonuses() {
    var partial=service.draft(version,racer,front,null,null,List.of(PassiveGadgetRules.id(38)));
    assertEquals(PassiveStatsResult.Status.REQUIRES_SELECTION,partial.effects().getFirst().status());
    var missing=service.draft(null,null,null,null,null,List.of());
    assertEquals(BaseStatsBreakdown.UNKNOWN,missing.base());
    var mixed=build(front,boardRear,tire,List.of(PassiveGadgetRules.id(38)));
    var result=service.buildPage(List.of(mixed)).get(mixed.id());
    assertEquals(PassiveStatsResult.Coverage.INVALID_LOADOUT,result.coverage());
    assertEquals(new BigDecimal("13.75"),result.base().total().speed());
    var unknown=build(front,rear,tire,List.of(UUID.randomUUID()));
    assertEquals(PassiveStatsResult.Coverage.INVALID_LOADOUT,service.buildPage(List.of(unknown)).get(unknown.id()).coverage());
    var tireOnBoard=build(boardFront,boardRear,tire,List.of());
    assertEquals(PassiveStatsResult.Coverage.INVALID_LOADOUT,service.buildPage(List.of(tireOnBoard)).get(tireOnBoard.id()).coverage());
  }

  private class Catalog implements GameDataRepository {
    int catalogReads;
    public List<RaceMap> listRaceMaps() { throw new AssertionError("Maps must not participate in stats"); }
    public List<GameVersion> listGameVersions() { catalogReads++;return List.of(new GameVersion(version,"1.4.1",LocalDate.of(2026,6,24))); }
    public Optional<GameVersion> findGameVersion(UUID id) { return listGameVersions().stream().filter(v->v.id().equals(id)).findFirst(); }
    public List<Racer> listRacers() { catalogReads++;return List.of(new Racer(racer,"Boost racer",RacingType.BOOST,null)); }
    public Optional<Racer> findRacer(UUID id) { return listRacers().stream().filter(r->r.id().equals(id)).findFirst(); }
    public List<Machine> listMachines() { catalogReads++;return List.of(new Machine(car,"Power car",RacingType.POWER,null),new Machine(board,"Board",RacingType.BOOST,null)); }
    public Optional<Machine> findMachine(UUID id) { return listMachines().stream().filter(m->m.id().equals(id)).findFirst(); }
    public List<MachinePart> listMachineParts() { catalogReads++;return List.of(new MachinePart(front,car,MachinePartType.FRONT),
        new MachinePart(rear,car,MachinePartType.REAR),new MachinePart(tire,car,MachinePartType.TIRE),
        new MachinePart(boardFront,board,MachinePartType.FRONT),new MachinePart(boardRear,board,MachinePartType.REAR)); }
    public Optional<MachinePart> findMachinePart(UUID id) { return listMachineParts().stream().filter(p->p.id().equals(id)).findFirst(); }
    public List<Gadget> listGadgets() { catalogReads++;return List.of(new Gadget(PassiveGadgetRules.id(11),"Boost Character Kit",null,3,null),
        new Gadget(PassiveGadgetRules.id(38),"Power Machine Kit",null,3,null),new Gadget(PassiveGadgetRules.id(23),"Panel Combo Kit",null,3,null)); }
    public Optional<Gadget> findGadget(UUID id) { return listGadgets().stream().filter(g->g.id().equals(id)).findFirst(); }
  }
}
