package dev.ringlab.domain.gamedata;

import java.util.UUID;

public record Gadget(UUID id, String name, String description, Integer slotCost, String imagePath,
    GadgetAcquisitionKind acquisitionKind, String acquisitionLabel) {
  public Gadget(UUID id, String name, String description, Integer slotCost, String imagePath) {
    this(id, name, description, slotCost, imagePath, GadgetAcquisitionKind.UNKNOWN, null);
  }
}
