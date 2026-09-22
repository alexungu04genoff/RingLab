package dev.ringlab.application;

import static org.junit.jupiter.api.Assertions.*;
import dev.ringlab.application.gamedata.BaseStatsService;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.port.out.BaseStatsRepository;
import dev.ringlab.port.out.GameDataRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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
