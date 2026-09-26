package dev.ringlab.adapter.in.rest.build;

import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.application.auth.AuthService;
import dev.ringlab.application.build.BuildService;
import dev.ringlab.application.vote.VoteService;
import dev.ringlab.domain.auth.User;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.domain.vote.VoteSummary;
import dev.ringlab.port.out.BuildRepository;
import dev.ringlab.port.out.GameDataRepository;
import java.time.Instant;
import java.util.*;
import jakarta.ws.rs.DefaultValue;
import org.junit.jupiter.api.Test;

class BuildRestResourceTest {
  @Test
  void explicitExclusionsUseDisplayedWinnersWithoutReadingTheCurrentSnapshot() {
    var winner = UUID.randomUUID();
    var builds = new BuildService(null, null, null, null) {
      @Override public Page list(Query query) {
        assertEquals(Set.of(winner), query.filter().excludedIds());
        assertEquals(2, query.page());
        return new Page(List.of(), 24);
      }
    };
    // A null cache also ensures explicit exclusions never consult a later server snapshot.
    var resource = new BuildRestResource(builds, null, null, null);
    var page = resource.list(null, null, null, null, null, true, List.of(winner), "rated", 2, 12);
    assertEquals(24, page.total());
    assertEquals(2, page.page());
  }
  @Test
  void defaultsPublicBuildListingToBestRated() throws NoSuchMethodException {
    var list = BuildRestResource.class.getDeclaredMethod("list", String.class, UUID.class,
        UUID.class, UUID.class, UUID.class, boolean.class, List.class, String.class, int.class, int.class);

    assertEquals("rated", list.getParameters()[7].getAnnotation(DefaultValue.class).value());
  }

  @Test
  void deletedOptionalSourceMapsToNullForDetailAndList() {
    UUID id = UUID.randomUUID();
    UUID source = UUID.randomUUID();
    Build remix = new Build(id, "Remix", "", id, id, id, id, null, null, source,
        List.of(), Instant.EPOCH, Instant.EPOCH);
    BuildRepository repository = new BuildRepository() {
      public Optional<Build> find(UUID requested) {
        return requested.equals(id) ? Optional.of(remix) : Optional.empty();
      }
      public List<dev.ringlab.domain.build.ranking.BuildRanking.Candidate> searchCandidates(Filter filter) {
        return List.of(new dev.ringlab.domain.build.ranking.BuildRanking.Candidate(id, remix.createdAt()));
      }
      public List<Build> findAll(Collection<UUID> ids) { return List.of(remix); }
      public void save(Build build) { throw new UnsupportedOperationException(); }
      public void delete(UUID build) { throw new UnsupportedOperationException(); }
    };
    VoteSummary listSummary = new VoteSummary(4, 1);
    var builds = new BuildService(repository, null, null, null) {
      @Override
      public Page list(Query query) { return new Page(List.of(remix), 1, Map.of(id, listSummary)); }
    };
    var users = new AuthService(null, null, null, null) {
      @Override
      public User current(UUID user) { return new User(user, "author", "private", "private", Instant.EPOCH); }
    };
    class CountingVoteService extends VoteService {
      int calls;
      CountingVoteService() { super(null, builds); }
      @Override public VoteSummary summary(UUID build) {
        calls++;
        return new VoteSummary(2, 3);
      }
    };
    var votes = new CountingVoteService();
    var responses = new BuildResponseAssembler(builds, users, new Catalog(id), votes);
    var resource = new BuildRestResource(builds, responses, null, null);
    var page = resource.list(null, null, null, null, null, false, List.of(), "newest", 0, 12);
    assertEquals(1, page.total());
    assertEquals(id, page.items().getFirst().id());
    assertEquals(3, page.items().getFirst().score());
    assertEquals(0, votes.calls);
    assertNull(page.items().getFirst().remixedFrom());
    assertNull(page.items().getFirst().tirePart());

    var detail = resource.get(id);
    assertNull(detail.remixedFrom());
    assertNull(detail.tirePart());
    assertEquals(-1, detail.score());
    assertEquals(1, votes.calls);
    assertThrows(dev.ringlab.application.NotFoundException.class, () -> resource.get(source));
  }

  private record Catalog(UUID id) implements GameDataRepository {
    public Optional<Racer> findRacer(UUID requested) {
      return Optional.of(new Racer(id, "Racer", RacingType.SPEED, null));
    }
    public Optional<MachinePart> findMachinePart(UUID requested) {
      return Optional.of(new MachinePart(id, id, MachinePartType.FRONT));
    }
    public Optional<Machine> findMachine(UUID requested) {
      return Optional.of(new Machine(id, "Machine", RacingType.SPEED, null));
    }
    public List<GameVersion> listGameVersions() { return List.of(); }
    public Optional<GameVersion> findGameVersion(UUID requested) { return Optional.empty(); }
    public List<Racer> listRacers() { return List.of(); }
    public List<Machine> listMachines() { return List.of(); }
    public List<MachinePart> listMachineParts() { return List.of(); }
    public List<Gadget> listGadgets() { return List.of(); }
    public Optional<Gadget> findGadget(UUID requested) { return Optional.empty(); }
  }
}
