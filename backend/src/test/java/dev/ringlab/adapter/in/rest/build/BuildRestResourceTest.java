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
import org.junit.jupiter.api.Test;

class BuildRestResourceTest {
  @Test
  void deletedOptionalSourceMapsToNullForDetailAndList() {
    UUID id = UUID.randomUUID();
    UUID source = UUID.randomUUID();
    Build remix = new Build(id, "Remix", "", id, id, id, id, id, null, source,
        List.of(), Instant.EPOCH, Instant.EPOCH);
    BuildRepository repository = new BuildRepository() {
      public Optional<Build> find(UUID requested) {
        return requested.equals(id) ? Optional.of(remix) : Optional.empty();
      }
      public List<Build> search(Filter filter) { return List.of(remix); }
      public void save(Build build) { throw new UnsupportedOperationException(); }
      public void delete(UUID build) { throw new UnsupportedOperationException(); }
    };
    var builds = new BuildService(repository, null, null) {
      @Override
      public Page list(Query query) { return new Page(List.of(remix), 1); }
    };
    var users = new AuthService(null, null) {
      @Override
      public User current(UUID user) { return new User(user, "author", "private", "private", Instant.EPOCH); }
    };
    var votes = new VoteService(null, builds) {
      @Override
      public VoteSummary summary(UUID build) { return new VoteSummary(0, 0); }
    };
    var resource = new BuildRestResource(builds, users, new Catalog(id), votes, null);
    assertNull(resource.get(id).remixedFrom());
    var page = resource.list(null, null, null, null, null, "newest", 0, 12);
    assertEquals(1, page.total());
    assertEquals(id, page.items().getFirst().id());
    assertNull(page.items().getFirst().remixedFrom());
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
