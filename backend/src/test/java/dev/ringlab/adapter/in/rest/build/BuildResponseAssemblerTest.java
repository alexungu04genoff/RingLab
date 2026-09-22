package dev.ringlab.adapter.in.rest.build;

import dev.ringlab.application.auth.AuthService;
import dev.ringlab.application.build.BuildService;
import dev.ringlab.application.vote.VoteService;
import dev.ringlab.domain.auth.User;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.domain.vote.VoteSummary;
import dev.ringlab.port.out.GameDataRepository;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BuildResponseAssemblerTest {
  @Test
  void preservesPublicFieldsOrderedGadgetsAndExplicitPageSummary() {
    UUID id = UUID.randomUUID(), author = UUID.randomUUID(), racer = UUID.randomUUID(),
        machine = UUID.randomUUID(), front = UUID.randomUUID(), rear = UUID.randomUUID(),
        tire = UUID.randomUUID(), version = UUID.randomUUID(), source = UUID.randomUUID(),
        first = UUID.randomUUID(), second = UUID.randomUUID();
    var created = Instant.parse("2026-01-01T00:00:00Z");
    var updated = created.plusSeconds(60);
    var build = new Build(id, "Title", " Description ", author, racer, front, rear, tire,
        version, source, List.of(second, first), created, updated);
    var parent = new Build(source, "Parent", "", author, racer, front, rear, tire,
        version, null, List.of(), created, created);
    var builds = new BuildService(null, null, null, null) {
      @Override public Optional<Build> remixSource(Build requested) {
        assertSame(build, requested);
        return Optional.of(parent);
      }
    };
    var users = new AuthService(null, null, null, null) {
      @Override public User current(UUID requested) {
        assertEquals(author, requested);
        return new User(author, "Author", "private@example.test", "private hash", created);
      }
    };
    var catalog = (GameDataRepository) Proxy.newProxyInstance(
        GameDataRepository.class.getClassLoader(), new Class<?>[] {GameDataRepository.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "findRacer" -> Optional.of(new Racer(racer, "Racer", null, "/racer.png"));
          case "findMachinePart" -> {
            UUID part = (UUID) args[0];
            var type = part.equals(front) ? MachinePartType.FRONT
                : part.equals(rear) ? MachinePartType.REAR : MachinePartType.TIRE;
            yield Optional.of(new MachinePart(part, machine, type));
          }
          case "findMachine" -> Optional.of(new Machine(machine, "Machine", RacingType.SPEED, "/machine.png"));
          case "findGameVersion" -> Optional.of(new GameVersion(version, "1.4.1", LocalDate.of(2026, 6, 23)));
          case "findGadget" -> Optional.of(new Gadget((UUID) args[0], "Gadget", null, 1, null));
          default -> throw new AssertionError("Unexpected lookup: " + method.getName());
        });
    var votes = new VoteService(null, null) {
      @Override public VoteSummary summary(UUID buildId) {
        throw new AssertionError("Page assembly must reuse the supplied summary");
      }
    };

    var response = new BuildResponseAssembler(builds, users, catalog, votes)
        .assemble(build, new VoteSummary(8, 3));

    assertEquals(id, response.id());
    assertEquals("Title", response.title());
    assertEquals(" Description ", response.description());
    assertEquals(author, response.author().id());
    assertEquals("Author", response.author().username());
    assertEquals(racer, response.racer().id());
    assertNull(response.racer().racingType());
    assertEquals(front, response.frontPart().id());
    assertEquals(rear, response.rearPart().id());
    assertEquals(tire, response.tirePart().id());
    assertEquals("Machine", response.frontPart().sourceMachineName());
    assertEquals("/machine.png", response.frontPart().sourceMachineImagePath());
    assertEquals(version, response.gameVersion().id());
    assertEquals(source, response.remixedFrom().id());
    assertEquals("Parent", response.remixedFrom().title());
    assertEquals(List.of(second, first), response.gadgets().stream().map(g -> g.id()).toList());
    assertNull(response.gadgets().getFirst().description());
    assertEquals(created, response.createdAt());
    assertEquals(updated, response.updatedAt());
    assertEquals(5, response.score());
    assertEquals(8, response.upvotes());
    assertEquals(3, response.downvotes());
  }
}
