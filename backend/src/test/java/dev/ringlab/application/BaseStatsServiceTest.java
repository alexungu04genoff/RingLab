package dev.ringlab.application;

import static org.junit.jupiter.api.Assertions.*;
import dev.ringlab.application.gamedata.BaseStatsService;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.port.out.BaseStatsRepository;
import dev.ringlab.port.out.GameDataRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class BaseStatsServiceTest {
  private final UUID version = UUID.randomUUID();
  private final UUID racer = UUID.randomUUID();
  private final UUID machine = UUID.randomUUID();
  private final UUID front = UUID.randomUUID();
  private final UUID rear = UUID.randomUUID();
  private final UUID tire = UUID.randomUUID();
  private final Map<UUID, BaseStats> racers = new HashMap<>();
  private final Map<UUID, BaseStats> parts = new HashMap<>();
  private final Catalog catalog = new Catalog();
  private int reads;
  private final BaseStatsService service = new BaseStatsService(new BaseStatsRepository() {
    public Map<UUID, BaseStats> racerStats(UUID id) { assertEquals(version, id); reads++; return racers; }
    public Map<UUID, BaseStats> machinePartStats(UUID id) { assertEquals(version, id); reads++; return parts; }
  }, catalog);

  private BaseStats ones() {
    return new BaseStats(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
  }

  private Build saved(UUID patch, UUID selectedTire) {
    return new Build(UUID.randomUUID(), "Saved", "", UUID.randomUUID(), racer, front, rear, selectedTire,
        patch, null, List.of(), Instant.EPOCH, Instant.EPOCH);
  }

  @Test
  void pageResultsEqualCanonicalCalculationIncludingBoardsDecimalsZeroAndUnknowns() {
    racers.put(racer, new BaseStats(BigDecimal.ZERO, new BigDecimal("1.25"), null, BigDecimal.ONE, BigDecimal.ONE));
    parts.put(front, ones()); parts.put(rear, ones()); parts.put(tire, ones());
    var car = saved(version, tire);
    var board = saved(version, null);
    var versionless = saved(null, tire);
    var carExpected = service.buildBreakdown(version, racer, front, rear, tire);
    var boardExpected = service.buildBreakdown(version, racer, front, rear, null);
    reads = 0;
    var result = service.buildPage(List.of(car, board, versionless));
    assertEquals(carExpected, result.get(car.id()));
    assertEquals(boardExpected, result.get(board.id()));
    assertEquals(new BigDecimal("3.25"), result.get(board.id()).total().acceleration());
    assertEquals(BigDecimal.ZERO, result.get(car.id()).character().speed());
    assertNull(result.get(car.id()).total().handling());
    assertEquals(BaseStatsBreakdown.UNKNOWN, result.get(versionless.id()));
    assertEquals(2, reads);
    parts.remove(tire);
    assertEquals(service.buildBreakdown(version, racer, front, rear, tire), service.buildPage(List.of(car)).get(car.id()));
    assertEquals(BaseStats.UNKNOWN, service.buildPage(List.of(car)).get(car.id()).machine());
  }

  @Test
  void pageReadsEachDistinctPatchMapOnceWithoutCatalogLookupsOrCrossContamination() {
    UUID other = UUID.randomUUID();
    Map<UUID, Integer> racerReads = new HashMap<>();
    Map<UUID, Integer> partReads = new HashMap<>();
    var pageService = new BaseStatsService(new BaseStatsRepository() {
      public Map<UUID, BaseStats> racerStats(UUID patch) {
        racerReads.merge(patch, 1, Integer::sum);
        return patch.equals(version) ? Map.of(racer, ones()) : Map.of();
      }
      public Map<UUID, BaseStats> machinePartStats(UUID patch) {
        partReads.merge(patch, 1, Integer::sum);
        return patch.equals(version) ? Map.of(front, ones(), rear, ones(), tire, ones()) : Map.of();
      }
    }, null); // Any attempt to reconstruct catalog references fails this test.
    var a = saved(version, tire); var b = saved(other, null);
    var page = new ArrayList<Build>();
    page.add(a); page.add(b);
    for (int i = 0; i < 10; i++) page.add(saved(i % 2 == 0 ? version : other, tire));
    var result = pageService.buildPage(page);
    assertEquals(new BigDecimal("4"), result.get(a.id()).total().speed());
    assertEquals(BaseStatsBreakdown.UNKNOWN, result.get(b.id()));
    assertEquals(Map.of(version, 1, other, 1), racerReads);
    assertEquals(racerReads, partReads);
    assertThrows(UnsupportedOperationException.class, () -> result.clear());
    pageService.buildPage(List.of(a));
    assertEquals(2, racerReads.get(version)); // No cache survives page requests.
  }

  @Test
  void emptyAndVersionlessPagesDoNotReadStats() {
    var pageService = new BaseStatsService(null, null);
    assertTrue(pageService.buildPage(List.of()).isEmpty());
    var build = saved(null, null);
    assertEquals(Map.of(build.id(), BaseStatsBreakdown.UNKNOWN), pageService.buildPage(List.of(build)));
  }

  @Test
  void absentVersionNeverReadsOrAssumesASnapshot() {
    assertEquals(BaseStats.UNKNOWN, service.build(null, racer, front, rear, tire));
    assertEquals(0, reads);
    assertThrows(ValidationException.class, () -> service.catalog(null));
    assertThrows(NotFoundException.class, () -> service.catalog(UUID.randomUUID()));
    assertEquals(0, reads);
  }

  @Test
  void absentRecordsAndIncompleteSelectionsAreUnknownWithoutRejectingCatalogEntities() {
    assertEquals(BaseStats.UNKNOWN, service.build(version, racer, front, rear, tire));
    racers.put(racer, ones()); parts.put(front, ones()); parts.put(rear, ones());
    assertEquals(BaseStats.UNKNOWN, service.build(version, racer, front, rear, tire));
    assertEquals(BaseStats.UNKNOWN, service.build(version, null, front, rear, null));
  }

  @Test
  void sumsOnlySelectedComponentsAndDerivesStockStatsFromParts() {
    racers.put(racer, ones());
    for (var id : List.of(front, rear, tire)) parts.put(id, ones());
    var breakdown = service.buildBreakdown(version, racer, front, rear, tire);
    assertEquals(new BigDecimal("4"), breakdown.total().speed());
    assertEquals(BigDecimal.ONE, breakdown.character().speed());
    assertEquals(new BigDecimal("3"), breakdown.machine().speed());
    assertEquals(new BigDecimal("3"), service.catalog(version).machines().get(machine).speed());
    parts.put(front, new BaseStats(BigDecimal.ZERO, BigDecimal.ONE, null, BigDecimal.ONE, BigDecimal.ONE));
    var total = service.build(version, racer, front, rear, tire);
    assertEquals(new BigDecimal("3"), total.speed());
    assertNull(total.handling());
    assertEquals(new BigDecimal("4"), total.boost());
    assertNull(service.catalog(version).machines().get(machine).handling());
    catalog.catalogParts.removeLast();
    assertEquals(BaseStats.UNKNOWN, service.catalog(version).machines().get(machine));
  }

  @Test
  void invalidIdsAndWrongPartSlotsRemainErrors() {
    assertThrows(NotFoundException.class, () -> service.build(version, UUID.randomUUID(), front, rear, tire));
    assertThrows(NotFoundException.class, () -> service.build(version, racer, UUID.randomUUID(), rear, tire));
    assertThrows(ValidationException.class, () -> service.build(version, racer, rear, rear, tire));
  }

  @Test
  void validatesReferencesEvenWithoutAVersionAndResolvesVersionFirst() {
    assertEquals("Game version not found", assertThrows(NotFoundException.class,
        () -> service.build(UUID.randomUUID(), UUID.randomUUID(), rear, front, null)).getMessage());
    assertEquals("Racer not found", assertThrows(NotFoundException.class,
        () -> service.build(null, UUID.randomUUID(), rear, front, null)).getMessage());
    assertEquals("Incorrect machine part type", assertThrows(ValidationException.class,
        () -> service.build(null, racer, rear, front, null)).getMessage());
    assertEquals(0, reads);
  }

  @Test
  void boardStatsSumSelectedPartsWithoutInventingATireContribution() {
    catalog.machineType = RacingType.BOOST;
    catalog.catalogParts.removeIf(part -> part.type() == MachinePartType.TIRE);
    racers.put(racer, ones());
    parts.put(front, ones());
    parts.put(rear, ones());

    var breakdown = service.buildBreakdown(version, racer, front, rear, null);

    assertEquals(new BigDecimal("2"), breakdown.machine().speed());
    assertEquals(new BigDecimal("3"), breakdown.total().speed());
    assertEquals(new BigDecimal("2"), service.catalog(version).machines().get(machine).speed());
    catalog.catalogParts.add(new MachinePart(tire, machine, MachinePartType.TIRE));
    parts.put(tire, ones());
    assertEquals(new BigDecimal("3"),
        service.buildBreakdown(version, racer, front, rear, tire).machine().speed());
  }

  private class Catalog implements GameDataRepository {
    RacingType machineType = RacingType.SPEED;
    final List<Machine> extraMachines = new ArrayList<>();
    final List<MachinePart> catalogParts = new ArrayList<>(List.of(
        new MachinePart(front, machine, MachinePartType.FRONT),
        new MachinePart(rear, machine, MachinePartType.REAR),
        new MachinePart(tire, machine, MachinePartType.TIRE)));
    public List<GameVersion> listGameVersions() { return List.of(new GameVersion(version, "1.3.1", LocalDate.of(2026, 3, 18))); }
    public Optional<GameVersion> findGameVersion(UUID id) { return listGameVersions().stream().filter(v -> v.id().equals(id)).findFirst(); }
    public List<Racer> listRacers() { return List.of(new Racer(racer, "Racer", RacingType.SPEED, null)); }
    public Optional<Racer> findRacer(UUID id) { return listRacers().stream().filter(r -> r.id().equals(id)).findFirst(); }
    public List<Machine> listMachines() {
      var machines = new ArrayList<>(extraMachines);
      machines.add(new Machine(machine, "Machine", machineType, null));
      return machines;
    }
    public Optional<Machine> findMachine(UUID id) { return listMachines().stream().filter(m -> m.id().equals(id)).findFirst(); }
    public List<MachinePart> listMachineParts() { return catalogParts; }
    public Optional<MachinePart> findMachinePart(UUID id) { return catalogParts.stream().filter(p -> p.id().equals(id)).findFirst(); }
    public List<Gadget> listGadgets() { return List.of(); }
    public Optional<Gadget> findGadget(UUID id) { return Optional.empty(); }
  }

  @Test
  void statsRemainAvailableForLegacyTypeConflicts() {
    UUID other = UUID.randomUUID();
    catalog.extraMachines.add(new Machine(other, "Giganto Liner", RacingType.ACCELERATION, null));
    UUID otherRear = UUID.randomUUID();
    catalog.catalogParts.add(new MachinePart(otherRear, other, MachinePartType.REAR));
    racers.put(racer, ones());
    parts.put(front, ones());
    parts.put(otherRear, ones());
    assertEquals(new BigDecimal("3"), service.build(version, racer, front, otherRear, null).speed());
    assertEquals(BaseStats.UNKNOWN, service.build(null, racer, front, otherRear, null));
    assertEquals(BaseStats.UNKNOWN, service.build(version, racer, null, otherRear, null));
    catalog.machineType = null;
    assertEquals(BaseStats.UNKNOWN, service.build(version, racer, front, null, null));
  }
}
