package dev.ringlab.application;

import static dev.ringlab.importing.ImportFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.application.gamedata.GameDataImportService;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.domain.gamedata.importing.*;
import dev.ringlab.domain.gamedata.importing.GameDataSet.*;
import dev.ringlab.port.out.GameDataImportRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GameDataImportServiceTest {
  private final MemoryRepository repository = new MemoryRepository(sample());
  private final GameDataImportService service = new GameDataImportService(repository);

  @Test void offlineValidationDoesNotReadDatabaseAndAcceptsUnknownCostAndRacerType() {
    var edit = new Edit(sample());
    edit.racers.set(0, row(new Racer(id(1), "Unknown type", null, null)));
    edit.gadgets.set(0, row(new Gadget(id(6), "Unknown cost", null, null, null)));
    service.validate(edit.build());
    assertEquals(0, repository.reads); assertFalse(repository.wrote);
  }

  @Test void acquisitionAndArtworkAreReviewablePresentationUpdatesWithAnIdempotentApply() {
    var edit = new Edit(sample());
    var gadget = new Gadget(id(6), "Gadget", null, 1, "/assets/gadgets/reviewed.png",
        GadgetAcquisitionKind.FESTIVAL_REWARD, "Samba de Amigo Festival");
    edit.gadgets.set(0, row(gadget));
    var plan = service.plan(edit.build());
    assertTrue(plan.safe()); assertEquals(1, plan.updateCount()); assertEquals(0, plan.inserts());
    assertTrue(plan.changes().stream().anyMatch(change -> change.contains("acquisition_kind")));
    assertTrue(plan.changes().stream().anyMatch(change -> change.contains("acquisition_label")));
    service.apply(edit.build(), plan.approvalToken());
    repository.current = edit.build(); // The recording fake does not implement database writes.
    assertEquals(0, service.plan(edit.build()).updateCount());
    assertEquals(gadget, repository.read().gadgets().getFirst().value());
  }

  static Stream<Consumer<Edit>> invalidDatasets() {
    return Stream.of(
        e -> e.racers.add(e.racers.getFirst()),
        e -> e.racers.add(row(new Racer(id(20), "Racer", RacingType.POWER, null))),
        e -> e.machines.add(e.machines.getFirst()),
        e -> e.parts.add(e.parts.getFirst()),
        e -> e.parts.add(row(new MachinePart(id(21), id(2), MachinePartType.FRONT))),
        e -> e.parts.set(0, row(new MachinePart(id(3), id(99), MachinePartType.FRONT))),
        e -> e.parts.removeLast(),
        e -> e.machines.set(0, row(new Machine(id(2), "Machine", RacingType.BOOST, null))),
        e -> e.machines.set(0, row(new Machine(id(2), "Machine", null, null))),
        e -> e.gadgets.set(0, row(new Gadget(id(6), "Gadget", null, 4, null))),
        e -> e.gadgets.set(0, row(new Gadget(id(6), "Gadget", null, 0, null))),
        e -> e.gadgets.set(0, row(new Gadget(id(6), "Gadget", null, 1, null, null, null))),
        e -> e.gadgets.set(0, row(new Gadget(id(6), "Gadget", null, 1, null, GadgetAcquisitionKind.FESTIVAL_REWARD, null))),
        e -> e.gadgets.set(0, row(new Gadget(id(6), "Gadget", null, 1, null, GadgetAcquisitionKind.FESTIVAL_REWARD, " "))),
        e -> e.gadgets.set(0, row(new Gadget(id(6), "Gadget", null, 1, null, GadgetAcquisitionKind.UNKNOWN, "Unreviewed event"))),
        e -> e.gadgets.add(e.gadgets.getFirst()),
        e -> e.maps.add(row(new RaceMap(id(90), "Another map", RaceMap.Category.CROSSWORLD, null, null, 1))),
        e -> e.maps.add(e.maps.getFirst()),
        e -> e.versions.add(e.versions.getFirst()),
        e -> e.snapshots.add(e.snapshots.getFirst()),
        e -> e.snapshots.clear(),
        e -> e.snapshots.set(0, new VersionStats(id(99), e.snapshots.getFirst().racers(), e.snapshots.getFirst().parts())),
        e -> e.snapshots.set(0, new VersionStats(id(8), List.of(row(new StatRow(id(99), BaseStats.ZERO))), e.snapshots.getFirst().parts())),
        e -> e.snapshots.set(0, new VersionStats(id(8), List.of(row(new StatRow(id(1), BaseStats.ZERO)), row(new StatRow(id(1), BaseStats.ZERO))), e.snapshots.getFirst().parts())));
  }

  @ParameterizedTest @MethodSource("invalidDatasets")
  void rejectsInvalidDatasetsBeforeDatabaseAccess(Consumer<Edit> change) {
    var edit = new Edit(sample()); change.accept(edit);
    var error = assertThrows(ImportValidationException.class, () -> service.apply(edit.build(), "unreviewed"));
    assertTrue(error.getMessage().contains("field=")); assertEquals(0, repository.reads); assertFalse(repository.wrote);
  }

  @Test void boostCompositionAcceptsExactlyFrontAndRear() {
    var edit = new Edit(sample()); edit.parts.removeLast();
    edit.machines.set(0, row(new Machine(id(2), "Board", RacingType.BOOST, null)));
    edit.snapshots.set(0, new VersionStats(id(8), edit.snapshots.getFirst().racers(), edit.snapshots.getFirst().parts().subList(0, 2)));
    service.validate(edit.build());
  }

  @Test void initialImportAndExactReimportHaveExpectedCounts() {
    var first = new GameDataImportService(new MemoryRepository(empty())).plan(sample());
    assertTrue(first.safe()); assertEquals(12, first.inserts()); assertEquals(0, first.updateCount());
    var noOp = service.plan(sample());
    assertTrue(noOp.safe()); assertEquals(0, noOp.inserts()); assertEquals(0, noOp.updateCount());
  }

  @Test void safePresentationChangesRetainIdentityAndShowBeforeAfter() {
    var edit = new Edit(sample());
    edit.racers.set(0, row(new Racer(id(1), "Renamed racer", RacingType.SPEED, "/assets/new.png")));
    edit.machines.set(0, row(new Machine(id(2), "Renamed machine", RacingType.SPEED, "/assets/new.png")));
    edit.gadgets.set(0, row(new Gadget(id(6), "Renamed gadget", "New, quoted description", 1, "/assets/new.png")));
    edit.maps.set(0, row(new RaceMap(id(7), "Renamed map", RaceMap.Category.MAIN_COURSE, "Pack", "/assets/new.png", 1)));
    var plan = service.plan(edit.build());
    assertTrue(plan.safe()); assertEquals(4, plan.updateCount()); assertEquals(0, plan.inserts());
    assertTrue(plan.changes().stream().anyMatch(s -> s.contains("\"Racer\" -> \"Renamed racer\"")));
    service.apply(edit.build(), plan.approvalToken()); assertTrue(repository.wrote);
  }

  static Stream<Consumer<Edit>> unsafeEdits() {
    return Stream.of(
        e -> e.racers.set(0, row(new Racer(id(1), "Racer", RacingType.POWER, null))),
        e -> e.machines.set(0, row(new Machine(id(2), "Machine", RacingType.POWER, null))),
        e -> e.gadgets.set(0, row(new Gadget(id(6), "Gadget", null, 2, null))),
        e -> e.gadgets.clear(),
        e -> e.maps.clear(),
        e -> e.maps.set(0, row(new RaceMap(id(7), "Map", RaceMap.Category.CROSSWORLD, null, null, 1))),
        e -> e.maps.set(0, row(new RaceMap(id(7), "Map", RaceMap.Category.MAIN_COURSE, null, null, 2))),
        e -> e.versions.set(0, row(new GameVersion(id(8), "1.0", LocalDate.of(2026, 2, 1)))),
        e -> e.versions.set(0, row(new GameVersion(id(8), "renamed", LocalDate.of(2026, 1, 1)))),
        e -> { e.versions.clear(); e.snapshots.clear(); },
        e -> e.snapshots.set(0, new VersionStats(id(8), List.of(), e.snapshots.getFirst().parts())),
        e -> e.snapshots.set(0, new VersionStats(id(8), List.of(row(new StatRow(id(1), BaseStats.UNKNOWN))), e.snapshots.getFirst().parts())));
  }

  @ParameterizedTest @MethodSource("unsafeEdits")
  void planReportsAndApplyRefusesUnsafeChanges(Consumer<Edit> change) {
    var edit = new Edit(sample()); change.accept(edit);
    var plan = service.plan(edit.build());
    assertFalse(plan.safe()); assertFalse(plan.unsafeChanges().isEmpty());
    assertThrows(ImportValidationException.class, () -> service.apply(edit.build(), plan.approvalToken()));
    assertFalse(repository.wrote);
  }

  @Test void forbidsPartTypeAndSourceChangesEvenWhenNewCompositionIsValid() {
    var edit = new Edit(sample());
    edit.parts.set(0, row(new MachinePart(id(3), id(2), MachinePartType.REAR)));
    edit.parts.set(1, row(new MachinePart(id(4), id(2), MachinePartType.FRONT)));
    assertFalse(service.plan(edit.build()).safe());
    edit = new Edit(sample());
    edit.machines.add(row(new Machine(id(22), "Second", RacingType.SPEED, null)));
    for (int i = 0; i < 3; i++) {
      var p = edit.parts.get(i).value();
      edit.parts.set(i, row(new MachinePart(p.id(), id(22), p.type())));
      edit.parts.add(row(new MachinePart(id(30 + i), id(2), p.type())));
    }
    assertFalse(service.plan(edit.build()).safe());
  }

  @Test void addingIdentityDoesNotRequireRewritingOldSnapshotsButAddingOldStatsIsRejected() {
    var edit = new Edit(sample()); edit.racers.add(row(new Racer(id(20), "New racer", RacingType.SPEED, null)));
    var plan = service.plan(edit.build()); assertTrue(plan.safe()); assertEquals(1, plan.inserts());
    edit.snapshots.set(0, new VersionStats(id(8), List.of(row(new StatRow(id(1), BaseStats.ZERO)),
        row(new StatRow(id(20), BaseStats.UNKNOWN))), edit.snapshots.getFirst().parts()));
    assertFalse(service.plan(edit.build()).safe());
  }

  @Test void newVersionMustExplicitlyCoverAllCurrentRacersAndParts() {
    var edit = new Edit(sample());
    edit.versions.add(row(new GameVersion(id(9), "1.1", LocalDate.of(2026, 2, 1))));
    edit.snapshots.add(new VersionStats(id(9), List.of(), List.of()));
    var missing = service.plan(edit.build()); assertFalse(missing.safe()); assertEquals(4, missing.unsafeChanges().size());
    edit.snapshots.set(1, new VersionStats(id(9), edit.snapshots.getFirst().racers(), edit.snapshots.getFirst().parts()));
    var complete = service.plan(edit.build()); assertTrue(complete.safe()); assertEquals(5, complete.inserts());
  }

  @Test void deterministicOutputAndTokenIgnoreRowOrderSourceLocationAndDecimalScale() {
    var first = service.plan(sample()); var edit = new Edit(sample());
    Collections.reverse(edit.parts);
    var zero = new BaseStats(new BigDecimal("0.00"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    edit.snapshots.set(0, new VersionStats(id(8), List.of(new CatalogRow<>("moved.csv", 42, new StatRow(id(1), zero))), edit.snapshots.getFirst().parts()));
    var second = service.plan(edit.build());
    assertEquals(first.approvalToken(), second.approvalToken()); assertEquals(first.changes(), second.changes());
    assertEquals(0, second.updateCount());
  }

  @Test void approvedPlanCannotBeUsedWithDifferentFilesOrDatabase() {
    var token = service.plan(sample()).approvalToken();
    var edit = new Edit(sample()); edit.gadgets.set(0, row(new Gadget(id(6), "Changed", null, 1, null)));
    assertThrows(ImportValidationException.class, () -> service.apply(edit.build(), token));
    repository.current = edit.build();
    assertThrows(ImportValidationException.class, () -> service.apply(sample(), token));
    assertFalse(repository.wrote);
  }

  @Test void simultaneousNameReuseIsRejectedBeforeUniqueConstraintFailure() {
    var edit = new Edit(sample()); edit.racers.set(0, row(new Racer(id(1), "New label", RacingType.SPEED, null)));
    edit.racers.add(row(new Racer(id(10), "Racer", RacingType.SPEED, null)));
    assertFalse(service.plan(edit.build()).safe());
  }

  private static final class MemoryRepository implements GameDataImportRepository {
    GameDataSet current; int reads; boolean wrote;
    MemoryRepository(GameDataSet current) { this.current = current; }
    public GameDataSet read() { reads++; return current; }
    public GameDataImportPlan apply(Function<GameDataSet, GameDataImportPlan> prepare) {
      var plan = prepare.apply(read()); wrote = true; return plan;
    }
  }
}
