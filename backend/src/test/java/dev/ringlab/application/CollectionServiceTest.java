package dev.ringlab.application;

import dev.ringlab.application.collection.CollectionService;
import dev.ringlab.domain.collection.*;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.port.out.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollectionServiceTest {
  @Test void defaultsIdempotentUpdatesTypedReferencesAndAccountsStayIndependent() {
    var racer = new Racer(UUID.randomUUID(), "Demo racer", RacingType.SPEED, null);
    var machine = new Machine(UUID.randomUUID(), "Demo source", RacingType.SPEED, null);
    var gadget = new Gadget(UUID.randomUUID(), "Demo gadget", null, 1, null);
    var catalog = new DemoCatalog(racer, machine, gadget);
    var repository = new MemoryCollection();
    var service = new CollectionService(repository, catalog);
    var demo = UUID.randomUUID(); var other = UUID.randomUUID();
    assertEquals(CollectionExclusions.NONE, service.load(demo));
    var ids = Map.of(CollectionCategory.RACER, racer.id(), CollectionCategory.MACHINE, machine.id(), CollectionCategory.GADGET, gadget.id());
    for (var category : CollectionCategory.values()) {
      service.setOwned(demo, category, ids.get(category), false);
      service.setOwned(demo, category, ids.get(category), false);
    }
    assertEquals(new CollectionExclusions(Set.of(racer.id()), Set.of(machine.id()), Set.of(gadget.id())), service.load(demo));
    assertEquals(CollectionExclusions.NONE, service.load(other));
    assertTrue(service.load(demo).racerAvailable(UUID.randomUUID()));
    assertThrows(ValidationException.class, () -> service.setOwned(demo, CollectionCategory.RACER, machine.id(), false));
    assertThrows(UnsupportedOperationException.class, () -> service.load(demo).racers().clear());
    for (var category : CollectionCategory.values()) {
      service.setOwned(demo, category, ids.get(category), true);
      service.setOwned(demo, category, ids.get(category), true);
    }
    assertEquals(CollectionExclusions.NONE, service.load(demo));
  }

  private record DemoCatalog(Racer racer, Machine machine, Gadget gadget) implements GameDataRepository {
    public Optional<Racer> findRacer(UUID id) { return Optional.of(racer).filter(value -> value.id().equals(id)); }
    public Optional<Machine> findMachine(UUID id) { return Optional.of(machine).filter(value -> value.id().equals(id)); }
    public Optional<Gadget> findGadget(UUID id) { return Optional.of(gadget).filter(value -> value.id().equals(id)); }
    public List<Racer> listRacers() { return List.of(racer); }
    public List<Machine> listMachines() { return List.of(machine); }
    public List<Gadget> listGadgets() { return List.of(gadget); }
    public List<RaceMap> listRaceMaps() { throw new AssertionError("Maps have no ownership"); }
    public List<GameVersion> listGameVersions() { throw new AssertionError("Versions have no ownership"); }
    public Optional<GameVersion> findGameVersion(UUID id) { throw new AssertionError("Versions have no ownership"); }
    public List<MachinePart> listMachineParts() { throw new AssertionError("Parts use source ownership"); }
    public Optional<MachinePart> findMachinePart(UUID id) { throw new AssertionError("Parts use source ownership"); }
  }

  private static class MemoryCollection implements CollectionRepository {
    private final Map<UUID, EnumMap<CollectionCategory, Set<UUID>>> exclusions = new HashMap<>();
    public CollectionExclusions load(UUID user) {
      var values = exclusions.getOrDefault(user, new EnumMap<>(CollectionCategory.class));
      return new CollectionExclusions(values.getOrDefault(CollectionCategory.RACER, Set.of()),
          values.getOrDefault(CollectionCategory.MACHINE, Set.of()), values.getOrDefault(CollectionCategory.GADGET, Set.of()));
    }
    public void setOwned(UUID user, CollectionCategory category, UUID item, boolean owned) {
      var ids = exclusions.computeIfAbsent(user, ignored -> new EnumMap<>(CollectionCategory.class))
          .computeIfAbsent(category, ignored -> new HashSet<>());
      if (owned) ids.remove(item); else ids.add(item);
    }
  }
}
