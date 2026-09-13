package dev.ringlab.application.build;

import lombok.RequiredArgsConstructor;

import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.build.GadgetPlate;
import dev.ringlab.domain.build.ranking.BuildRanking;
import dev.ringlab.domain.build.ranking.BuildSort;
import dev.ringlab.domain.gamedata.Gadget;
import dev.ringlab.domain.gamedata.MachinePartType;
import dev.ringlab.application.ForbiddenException;
import dev.ringlab.application.NotFoundException;
import dev.ringlab.application.ValidationException;
import dev.ringlab.port.out.BuildRepository;
import dev.ringlab.port.out.GameDataRepository;
import dev.ringlab.port.out.VoteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
@RequiredArgsConstructor
public class BuildService {
  public record Query(BuildRepository.Filter filter, BuildSort sort, int page, int size) {}

  public record Page(List<Build> items, long total) {}

  public record Draft(
      String title, String description, UUID racerId, UUID frontPartId,
      UUID rearPartId, UUID tirePartId, UUID gameVersionId, UUID remixedFromBuildId,
      List<UUID> gadgetIds) {}

  private final BuildRepository builds;
  private final GameDataRepository game;
  private final VoteRepository votes;

  public Build get(UUID id) {
    return builds.find(id).orElseThrow(() -> NotFoundException.missing("Build"));
  }

  public Page list(Query query) {
    List<Build> candidates = builds.search(query.filter());
    var summaries = votes.summaries(candidates.stream().map(Build::id).toList());
    List<Build> ranked = candidates.stream()
        .sorted(BuildRanking.comparator(query.sort(), summaries))
        .toList();
    long offset = (long) query.page() * query.size();
    if (offset >= ranked.size()) return new Page(List.of(), ranked.size());
    int from = (int) offset;
    int to = (int) Math.min(offset + query.size(), ranked.size());
    return new Page(ranked.subList(from, to), ranked.size());
  }

  private void validate(Draft d) {
    requireRacer(d.racerId());
    requirePart(d.frontPartId(), MachinePartType.FRONT);
    requirePart(d.rearPartId(), MachinePartType.REAR);
    requirePart(d.tirePartId(), MachinePartType.TIRE);
    if (d.gameVersionId() != null && game.findGameVersion(d.gameVersionId()).isEmpty()) {
      throw new ValidationException("Unknown game version ID");
    }
    validateGadgets(d.gadgetIds());
  }

  private void requireRacer(UUID id) {
    if (game.findRacer(id).isEmpty()) throw new ValidationException("Unknown racer ID");
  }

  private void requirePart(UUID id, MachinePartType expectedType) {
    if (id == null) throw new ValidationException("Missing " + expectedType + " part ID");
    var part = game.findMachinePart(id)
        .orElseThrow(() -> new ValidationException("Unknown " + expectedType + " part ID"));
    if (part.type() != expectedType) {
      throw new ValidationException("Expected " + expectedType + " part, got " + part.type());
    }
  }

  private void validateGadgets(List<UUID> gadgetIds) {
    if (new HashSet<>(gadgetIds).size() != gadgetIds.size()) {
      throw new ValidationException("Duplicate gadget ID");
    }

    List<Integer> slotCosts = gadgetIds.stream()
        .map(this::requireGadget)
        .map(this::requireValidSlotCost)
        .toList();
    if (!GadgetPlate.canFit(slotCosts)) {
      throw new ValidationException("Selected gadgets do not fit the 2x3 Gadget Plate");
    }
  }

  private Gadget requireGadget(UUID id) {
    return game.findGadget(id)
        .orElseThrow(() -> new ValidationException("Unknown gadget ID"));
  }

  private int requireValidSlotCost(Gadget gadget) {
    if (gadget.slotCost() == null) {
      throw new ValidationException("Gadget slot cost is unknown: " + gadget.name());
    }
    if (gadget.slotCost() < 1 || gadget.slotCost() > GadgetPlate.ROW_CAPACITY) {
      throw new ValidationException("Gadget slot cost is invalid: " + gadget.name());
    }
    return gadget.slotCost();
  }

  @Transactional
  public Build create(UUID author, Draft d) {
    validate(d);
    if (d.remixedFromBuildId() != null && builds.find(d.remixedFromBuildId()).isEmpty()) {
      throw new ValidationException("Unknown remix source build ID");
    }
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
            d.remixedFromBuildId(),
            d.gadgetIds(),
            now,
            now);
    builds.save(b);
    return b;
  }

  @Transactional
  public Build edit(UUID id, UUID actor, Draft d) {
    var old = get(id);
    ForbiddenException.requireOwner(old.authorId(), actor);
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
            old.remixedFromBuildId(),
            d.gadgetIds(),
            old.createdAt(),
            Instant.now());
    builds.save(b);
    return b;
  }

  @Transactional
  public void delete(UUID id, UUID actor) {
    ForbiddenException.requireOwner(get(id).authorId(), actor);
    builds.delete(id);
  }
}
