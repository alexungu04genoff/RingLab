package dev.ringlab.build.application;

import dev.ringlab.build.application.port.out.BuildStore;
import dev.ringlab.build.domain.Build;
import dev.ringlab.gamedata.application.port.out.GameDataStore;
import dev.ringlab.shared.application.AppException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class BuildService {
  public record Draft(
      String title, String description, UUID racerId, UUID machineId, List<UUID> gadgetIds) {}

  private final BuildStore builds;
  private final GameDataStore game;

  public BuildService(BuildStore builds, GameDataStore game) {
    this.builds = builds;
    this.game = game;
  }

  public Build get(UUID id) {
    return builds.find(id).orElseThrow(() -> AppException.missing("Build"));
  }

  public BuildStore.Page list(BuildStore.Filter filter) {
    return builds.list(filter);
  }

  private void validate(Draft d) {
    require(GameDataStore.Kind.RACER, d.racerId());
    require(GameDataStore.Kind.MACHINE, d.machineId());
    d.gadgetIds().forEach(id -> require(GameDataStore.Kind.GADGET, id));
  }

  private void require(GameDataStore.Kind kind, UUID id) {
    if (game.find(kind, id).isEmpty())
      throw new AppException(400, "Unknown " + kind.name().toLowerCase(Locale.ROOT) + " ID");
  }

  @Transactional
  public Build create(UUID author, Draft d) {
    validate(d);
    var now = Instant.now();
    var b =
        new Build(
            UUID.randomUUID(),
            d.title().trim(),
            d.description(),
            author,
            d.racerId(),
            d.machineId(),
            d.gadgetIds(),
            now,
            now);
    builds.save(b);
    return b;
  }

  @Transactional
  public Build edit(UUID id, UUID actor, Draft d) {
    var old = get(id);
    AppException.requireOwner(old.authorId(), actor);
    validate(d);
    var b =
        new Build(
            id,
            d.title().trim(),
            d.description(),
            old.authorId(),
            d.racerId(),
            d.machineId(),
            d.gadgetIds(),
            old.createdAt(),
            Instant.now());
    builds.save(b);
    return b;
  }

  @Transactional
  public void delete(UUID id, UUID actor) {
    AppException.requireOwner(get(id).authorId(), actor);
    builds.delete(id);
  }
}
