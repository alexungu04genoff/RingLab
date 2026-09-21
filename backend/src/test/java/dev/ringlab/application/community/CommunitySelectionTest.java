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
      rear = UUID.randomUUID(), tire = UUID.randomUUID(), patch = UUID.randomUUID(), author = UUID.randomUUID();
  final Map<UUID, Build> builds = new LinkedHashMap<>();
  final Map<UUID, VoteSummary> votes = new HashMap<>();
  final Map<UUID, MachinePart> parts = new HashMap<>(Map.of(
      front, new MachinePart(front, machine, MachinePartType.FRONT),
      rear, new MachinePart(rear, machine, MachinePartType.REAR),
      tire, new MachinePart(tire, machine, MachinePartType.TIRE)));
  final Map<UUID, Machine> machines = new HashMap<>(Map.of(machine, new Machine(machine, "Test", RacingType.SPEED, null)));
  final Map<UUID, Gadget> gadgets = new HashMap<>();
  int statsReads;

  Build build(String title, long up, long down, UUID selectedTire, List<UUID> selectedGadgets) {
    var b = new Build(UUID.randomUUID(), title, "Description", author, racer, front, rear,
        selectedTire, patch, null, selectedGadgets, Instant.EPOCH, Instant.EPOCH);
    builds.put(b.id(), b);
    votes.put(b.id(), new VoteSummary(up, down));
    return b;
  }

  CommunitySelectionService service() {
    var repository = stub(BuildRepository.class, (name, args) -> switch (name) {
      case "searchCandidates" -> {
        assertEquals(new BuildRepository.Filter(null, null, null, null, null), args[0]);
        yield builds.values().stream().map(b -> new BuildRanking.Candidate(b.id(), b.createdAt())).toList();
      }
      case "findAll" -> { assertTrue(((Collection<?>) args[0]).size() <= 50);
        yield builds.values().stream().filter(b -> ((Collection<?>) args[0]).contains(b.id())).toList(); }
      default -> throw new AssertionError(name);
    });
    var game = stub(GameDataRepository.class, (name, args) -> switch (name) {
      case "listRacers" -> List.of(new Racer(racer, "Guest racer", RacingType.SPEED, "/assets/racers/tails.png"));
      case "listMachines" -> List.copyOf(machines.values());
      case "listMachineParts" -> List.copyOf(parts.values());
      case "listGadgets" -> List.copyOf(gadgets.values());
      case "listGameVersions" -> List.of(new GameVersion(patch, "1.4.1", LocalDate.of(2026, 6, 23)));
      default -> throw new AssertionError("Unexpected catalog lookup: " + name);
    });
    var voteRepository = stub(VoteRepository.class, (name, args) -> {
      assertEquals("summaries", name); return votes;
    });
    var users = stub(UserRepository.class, (name, args) -> {
      assertEquals("byId", name);
      return Optional.of(new User(author, "ringlab_demo_tails", "private@test", "secret", Instant.EPOCH));
    });
    var stats = stub(BaseStatsRepository.class, (name, args) -> { statsReads++; return Map.of(); });
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
  }

  @Test void topThreeUsesTheSameCanonicalBestRatedOrderAsNormalBrowsing() {
    var fiveDown = build("Five down", 0, 5, tire, List.of());
    var oneDown = build("One down", 0, 1, tire, List.of());
    var unrated = build("Unrated", 0, 0, tire, List.of());
    var positive = build("Positive", 8, 0, tire, List.of());
    var expected = builds.values().stream().map(b -> new BuildRanking.Candidate(b.id(), b.createdAt()))
        .sorted(BuildRanking.comparator(BuildSort.BEST_RATED, votes)).limit(3).map(BuildRanking.Candidate::id).toList();
    assertEquals(List.of(positive.id(), unrated.id(), oneDown.id()), expected);
    assertEquals(expected, service().select().items().stream().map(e -> e.build().id()).toList());
    assertFalse(expected.contains(fiveDown.id()));
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
