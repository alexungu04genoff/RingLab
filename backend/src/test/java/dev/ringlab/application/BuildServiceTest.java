package dev.ringlab.application;

import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.application.build.BuildService;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.gamedata.Gadget;
import dev.ringlab.domain.gamedata.Machine;
import dev.ringlab.domain.gamedata.Racer;
import dev.ringlab.domain.gamedata.RacingType;
import dev.ringlab.port.out.BuildRepository;
import dev.ringlab.port.out.GameDataRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BuildServiceTest {
  private final UUID racerId = UUID.randomUUID();
  private final UUID machineId = UUID.randomUUID();
  private final UUID gadgetId = UUID.randomUUID();
  private final UUID authorId = UUID.randomUUID();
  private InMemoryBuildRepository builds;
  private GameDataStub gameData;
  private BuildService service;

  @BeforeEach
  void setUp() {
    builds = new InMemoryBuildRepository();
    gameData = new GameDataStub(racerId, machineId, gadgetId);
    service = new BuildService(builds, gameData);
  }

  @Test
  void getsExistingBuildAndRejectsMissingBuild() {
    Build build = existingBuild();
    builds.saved.put(build.id(), build);

    assertEquals(build, service.get(build.id()));
    AppException error =
        assertThrows(AppException.class, () -> service.get(UUID.randomUUID()));
    assertEquals(404, error.status);
  }

  @Test
  void createsBuildWithValidatedReferencesAndTrimmedTitle() {
    Build created = service.create(authorId, draft("  Fast route  "));

    assertSame(created, builds.lastSaved);
    assertEquals("Fast route", created.title());
    assertEquals("Description stays as supplied", created.description());
    assertEquals(authorId, created.authorId());
    assertEquals(racerId, created.racerId());
    assertEquals(machineId, created.machineId());
    assertEquals(List.of(gadgetId), created.gadgetIds());
    assertEquals(created.createdAt(), created.updatedAt());
  }

  @Test
  void rejectsUnknownRacer() {
    gameData.racers.clear();

    AppException error = assertThrows(AppException.class, () -> service.create(authorId, draft("Build")));

    assertEquals(400, error.status);
    assertEquals("Unknown racer ID", error.getMessage());
    assertNull(builds.lastSaved);
  }

  @Test
  void rejectsUnknownMachine() {
    gameData.machines.clear();

    AppException error = assertThrows(AppException.class, () -> service.create(authorId, draft("Build")));

    assertEquals(400, error.status);
    assertEquals("Unknown machine ID", error.getMessage());
    assertNull(builds.lastSaved);
  }

  @Test
  void rejectsUnknownGadget() {
    gameData.gadgets.clear();

    AppException error = assertThrows(AppException.class, () -> service.create(authorId, draft("Build")));

    assertEquals(400, error.status);
    assertEquals("Unknown gadget ID", error.getMessage());
    assertNull(builds.lastSaved);
  }

  @Test
  void ownerCanEditWhileAuthorAndCreationTimeArePreserved() {
    Build old = existingBuild();
    builds.saved.put(old.id(), old);

    Build edited = service.edit(old.id(), authorId, draft("  Updated  "));

    assertSame(edited, builds.lastSaved);
    assertEquals(old.id(), edited.id());
    assertEquals("Updated", edited.title());
    assertEquals(old.authorId(), edited.authorId());
    assertEquals(old.createdAt(), edited.createdAt());
    assertFalse(edited.updatedAt().isBefore(old.createdAt()));
  }

  @Test
  void nonOwnerCannotEdit() {
    Build old = existingBuild();
    builds.saved.put(old.id(), old);

    AppException error =
        assertThrows(
            AppException.class, () -> service.edit(old.id(), UUID.randomUUID(), draft("Updated")));

    assertEquals(403, error.status);
    assertNull(builds.lastSaved);
  }

  @Test
  void ownerCanDelete() {
    Build old = existingBuild();
    builds.saved.put(old.id(), old);

    service.delete(old.id(), authorId);

    assertEquals(old.id(), builds.deletedId);
  }

  @Test
  void nonOwnerCannotDelete() {
    Build old = existingBuild();
    builds.saved.put(old.id(), old);

    AppException error =
        assertThrows(AppException.class, () -> service.delete(old.id(), UUID.randomUUID()));

    assertEquals(403, error.status);
    assertNull(builds.deletedId);
  }

  private BuildService.Draft draft(String title) {
    return new BuildService.Draft(
        title, "Description stays as supplied", racerId, machineId, List.of(gadgetId));
  }

  private Build existingBuild() {
    Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
    return new Build(
        UUID.randomUUID(),
        "Original",
        "Original description",
        authorId,
        racerId,
        machineId,
        List.of(gadgetId),
        createdAt,
        createdAt);
  }

  private static final class InMemoryBuildRepository implements BuildRepository {
    private final Map<UUID, Build> saved = new HashMap<>();
    private Build lastSaved;
    private UUID deletedId;

    @Override
    public Optional<Build> find(UUID id) {
      return Optional.ofNullable(saved.get(id));
    }

    @Override
    public Page list(Filter filter) {
      return new Page(List.copyOf(saved.values()), saved.size());
    }

    @Override
    public void save(Build build) {
      lastSaved = build;
      saved.put(build.id(), build);
    }

    @Override
    public void delete(UUID id) {
      deletedId = id;
      saved.remove(id);
    }
  }

  private static final class GameDataStub implements GameDataRepository {
    private final Map<UUID, Racer> racers = new HashMap<>();
    private final Map<UUID, Machine> machines = new HashMap<>();
    private final Map<UUID, Gadget> gadgets = new HashMap<>();

    private GameDataStub(UUID racerId, UUID machineId, UUID gadgetId) {
      racers.put(racerId, new Racer(racerId, "Racer", RacingType.SPEED, null));
      machines.put(machineId, new Machine(machineId, "Machine", RacingType.BOOST, null));
      gadgets.put(gadgetId, new Gadget(gadgetId, "Gadget", null, null, null));
    }

    @Override
    public List<Racer> listRacers() {
      return List.copyOf(racers.values());
    }

    @Override
    public Optional<Racer> findRacer(UUID id) {
      return Optional.ofNullable(racers.get(id));
    }

    @Override
    public List<Machine> listMachines() {
      return List.copyOf(machines.values());
    }

    @Override
    public Optional<Machine> findMachine(UUID id) {
      return Optional.ofNullable(machines.get(id));
    }

    @Override
    public List<Gadget> listGadgets() {
      return List.copyOf(gadgets.values());
    }

    @Override
    public Optional<Gadget> findGadget(UUID id) {
      return Optional.ofNullable(gadgets.get(id));
    }
  }
}
