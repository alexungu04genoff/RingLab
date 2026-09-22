package dev.ringlab.application;

import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.application.build.BuildService;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.build.ranking.BuildSort;
import dev.ringlab.domain.vote.Vote;
import dev.ringlab.domain.vote.VoteSummary;
import dev.ringlab.domain.gamedata.Gadget;
import dev.ringlab.domain.gamedata.GameVersion;
import dev.ringlab.domain.gamedata.Machine;
import dev.ringlab.domain.gamedata.MachinePart;
import dev.ringlab.domain.gamedata.MachinePartType;
import dev.ringlab.domain.gamedata.Racer;
import dev.ringlab.domain.gamedata.RacingType;
import dev.ringlab.port.out.BuildRepository;
import dev.ringlab.port.out.GameDataRepository;
import dev.ringlab.port.out.VoteRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BuildServiceTest {
  private final UUID racerId = UUID.randomUUID();
  private final UUID machineId = UUID.randomUUID();
  private final UUID frontPartId = UUID.randomUUID();
  private final UUID rearPartId = UUID.randomUUID();
  private final UUID tirePartId = UUID.randomUUID();
  private final UUID gadgetId = UUID.randomUUID();
  private final UUID authorId = UUID.randomUUID();
  private final UUID gameVersionId = UUID.randomUUID();
  private InMemoryBuildRepository builds;
  private GameDataStub gameData;
  private EmptyVoteRepository votes;
  private BuildService service;
  private StubProfanityPolicy profanity;

  @BeforeEach
  void setUp() {
    builds = new InMemoryBuildRepository();
    gameData = new GameDataStub(racerId, machineId, gadgetId);
    gameData.parts.put(frontPartId, new MachinePart(frontPartId, machineId, MachinePartType.FRONT));
    gameData.parts.put(rearPartId, new MachinePart(rearPartId, machineId, MachinePartType.REAR));
    gameData.parts.put(tirePartId, new MachinePart(tirePartId, machineId, MachinePartType.TIRE));
    gameData.versions.put(gameVersionId,
        new GameVersion(gameVersionId, "1.4.1", java.time.LocalDate.of(2026, 6, 23)));
    votes = new EmptyVoteRepository();
    profanity = new StubProfanityPolicy();
    service = new BuildService(builds, gameData, votes, profanity);
  }

  @Test
  void listingRanksRepositoryFactsBeforePaginatingAndReportsTotal() {
    Instant base = Instant.parse("2026-01-01T00:00:00Z");
    Build tiny = rankedBuild("tiny", base.plusSeconds(1));
    Build strong = rankedBuild("strong", base.plusSeconds(2));
    Build newest = rankedBuild("newest", base.plusSeconds(3));
    builds.searchResults = List.of(newest, tiny, strong);
    votes.summaries.put(tiny.id(), new VoteSummary(3, 0));
    votes.summaries.put(strong.id(), new VoteSummary(40, 1));

    var filter = new BuildRepository.Filter(null, null, null, null, null);
    assertEquals(List.of(strong, tiny),
        service.list(new BuildService.Query(filter, BuildSort.BEST_RATED, 0, 2)).items());
    assertEquals(List.of(newest.id(), tiny.id(), strong.id()), votes.requestedIds);
    assertEquals(List.of(strong, tiny, newest),
        service.list(new BuildService.Query(filter, BuildSort.SCORE, 0, 3)).items());
    assertEquals(List.of(newest.id(), tiny.id(), strong.id()), votes.requestedIds);
    var newestPage = service.list(new BuildService.Query(filter, BuildSort.NEWEST, 1, 2));
    assertEquals(List.of(tiny), newestPage.items());
    assertEquals(List.of(tiny.id()), votes.requestedIds);
    assertEquals(new VoteSummary(3, 0), newestPage.summary(tiny.id()));
    assertEquals(3, newestPage.total());
    assertTrue(service.list(new BuildService.Query(filter, BuildSort.NEWEST,
        Integer.MAX_VALUE, 50)).items().isEmpty());
    assertEquals(List.of(tiny.id()), votes.requestedIds);
  }

  @Test
  void normalBestRatedListingUsesCanonicalWilsonZeroEvidenceOrder() {
    Instant base = Instant.parse("2026-01-01T00:00:00Z");
    Build fiveDownNewest = rankedBuild("five down", base.plusSeconds(4));
    Build unratedOld = rankedBuild("unrated old", base.plusSeconds(1));
    Build oneDown = rankedBuild("one down", base.plusSeconds(3));
    Build unratedNew = rankedBuild("unrated new", base.plusSeconds(2));
    builds.searchResults = List.of(fiveDownNewest, unratedOld, oneDown, unratedNew);
    votes.summaries.put(fiveDownNewest.id(), new VoteSummary(0, 5));
    votes.summaries.put(oneDown.id(), new VoteSummary(0, 1));

    var page = service.list(new BuildService.Query(
        new BuildRepository.Filter(null, null, null, null, null),
        BuildSort.BEST_RATED, 0, 4));

    assertEquals(List.of(unratedNew, unratedOld, oneDown, fiveDownNewest), page.items());
  }

  @Test
  void bestRatedUsesPatchChronologyBeforeZeroEvidenceAndPaginatesGlobally() {
    UUID newerPatch = gameVersionId;
    UUID olderPatch = UUID.randomUUID();
    gameData.versions.put(newerPatch,
        new GameVersion(newerPatch, "1.4.1", java.time.LocalDate.of(2026, 6, 23)));
    gameData.versions.put(olderPatch,
        new GameVersion(olderPatch, "1.10.0", java.time.LocalDate.of(2026, 3, 18)));
    Instant base = Instant.parse("2026-01-01T00:00:00Z");
    Build oldUnrated = rankedBuild("old patch unrated", base.plusSeconds(3), olderPatch);
    Build newOneDown = rankedBuild("new patch down", base.plusSeconds(1), newerPatch);
    Build newUnrated = rankedBuild("new patch unrated", base, newerPatch);
    Build oldNegativeWithUpvotes = rankedBuild("old patch negative", base, olderPatch);
    builds.searchResults = List.of(oldUnrated, newOneDown, oldNegativeWithUpvotes, newUnrated);
    votes.summaries.put(newOneDown.id(), new VoteSummary(0, 1));
    votes.summaries.put(oldNegativeWithUpvotes.id(), new VoteSummary(20, 40));
    var filter = new BuildRepository.Filter(null, null, null, null, null);

    var first = service.list(new BuildService.Query(filter, BuildSort.BEST_RATED, 0, 2));
    var second = service.list(new BuildService.Query(filter, BuildSort.BEST_RATED, 1, 2));

    assertEquals(4, first.total());
    assertEquals(List.of(oldNegativeWithUpvotes, newUnrated), first.items());
    assertEquals(List.of(newOneDown, oldUnrated), second.items());
    assertEquals(List.of(oldNegativeWithUpvotes, oldUnrated), service.list(new BuildService.Query(
        new BuildRepository.Filter(null, null, null, null, olderPatch),
        BuildSort.BEST_RATED, 0, 2)).items());
  }

  @Test
  void listingRestoresRankedOrderAndSkipsMissingBulkHydrationResults() {
    Instant base = Instant.parse("2026-01-01T00:00:00Z");
    Build oldest = rankedBuild("oldest", base);
    Build middle = rankedBuild("middle", base.plusSeconds(1));
    Build newest = rankedBuild("newest", base.plusSeconds(2));
    builds.searchResults = List.of(middle, oldest, newest);
    builds.hydrationResults = List.of(oldest, newest);

    var page = service.list(new BuildService.Query(
        new BuildRepository.Filter(null, null, null, null, null), BuildSort.NEWEST, 0, 3));

    assertEquals(List.of(newest, oldest), page.items());
    assertEquals(List.of(newest.id(), middle.id(), oldest.id()), builds.hydratedIds);
  }

  @Test
  void scoreAndBestRatedReturnPageSummariesFromTheirRankingQuery() {
    Instant base = Instant.parse("2026-01-01T00:00:00Z");
    Build first = rankedBuild("first", base.plusSeconds(1));
    Build second = rankedBuild("second", base.plusSeconds(2));
    builds.searchResults = List.of(first, second);
    votes.summaries.put(first.id(), new VoteSummary(7, 2));
    votes.summaries.put(second.id(), new VoteSummary(1, 4));
    var filter = new BuildRepository.Filter(null, null, null, null, null);

    for (BuildSort sort : List.of(BuildSort.SCORE, BuildSort.BEST_RATED)) {
      votes.summaryCalls = 0;
      var page = service.list(new BuildService.Query(filter, sort, 0, 1));

      assertEquals(List.of(first.id(), second.id()), votes.requestedIds);
      assertEquals(new VoteSummary(7, 2), page.summary(first.id()));
      assertEquals(0, votes.summaryCalls);
    }
  }

  private Build rankedBuild(String title, Instant createdAt) {
    return rankedBuild(title, createdAt, null);
  }

  private Build rankedBuild(String title, Instant createdAt, UUID versionId) {
    return new Build(UUID.randomUUID(), title, "", authorId, racerId, frontPartId,
        rearPartId, tirePartId, versionId, null, List.of(), createdAt, createdAt);
  }

  @Test
  void getsExistingBuildAndRejectsMissingBuild() {
    Build build = existingBuild();
    builds.saved.put(build.id(), build);

    assertEquals(build, service.get(build.id()));
    NotFoundException error =
        assertThrows(NotFoundException.class, () -> service.get(UUID.randomUUID()));
    assertEquals("Build not found", error.getMessage());
  }

  @Test
  void createsBuildWithValidatedReferencesAndTrimmedTitle() {
    Build created = service.create(authorId, draft("  Fast route  "));

    assertSame(created, builds.lastSaved);
    assertEquals("Fast route", created.title());
    assertEquals("Description stays as supplied", created.description());
    assertEquals(authorId, created.authorId());
    assertEquals(racerId, created.racerId());
    assertEquals(frontPartId, created.frontPartId());
    assertEquals(rearPartId, created.rearPartId());
    assertEquals(tirePartId, created.tirePartId());
    assertEquals(gameVersionId, created.gameVersionId());
    assertNull(created.remixedFromBuildId());
    assertEquals(created, service.get(created.id()));
    assertEquals(List.of(gadgetId), created.gadgetIds());
    assertEquals(created.createdAt(), created.updatedAt());
  }

  @Test
  void rejectsProfanityInBuildTitleAndDescriptionBeforePersistence() {
    profanity.blocked.addAll(List.of("Blocked title", "Blocked description"));
    var badTitle = new BuildService.Draft("Blocked title", "Clean description", racerId, frontPartId,
        rearPartId, tirePartId, null, null, List.of(gadgetId));
    var badDescription = new BuildService.Draft("Clean title", "Blocked description", racerId,
        frontPartId, rearPartId, tirePartId, null, null, List.of(gadgetId));

    for (var draft : List.of(badTitle, badDescription)) {
      var error = assertThrows(ValidationException.class, () -> service.create(authorId, draft));
      assertEquals("Text contains inappropriate language", error.getMessage());
      assertEquals(draft == badTitle ? "title" : "description", error.field());
    }
    assertNull(builds.lastSaved);
    assertTrue(profanity.checks.contains(new StubProfanityPolicy.Check("Blocked title", "title")));
    assertTrue(profanity.checks.contains(new StubProfanityPolicy.Check("Blocked description", "description")));
  }

  @Test
  void createsRemixWithDirectSourceAndNormalValidation() {
    Build source = existingBuild();
    builds.saved.put(source.id(), source);

    var remixDraft = new BuildService.Draft("Remix", "Independent copy", racerId, frontPartId,
        rearPartId, tirePartId, gameVersionId, source.id(), List.of(gadgetId));
    Build remix = service.create(UUID.randomUUID(), remixDraft);

    assertEquals(source.id(), remix.remixedFromBuildId());
    assertNotEquals(source.authorId(), remix.authorId());
    assertEquals(List.of(gadgetId), remix.gadgetIds());

    gameData.parts.remove(frontPartId);
    assertThrows(ValidationException.class, () -> service.create(authorId, remixDraft));
  }

  @Test
  void rejectsUnknownRemixSource() {
    var draft = new BuildService.Draft("Remix", "", racerId, frontPartId, rearPartId,
        tirePartId, gameVersionId, UUID.randomUUID(), List.of(gadgetId));

    ValidationException error =
        assertThrows(ValidationException.class, () -> service.create(authorId, draft));

    assertEquals("Unknown remix source build ID", error.getMessage());
    assertNull(builds.lastSaved);
  }

  @Test
  void editingRemixCannotReplaceItsSource() {
    Build source = existingBuild();
    builds.saved.put(source.id(), source);
    Build remix = service.create(authorId, new BuildService.Draft("Remix", "", racerId,
        frontPartId, rearPartId, tirePartId, gameVersionId, source.id(), List.of(gadgetId)));

    Build edited = service.edit(remix.id(), authorId, new BuildService.Draft("Edited", "", racerId,
        frontPartId, rearPartId, tirePartId, gameVersionId, UUID.randomUUID(), List.of(gadgetId)));

    assertEquals(source.id(), edited.remixedFromBuildId());
  }

  @Test
  void rejectsUnknownRacer() {
    gameData.racers.clear();

    ValidationException error =
        assertThrows(ValidationException.class, () -> service.create(authorId, draft("Build")));

    assertEquals("Unknown racer ID", error.getMessage());
    assertNull(builds.lastSaved);
  }

  @Test
  void rejectsUnknownFrontPart() {
    gameData.parts.remove(frontPartId);

    ValidationException error =
        assertThrows(ValidationException.class, () -> service.create(authorId, draft("Build")));

    assertEquals("Unknown FRONT part ID", error.getMessage());
    assertNull(builds.lastSaved);
  }

  @Test
  void rejectsUnknownGadget() {
    gameData.gadgets.clear();

    ValidationException error =
        assertThrows(ValidationException.class, () -> service.create(authorId, draft("Build")));

    assertEquals("Unknown gadget ID", error.getMessage());
    assertNull(builds.lastSaved);
  }

  @Test
  void rejectsDuplicateGadgetsBeforeSaving() {
    var duplicate = draftWithGadgets(List.of(gadgetId, gadgetId));

    ValidationException error =
        assertThrows(ValidationException.class, () -> service.create(authorId, duplicate));

    assertEquals("Duplicate gadget ID", error.getMessage());
    assertNull(builds.lastSaved);
  }

  @Test
  void rejectsUnknownAndInvalidGadgetCosts() {
    UUID unknownCostId = addGadget("Unknown cost", null);
    UUID zeroCostId = addGadget("Zero cost", 0);
    UUID excessiveCostId = addGadget("Excessive cost", 4);

    ValidationException unknownCost = assertThrows(ValidationException.class,
        () -> service.create(authorId, draftWithGadgets(List.of(unknownCostId))));
    assertEquals("Gadget slot cost is unknown: Unknown cost", unknownCost.getMessage());

    for (UUID invalidId : List.of(zeroCostId, excessiveCostId)) {
      ValidationException invalidCost = assertThrows(ValidationException.class,
          () -> service.create(authorId, draftWithGadgets(List.of(invalidId))));
      assertTrue(invalidCost.getMessage().startsWith("Gadget slot cost is invalid: "));
    }
    assertNull(builds.lastSaved);
  }

  @Test
  void rejectsUnplaceablePlateOnCreateAndEdit() {
    List<UUID> twoSlotGadgets = List.of(
        addGadget("First", 2), addGadget("Second", 2), addGadget("Third", 2));
    var invalid = draftWithGadgets(twoSlotGadgets);

    ValidationException createError =
        assertThrows(ValidationException.class, () -> service.create(authorId, invalid));
    assertEquals("Selected gadgets do not fit the 2x3 Gadget Plate", createError.getMessage());

    var existing = service.create(authorId, draft("Existing"));
    builds.lastSaved = null;
    ValidationException editError = assertThrows(ValidationException.class,
        () -> service.edit(existing.id(), authorId, invalid));
    assertEquals("Selected gadgets do not fit the 2x3 Gadget Plate", editError.getMessage());
    assertNull(builds.lastSaved);
    assertEquals(List.of(gadgetId), service.get(existing.id()).gadgetIds());
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

    ForbiddenException error =
        assertThrows(
            ForbiddenException.class,
            () -> service.edit(old.id(), UUID.randomUUID(), draft("Updated")));

    assertEquals("Only the author may change this resource", error.getMessage());
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

    ForbiddenException error =
        assertThrows(ForbiddenException.class, () -> service.delete(old.id(), UUID.randomUUID()));

    assertEquals("Only the author may change this resource", error.getMessage());
    assertNull(builds.deletedId);
  }

  @Test
  void acceptsMixedSourcesAndEditsOnePartIndependently() {
    var old = service.create(authorId, draft("Stock"));
    UUID mixedRear = UUID.randomUUID();
    UUID mixedMachine = UUID.randomUUID();
    gameData.machines.put(mixedMachine,
        new Machine(mixedMachine, "Other", RacingType.SPEED, null));
    gameData.parts.put(mixedRear, new MachinePart(mixedRear, mixedMachine, MachinePartType.REAR));
    var mixed = new BuildService.Draft("Mixed", "", racerId, frontPartId, mixedRear,
        tirePartId, gameVersionId, null, List.of(gadgetId));
    var created = service.create(authorId, mixed);
    assertEquals(mixedRear, service.get(created.id()).rearPartId());
    var edited = service.edit(old.id(), authorId, mixed);
    assertEquals(old.frontPartId(), edited.frontPartId());
    assertEquals(mixedRear, edited.rearPartId());
    assertEquals(old.tirePartId(), edited.tirePartId());
    assertEquals(List.of(gadgetId), edited.gadgetIds());
  }

  @Test
  void boardBuildUsesFrontAndRearWithoutTires() {
    UUID boardMachine = UUID.randomUUID();
    UUID secondBoardMachine = UUID.randomUUID();
    UUID boardFront = UUID.randomUUID();
    UUID boardRear = UUID.randomUUID();
    gameData.machines.put(boardMachine,
        new Machine(boardMachine, "Board", RacingType.BOOST, null));
    gameData.machines.put(secondBoardMachine,
        new Machine(secondBoardMachine, "Other Board", RacingType.BOOST, null));
    gameData.parts.put(boardFront, new MachinePart(boardFront, boardMachine, MachinePartType.FRONT));
    gameData.parts.put(boardRear, new MachinePart(boardRear, secondBoardMachine, MachinePartType.REAR));

    var draft = new BuildService.Draft("Board", "", racerId, boardFront, boardRear,
        null, gameVersionId, null, List.of());
    var created = service.create(authorId, draft);

    assertNull(created.tirePartId());
    assertEquals(boardFront, created.frontPartId());
    assertEquals(boardRear, created.rearPartId());
  }

  @Test
  void rejectsMissingStandardTireBoardTireAndMixedFamilies() {
    assertThrows(ValidationException.class, () -> service.create(authorId,
        new BuildService.Draft("Standard", "", racerId, frontPartId, rearPartId,
            null, gameVersionId, null, List.of())));

    UUID boardMachine = UUID.randomUUID();
    UUID boardFront = UUID.randomUUID();
    UUID boardRear = UUID.randomUUID();
    gameData.machines.put(boardMachine,
        new Machine(boardMachine, "Board", RacingType.BOOST, null));
    gameData.parts.put(boardFront, new MachinePart(boardFront, boardMachine, MachinePartType.FRONT));
    gameData.parts.put(boardRear, new MachinePart(boardRear, boardMachine, MachinePartType.REAR));

    assertThrows(ValidationException.class, () -> service.create(authorId,
        new BuildService.Draft("Board with tires", "", racerId, boardFront, boardRear,
            tirePartId, gameVersionId, null, List.of())));
    assertThrows(ValidationException.class, () -> service.create(authorId,
        new BuildService.Draft("Mixed", "", racerId, frontPartId, boardRear,
            tirePartId, gameVersionId, null, List.of())));
  }

  @Test
  void rejectsWrongTypesInEverySlot() {
    for (var wrong : List.of(
        new BuildService.Draft("Bad", "", racerId, rearPartId, rearPartId, tirePartId, null, null, List.of()),
        new BuildService.Draft("Bad", "", racerId, frontPartId, frontPartId, tirePartId, null, null, List.of()),
        new BuildService.Draft("Bad", "", racerId, frontPartId, rearPartId, frontPartId, null, null, List.of()))) {
      var error = assertThrows(ValidationException.class, () -> service.create(authorId, wrong));
      assertTrue(error.getMessage().startsWith("Expected "));
    }
    assertNull(builds.lastSaved);
  }

  @Test
  void allMachineTypesUseTheirOwnSlotsIndependentlyOfRacerType() {
    for (var type : RacingType.values()) {
      gameData.machines.put(machineId, new Machine(machineId, "Source", type, null));
      var tire = type == RacingType.BOOST ? null : tirePartId;
      var valid = new BuildService.Draft("Valid", "", racerId, frontPartId, rearPartId,
          tire, gameVersionId, null, List.of());
      var created = service.create(authorId, valid);
      assertEquals(tire, created.tirePartId());
      assertEquals(tire, service.edit(created.id(), authorId, valid).tirePartId());
      var remix = new BuildService.Draft("Remix", "", racerId, frontPartId, rearPartId,
          tire, gameVersionId, created.id(), List.of());
      assertEquals(created.id(), service.create(authorId, remix).remixedFromBuildId());
      var invalid = new BuildService.Draft("Invalid", "", racerId, frontPartId, rearPartId,
          tire == null ? tirePartId : null, gameVersionId, null, List.of());
      assertThrows(ValidationException.class, () -> service.create(authorId, invalid));
    }
  }

  @Test
  void incompatibleRearOrTireCannotBeCreatedEditedOrPublishedAsRemix() {
    gameData.machines.put(machineId, new Machine(machineId, "Goromaru", RacingType.POWER, null));
    var original = service.create(authorId, draft("Power machine"));
    UUID other = UUID.randomUUID();
    gameData.machines.put(other, new Machine(other, "Giganto Liner", RacingType.ACCELERATION, null));
    for (var slot : List.of(MachinePartType.REAR, MachinePartType.TIRE)) {
      UUID partId = UUID.randomUUID();
      gameData.parts.put(partId, new MachinePart(partId, other, slot));
      var bad = new BuildService.Draft("Invalid", "", racerId, frontPartId,
          slot == MachinePartType.REAR ? partId : rearPartId,
          slot == MachinePartType.TIRE ? partId : tirePartId, gameVersionId, original.id(), List.of());
      var error = assertThrows(ValidationException.class, () -> service.create(authorId, bad));
      assertTrue(error.getMessage().contains("Power machine"));
      assertThrows(ValidationException.class, () -> service.edit(original.id(), authorId, bad));
      assertEquals(original, service.get(original.id()));
    }
    gameData.machines.put(machineId, new Machine(machineId, "Unverified", null, null));
    assertTrue(assertThrows(ValidationException.class, () -> service.create(authorId, draft("Unknown")))
        .getMessage().contains("cannot be verified"));
  }

  @Test
  void rejectsMissingPartAndPreservesOrderedGadgets() {
    var invalid = new BuildService.Draft("Bad", "", racerId, null, rearPartId, tirePartId, null, null, List.of());
    assertThrows(ValidationException.class, () -> service.create(authorId, invalid));
    UUID second = UUID.randomUUID();
    gameData.gadgets.put(second, new Gadget(second, "Second", null, 2, null));
    var ordered = new BuildService.Draft("Ordered", "", racerId, frontPartId, rearPartId,
        tirePartId, gameVersionId, null, List.of(second, gadgetId));
    var created = service.create(authorId, ordered);
    assertEquals(List.of(second, gadgetId), service.get(created.id()).gadgetIds());
  }

  @Test
  void rejectsInvalidTextAndNullDraftFieldsWithoutSaving() {
    assertThrows(ValidationException.class, () -> service.create(authorId, null));
    assertThrows(ValidationException.class, () -> service.create(null, draft("Valid")));
    for (String title : Arrays.asList(null, "", " \t\n", "x".repeat(121))) {
      assertThrows(ValidationException.class, () -> service.create(authorId, draft(title)));
    }
    for (var invalid : List.of(
        new BuildService.Draft("Valid", null, racerId, frontPartId, rearPartId, tirePartId, null, null, List.of()),
        new BuildService.Draft("Valid", "x".repeat(10001), racerId, frontPartId, rearPartId, tirePartId, null, null, List.of()),
        new BuildService.Draft("Valid", "", null, frontPartId, rearPartId, tirePartId, null, null, List.of()),
        draftWithGadgets(null), draftWithGadgets(Arrays.asList((UUID) null)))) {
      assertThrows(ValidationException.class, () -> service.create(authorId, invalid));
    }
    assertNull(builds.lastSaved);
  }

  @Test
  void acceptsTextBoundariesAndRejectsInvalidEditWithoutChangingBuild() {
    var valid = new BuildService.Draft("x".repeat(120), "d".repeat(10000), racerId,
        frontPartId, rearPartId, tirePartId, gameVersionId, null, List.of());
    Build created = service.create(authorId, valid);
    assertEquals(valid.title(), created.title());
    assertEquals(valid.description(), created.description());
    assertThrows(ValidationException.class, () -> service.edit(created.id(), authorId, draft(" ")));
    assertEquals(created, service.get(created.id()));
  }

  @Test
  void guardsInvalidQueriesBeforeCallingRepositories() {
    var filter = new BuildRepository.Filter(null, null, null, null, null);
    assertThrows(ValidationException.class, () -> service.list(null));
    for (var query : List.of(
        new BuildService.Query(null, BuildSort.NEWEST, 0, 12),
        new BuildService.Query(filter, null, 0, 12),
        new BuildService.Query(filter, BuildSort.NEWEST, -1, 12),
        new BuildService.Query(filter, BuildSort.NEWEST, 0, 0),
        new BuildService.Query(filter, BuildSort.NEWEST, 0, 51),
        new BuildService.Query(new BuildRepository.Filter("x".repeat(121), null, null, null, null),
            BuildSort.NEWEST, 0, 12))) {
      assertThrows(ValidationException.class, () -> service.list(query));
    }
  }

  @Test
  void missingOptionalSourceDoesNotMakeExistingRemixMissing() {
    Build source = service.create(authorId, draft("Source"));
    Build remix = service.create(authorId, new BuildService.Draft("Remix", "", racerId,
        frontPartId, rearPartId, tirePartId, gameVersionId, source.id(), List.of()));
    assertEquals(Optional.of(source), service.remixSource(remix));
    builds.saved.remove(source.id());
    assertTrue(service.remixSource(remix).isEmpty());
    assertEquals(remix, service.get(remix.id()));
    assertThrows(NotFoundException.class, () -> service.get(source.id()));
    assertTrue(service.remixSource(existingBuild()).isEmpty());
  }

  private BuildService.Draft draft(String title) {
    return new BuildService.Draft(
        title, "Description stays as supplied", racerId, frontPartId, rearPartId, tirePartId, gameVersionId, null, List.of(gadgetId));
  }

  private BuildService.Draft draftWithGadgets(List<UUID> gadgetIds) {
    return new BuildService.Draft(
        "Build", "", racerId, frontPartId, rearPartId, tirePartId, gameVersionId, null, gadgetIds);
  }

  private UUID addGadget(String name, Integer slotCost) {
    UUID id = UUID.randomUUID();
    gameData.gadgets.put(id, new Gadget(id, name, null, slotCost, null));
    return id;
  }

  private BuildService.Draft versionDraft(UUID version) {
    return new BuildService.Draft("Versioned", "", racerId, frontPartId, rearPartId,
        tirePartId, version, null, List.of(gadgetId));
  }

  @Test
  void createsReadsAndChangesVersion() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    gameData.versions.put(first, new GameVersion(first, "1.4.1", java.time.LocalDate.of(2026, 6, 23)));
    gameData.versions.put(second, new GameVersion(second, "1.3.1", java.time.LocalDate.of(2026, 3, 18)));
    var missingOnCreate = assertThrows(ValidationException.class,
        () -> service.create(authorId, versionDraft(null)));
    assertEquals("Select a game version / patch", missingOnCreate.getMessage());
    assertEquals("gameVersionId", missingOnCreate.field());
    var build = service.create(authorId, versionDraft(first));
    assertEquals(first, service.get(build.id()).gameVersionId());
    service.edit(build.id(), authorId, versionDraft(second));
    assertEquals(second, service.get(build.id()).gameVersionId());
    var missing = assertThrows(ValidationException.class,
        () -> service.edit(build.id(), authorId, versionDraft(null)));
    assertEquals("Select a game version / patch", missing.getMessage());
    assertEquals("gameVersionId", missing.field());
    assertEquals(second, service.get(build.id()).gameVersionId());
  }

  @Test
  void rejectsUnknownVersionOnCreateAndEditWithoutSaving() {
    var invalid = versionDraft(UUID.randomUUID());
    var error = assertThrows(ValidationException.class, () -> service.create(authorId, invalid));
    assertEquals("Unknown game version ID", error.getMessage());
    assertNull(builds.lastSaved);
    var existing = service.create(authorId, versionDraft(gameVersionId));
    assertThrows(
        ValidationException.class, () -> service.edit(existing.id(), authorId, invalid));
    assertEquals(existing, service.get(existing.id()));
  }

  private Build existingBuild() {
    Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
    return new Build(
        UUID.randomUUID(),
        "Original",
        "Original description",
        authorId,
        racerId,
        frontPartId,
        rearPartId,
        tirePartId,
        null,
        null,
        List.of(gadgetId),
        createdAt,
        createdAt);
  }

  private static final class InMemoryBuildRepository implements BuildRepository {
    private final Map<UUID, Build> saved = new HashMap<>();
    private List<Build> searchResults;
    private List<Build> hydrationResults;
    private List<UUID> hydratedIds = List.of();
    private Build lastSaved;
    private UUID deletedId;

    @Override
    public Optional<Build> find(UUID id) {
      return Optional.ofNullable(saved.get(id));
    }

    @Override
    public List<dev.ringlab.domain.build.ranking.BuildRanking.Candidate> searchCandidates(Filter filter) {
      List<Build> results = searchResults == null ? List.copyOf(saved.values()) : searchResults;
      return results.stream()
          .filter(build -> filter.gameVersionId() == null
              || filter.gameVersionId().equals(build.gameVersionId()))
          .map(build ->
          new dev.ringlab.domain.build.ranking.BuildRanking.Candidate(
              build.id(), build.createdAt(), build.gameVersionId())).toList();
    }

    @Override
    public List<Build> findAll(Collection<UUID> ids) {
      hydratedIds = List.copyOf(ids);
      if (hydrationResults != null) return hydrationResults;
      Map<UUID, Build> available = new HashMap<>(saved);
      if (searchResults != null) searchResults.forEach(build -> available.put(build.id(), build));
      return ids.stream().map(available::get).filter(Objects::nonNull).toList();
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

  private static final class EmptyVoteRepository implements VoteRepository {
    private final Map<UUID, VoteSummary> summaries = new HashMap<>();
    private List<UUID> requestedIds = List.of();
    private int summaryCalls;
    public void put(Vote vote) {}
    public void remove(UUID userId, UUID buildId) {}
    public VoteSummary summary(UUID buildId) {
      summaryCalls++;
      return summaries.getOrDefault(buildId, new VoteSummary(0, 0));
    }
    public Map<UUID, VoteSummary> summaries(Collection<UUID> buildIds) {
      requestedIds = List.copyOf(buildIds);
      return buildIds.stream().filter(summaries::containsKey)
          .collect(java.util.stream.Collectors.toMap(id -> id, summaries::get));
    }
    public int value(UUID userId, UUID buildId) { return 0; }
  }

  private static final class GameDataStub implements GameDataRepository {
    private final Map<UUID, GameVersion> versions = new HashMap<>();

    public List<GameVersion> listGameVersions() {
      return List.copyOf(versions.values());
    }

    public Optional<GameVersion> findGameVersion(UUID id) {
      return Optional.ofNullable(versions.get(id));
    }
    private final Map<UUID, MachinePart> parts = new HashMap<>();
    private final Map<UUID, Racer> racers = new HashMap<>();
    private final Map<UUID, Machine> machines = new HashMap<>();
    private final Map<UUID, Gadget> gadgets = new HashMap<>();

    private GameDataStub(UUID racerId, UUID machineId, UUID gadgetId) {
      racers.put(racerId, new Racer(racerId, "Racer", RacingType.SPEED, null));
      machines.put(machineId, new Machine(machineId, "Machine", RacingType.SPEED, null));
      gadgets.put(gadgetId, new Gadget(gadgetId, "Gadget", null, 1, null));
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

    public List<MachinePart> listMachineParts() {
      return List.copyOf(parts.values());
    }

    public Optional<MachinePart> findMachinePart(UUID id) {
      return Optional.ofNullable(parts.get(id));
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
