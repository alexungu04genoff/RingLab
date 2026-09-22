package dev.ringlab.application.build;

import dev.ringlab.application.ValidationException;
import dev.ringlab.application.gamedata.MachineCompatibility;
import dev.ringlab.application.validation.ProfanityPolicy;
import dev.ringlab.domain.build.GadgetPlate;
import dev.ringlab.domain.gamedata.Gadget;
import dev.ringlab.domain.gamedata.MachineComposition;
import dev.ringlab.domain.gamedata.MachinePart;
import dev.ringlab.domain.gamedata.MachinePartType;
import dev.ringlab.port.out.GameDataRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/** Validates publishable drafts; ownership and remix provenance belong to the use case. */
@ApplicationScoped
@RequiredArgsConstructor
public class BuildDraftValidator {
  private final GameDataRepository game;
  private final ProfanityPolicy profanity;

  void validate(BuildService.Draft draft) {
    if (draft == null) throw new ValidationException("Missing build draft");
    validateText(draft);
    requireRacer(draft.racerId());
    validateMachineParts(draft);
    requireVersion(draft.gameVersionId());
    validateGadgets(draft.gadgetIds());
  }

  private void validateText(BuildService.Draft draft) {
    if (draft.title() == null || draft.title().isBlank() || draft.title().length() > 120)
      throw new ValidationException("Title must be nonblank and at most 120 characters", "title");
    if (draft.description() == null || draft.description().length() > 10000)
      throw new ValidationException("Description is required and must be at most 10000 characters", "description");
    profanity.requireClean(draft.title(), "title");
    profanity.requireClean(draft.description(), "description");
  }

  private void validateMachineParts(BuildService.Draft draft) {
    var front = requirePart(draft.frontPartId(), MachinePartType.FRONT);
    var rear = requirePart(draft.rearPartId(), MachinePartType.REAR);
    var machineType = MachineCompatibility.requireCompatible(game, front, rear);
    if (!MachineComposition.requiredSlots(machineType).contains(MachinePartType.TIRE)) {
      if (draft.tirePartId() != null) {
        throw new ValidationException("Boost machines do not use a tire part");
      }
    } else {
      var tire = requirePart(draft.tirePartId(), MachinePartType.TIRE);
      MachineCompatibility.requireCompatible(game, front, tire);
    }
  }

  private void requireVersion(UUID id) {
    if (id == null) {
      throw new ValidationException("Select a game version / patch", "gameVersionId");
    }
    if (game.findGameVersion(id).isEmpty()) {
      throw new ValidationException("Unknown game version ID");
    }
  }

  private void requireRacer(UUID id) {
    if (id == null) throw new ValidationException("Missing racer ID");
    if (game.findRacer(id).isEmpty()) throw new ValidationException("Unknown racer ID");
  }

  private MachinePart requirePart(UUID id, MachinePartType expectedType) {
    if (id == null) throw new ValidationException("Missing " + expectedType + " part ID");
    var part = game.findMachinePart(id)
        .orElseThrow(() -> new ValidationException("Unknown " + expectedType + " part ID"));
    if (part.type() != expectedType) {
      throw new ValidationException("Expected " + expectedType + " part, got " + part.type());
    }
    return part;
  }

  private void validateGadgets(List<UUID> gadgetIds) {
    if (gadgetIds == null || gadgetIds.stream().anyMatch(java.util.Objects::isNull))
      throw new ValidationException("Gadget IDs are required and must not contain null");
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
}
