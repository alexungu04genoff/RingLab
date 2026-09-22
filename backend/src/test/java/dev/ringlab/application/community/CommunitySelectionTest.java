package dev.ringlab.application.community;

import dev.ringlab.domain.auth.User;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.build.ranking.*;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.domain.vote.VoteSummary;
import dev.ringlab.port.out.*;
import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommunitySelectionTest {
  final UUID racer = UUID.randomUUID(), machine = UUID.randomUUID(), front = UUID.randomUUID(),
      rear = UUID.randomUUID(), tire = UUID.randomUUID(), patch = UUID.randomUUID(),
      oldPatch = UUID.randomUUID(), author = UUID.randomUUID();
  final Map<UUID, Build> builds = new LinkedHashMap<>();
  final Map<UUID, VoteSummary> votes = new HashMap<>();
  final Map<UUID, MachinePart> parts = new HashMap<>(Map.of(
      front, new MachinePart(front, machine, MachinePartType.FRONT),
      rear, new MachinePart(rear, machine, MachinePartType.REAR),
      tire, new MachinePart(tire, machine, MachinePartType.TIRE)));
  final Map<UUID, Machine> machines = new HashMap<>(Map.of(machine, new Machine(machine, "Test", RacingType.SPEED, null)));
  final Map<UUID, Gadget> gadgets = new HashMap<>();
  int statsReads;
  int authorReads;
  int hydrationBatches;
  String authorName = "ringlab_demo_tails";
  final Set<UUID> missingBuilds = new HashSet<>();
  final Map<UUID, Map<UUID, BaseStats>> racerStats = new HashMap<>();
  final Map<UUID, Map<UUID, BaseStats>> partStats = new HashMap<>();

  Build build(String title, long up, long down, UUID selectedTire, List<UUID> selectedGadgets) {
    return build(title, up, down, selectedTire, selectedGadgets, patch);
  }

  Build build(String title, long up, long down, UUID selectedTire,
      List<UUID> selectedGadgets, UUID version) {
    var b = new Build(UUID.randomUUID(), title, "Description", author, racer, front, rear,
        selectedTire, version, null, selectedGadgets, Instant.EPOCH, Instant.EPOCH);
    builds.put(b.id(), b);
    votes.put(b.id(), new VoteSummary(up, down));
    return b;
  }

  CommunitySelectionService service() {
    var repository = stub(BuildRepository.class, (name, args) -> switch (name) {
      case "searchCandidates" -> {
        assertEquals(new BuildRepository.Filter(null, null, null, null, null), args[0]);
        yield builds.values().stream().map(b -> new BuildRanking.Candidate(
            b.id(), b.createdAt(), b.gameVersionId())).toList();
      }
      case "findAll" -> { assertTrue(((Collection<?>) args[0]).size() <= 50);
        hydrationBatches++;
        yield builds.values().stream().filter(b -> ((Collection<?>) args[0]).contains(b.id()))
            .filter(b -> !missingBuilds.contains(b.id())).toList(); }
      case "find" -> Optional.ofNullable(builds.get(args[0]));
      default -> throw new AssertionError(name);
    });
    var game = stub(GameDataRepository.class, (name, args) -> switch (name) {
      case "listRacers" -> List.of(new Racer(racer, "Guest racer", RacingType.SPEED, "/assets/racers/tails.png"));
      case "listMachines" -> List.copyOf(machines.values());
      case "listMachineParts" -> List.copyOf(parts.values());
      case "listGadgets" -> List.copyOf(gadgets.values());
      case "listGameVersions" -> List.of(new GameVersion(patch, "1.4.1", LocalDate.of(2026, 6, 23)),
          new GameVersion(oldPatch, "1.10.0", LocalDate.of(2026, 3, 18)));
      default -> throw new AssertionError("Unexpected catalog lookup: " + name);
    });
    var voteRepository = stub(VoteRepository.class, (name, args) -> {
      assertEquals("summaries", name); return votes;
    });
    var users = stub(UserRepository.class, (name, args) -> {
      assertEquals("byId", name);
      authorReads++;
      return Optional.of(new User(author, authorName, "private@test", "secret", Instant.EPOCH));
    });
    var stats = stub(BaseStatsRepository.class, (name, args) -> {
      statsReads++;
      return switch (name) {
        case "racerStats" -> racerStats.getOrDefault(args[0], Map.of());
        case "machinePartStats" -> partStats.getOrDefault(args[0], Map.of());
        default -> throw new AssertionError(name);
      };
    });
    return new CommunitySelectionService(repository, voteRepository, game, users, stats);
  }

  @Test void excludesBeforeLimitingAndKeepsOrdinaryDemoAccountsAndUnknownStats() {
    for (int i = 0; i < 55; i++) build("[Wilson Demo] " + i, 100, 0, tire, List.of());
    build("Invalid missing tire", 100, 0, null, List.of());
    var a = build("Ordinary demo drive", 40, 1, tire, List.of());
    var b = build("Unlabelled Sonic showcase", 8, 0, tire, List.of());
    var c = build("Third", 3, 0, tire, List.of());
    build("Fourth", 0, 0, tire, List.of());
    var before = Map.copyOf(builds);
    var result = service().select();
    assertEquals(List.of(a.id(), b.id(), c.id()), result.items().stream().map(e -> e.build().id()).toList());
    assertEquals(before, builds);
    assertEquals("ringlab_demo_tails", result.items().getFirst().authorName());
    assertNull(result.items().getFirst().stats().total().speed());
    assertEquals(2, statsReads);
    assertEquals(1, authorReads);
    assertEquals(2, hydrationBatches);
  }

  @Test void topThreeUsesTheSameCanonicalBestRatedOrderAsNormalBrowsing() {
    var fiveDown = build("Five down", 0, 5, tire, List.of());
    var oneDown = build("One down", 0, 1, tire, List.of());
    var unrated = build("Unrated", 0, 0, tire, List.of());
    var positive = build("Positive", 8, 0, tire, List.of());
    var expected = List.of(positive.id(), unrated.id(), oneDown.id());
    assertEquals(expected, service().select().items().stream().map(e -> e.build().id()).toList());
    assertFalse(expected.contains(fiveDown.id()));
  }

  @Test void topThreeAppliesPatchChronologyAndStillExcludesControlledDemos() {
    build("[Wilson Demo] very popular", 100, 0, tire, List.of(), patch);
    var oldUnrated = build("Old unrated", 0, 0, tire, List.of(), oldPatch);
    var newDown = build("New one down", 0, 1, tire, List.of(), patch);
    var newUnrated = build("New unrated", 0, 0, tire, List.of(), patch);
    var oldNegative = build("Old negative with upvotes", 20, 40, tire, List.of(), oldPatch);

    assertEquals(List.of(oldNegative.id(), newUnrated.id(), newDown.id()),
        service().select().items().stream().map(entry -> entry.build().id()).toList());
    assertFalse(service().select().items().stream()
        .anyMatch(entry -> entry.build().id().equals(oldUnrated.id())));
  }

  @Test void emptyAndBoostAndFewerThanThree() {
    assertTrue(service().select().items().isEmpty());
    machines.put(machine, new Machine(machine, "Board", RacingType.BOOST, null));
    build("Invalid board tire", 100, 0, tire, List.of());
    var b = build("Board", 2, 0, null, List.of());
    var result = service().select();
    assertEquals(1, result.items().size());
    assertEquals(b.id(), result.items().getFirst().build().id());
    assertNull(result.items().getFirst().tire());
  }

  @Test void stopsHydratingAfterThreeEligibleBuildsAndReloadsLookupsOnNextSelection() {
    for (int i = 0; i < 120; i++) build("Eligible " + i, 1, 0, tire, List.of());
    var selection = service();
    var first = selection.select();
    assertEquals(3, first.items().size());
    assertEquals(1, hydrationBatches);
    assertEquals(1, authorReads);
    assertEquals(2, statsReads);
    authorName = "renamed";
    var second = selection.select();
    assertEquals(2, hydrationBatches);
    assertEquals(2, authorReads);
    assertEquals(4, statsReads);
    assertEquals("renamed", second.items().getFirst().authorName());
    assertEquals("ringlab_demo_tails", first.items().getFirst().authorName());
    assertThrows(UnsupportedOperationException.class, () -> first.items().clear());
  }

  @Test void skipsMissingHydrationAndUnknownVersionsButKeepsVersionlessBuilds() {
    var missing = build("Deleted during selection", 100, 0, tire, List.of());
    missingBuilds.add(missing.id());
    build("Unknown version", 80, 0, tire, List.of(), UUID.randomUUID());
    var legacy = build("Legacy", 2, 0, tire, List.of(), null);
    var result = service().select();
    assertEquals(List.of(legacy.id()), result.items().stream().map(e -> e.build().id()).toList());
    assertNull(result.items().getFirst().patch());
    assertEquals(BaseStats.UNKNOWN, result.items().getFirst().stats().total());
    assertEquals(0, statsReads);
  }

  @Test void entriesUseTheirOwnVersionPreserveGadgetOrderAndResolveRemixSource() {
    var firstGadget = UUID.randomUUID();
    var secondGadget = UUID.randomUUID();
    gadgets.put(firstGadget, new Gadget(firstGadget, "First", null, 1, null));
    gadgets.put(secondGadget, new Gadget(secondGadget, "Second", null, 2, null));
    var one = new BaseStats(java.math.BigDecimal.ONE, java.math.BigDecimal.ONE,
        java.math.BigDecimal.ONE, java.math.BigDecimal.ONE, java.math.BigDecimal.ONE);
    racerStats.put(patch, Map.of(racer, one));
    partStats.put(patch, Map.of(front, one, rear, one, tire, one));
    var parent = build("Parent", 4, 0, tire, List.of(), oldPatch);
    var draft = build("Remix", 8, 0, tire, List.of(secondGadget, firstGadget));
    var remix = new Build(draft.id(), draft.title(), draft.description(), draft.authorId(),
        racer, front, rear, tire, patch, parent.id(), draft.gadgetIds(), draft.createdAt(), draft.updatedAt());
    builds.put(remix.id(), remix);
    var result = service().select();
    var entry = result.items().getFirst();
    assertEquals(remix, entry.build());
    assertEquals(parent, entry.remixSource());
    assertEquals(List.of(secondGadget, firstGadget), entry.gadgets().stream().map(Gadget::id).toList());
    assertEquals(new java.math.BigDecimal("4"), entry.stats().total().speed());
    assertEquals(BaseStats.UNKNOWN, result.items().get(1).stats().total());
    assertEquals(4, statsReads);
    assertEquals(1, authorReads);
  }

  @Test void exactDemoPrefixesOnly() {
    for (var label : List.of("Wilson", "Comment", "Pagination", "Compare", "Remix", "Machine",
        "Gadget", "Ownership", "Vote", "Version")) assertTrue(CommunityEligibility.controlledDemo("[" + label + " Demo] test"));
    assertTrue(CommunityEligibility.controlledDemo("[DEMO] old"));
    assertFalse(CommunityEligibility.controlledDemo("My demo build"));
    assertFalse(CommunityEligibility.controlledDemo("My [DEMO] build"));
  }

  @Test void invalidGadgetsAndIncompatiblePartsAreReadOnly() {
    var id = UUID.randomUUID();
    var b = build("Gadget", 1, 0, tire, List.of(id));
    assertFalse(CommunityEligibility.valid(b, parts, machines, gadgets));
    gadgets.put(id, new Gadget(id, "Unknown cost", null, null, null));
    assertFalse(CommunityEligibility.valid(b, parts, machines, gadgets));
    gadgets.put(id, new Gadget(id, "Known cost", null, 3, null));
    assertTrue(CommunityEligibility.valid(b, parts, machines, gadgets));
    var other = UUID.randomUUID();
    machines.put(other, new Machine(other, "Other", RacingType.POWER, null));
    parts.put(rear, new MachinePart(rear, other, MachinePartType.REAR));
    assertFalse(CommunityEligibility.valid(b, parts, machines, gadgets));
  }

  interface Call { Object invoke(String name, Object[] args); }
  @SuppressWarnings("unchecked")
  static <T> T stub(Class<T> type, Call call) {
    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
        (proxy, method, args) -> call.invoke(method.getName(), args));
  }
}
