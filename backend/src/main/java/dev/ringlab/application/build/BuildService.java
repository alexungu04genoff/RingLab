package dev.ringlab.application.build;

import lombok.RequiredArgsConstructor;

import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.build.GadgetPlate;
import dev.ringlab.domain.gamedata.Gadget;
import dev.ringlab.domain.gamedata.MachinePartType;
import dev.ringlab.application.AppException;
import dev.ringlab.port.out.BuildRepository;
import dev.ringlab.port.out.GameDataRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class BuildService {
  public record Draft(
      String title, String description, UUID racerId, UUID frontPartId,
      UUID rearPartId, UUID tirePartId, UUID gameVersionId, List<UUID> gadgetIds) {}

  private final BuildRepository builds;
  private final GameDataRepository game;

  public Build get(UUID id) {
    return builds.find(id).orElseThrow(() -> AppException.missing("Build"));
  }

  public BuildRepository.Page list(BuildRepository.Filter filter) {
    return builds.list(filter);
  }

  private void validate(Draft d) {
    requireRacer(d.racerId());
    requirePart(d.frontPartId(), MachinePartType.FRONT);
    requirePart(d.rearPartId(), MachinePartType.REAR);
    requirePart(d.tirePartId(), MachinePartType.TIRE);
    if (d.gameVersionId() != null && game.findGameVersion(d.gameVersionId()).isEmpty()) {
      throw new AppException(400, "Unknown game version ID");
    }
    validateGadgets(d.gadgetIds());
  }

  private void requireRacer(UUID id) {
    if (game.findRacer(id).isEmpty()) throw new AppException(400, "Unknown racer ID");
  }

  private void requirePart(UUID id, MachinePartType expectedType) {
    if (id == null) throw new AppException(400, "Missing " + expectedType + " part ID");
    var part = game.findMachinePart(id)
        .orElseThrow(() -> new AppException(400, "Unknown " + expectedType + " part ID"));
    if (part.type() != expectedType) {
      throw new AppException(400, "Expected " + expectedType + " part, got " + part.type());
    }
  }

  private void validateGadgets(List<UUID> gadgetIds) {
    if (new HashSet<>(gadgetIds).size() != gadgetIds.size()) {
      throw new AppException(400, "Duplicate gadget ID");
    }

    List<Integer> slotCosts = gadgetIds.stream()
        .map(this::requireGadget)
        .map(this::requireValidSlotCost)
        .toList();
    if (!GadgetPlate.canFit(slotCosts)) {
      throw new AppException(400, "Selected gadgets do not fit the 2x3 Gadget Plate");
    }
  }

  private Gadget requireGadget(UUID id) {
    return game.findGadget(id)
        .orElseThrow(() -> new AppException(400, "Unknown gadget ID"));
  }

  private int requireValidSlotCost(Gadget gadget) {
    if (gadget.slotCost() == null) {
      throw new AppException(400, "Gadget slot cost is unknown: " + gadget.name());
    }
    if (gadget.slotCost() < 1 || gadget.slotCost() > GadgetPlate.ROW_CAPACITY) {
      throw new AppException(400, "Gadget slot cost is invalid: " + gadget.name());
    }
    return gadget.slotCost();
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
            d.frontPartId(),
            d.rearPartId(),
            d.tirePartId(),
            d.gameVersionId(),
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
            d.frontPartId(),
            d.rearPartId(),
            d.tirePartId(),
            d.gameVersionId(),
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
