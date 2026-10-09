package dev.ringlab.adapter.in.rest.gamedata.response;

import dev.ringlab.domain.gamedata.Gadget;
import dev.ringlab.domain.gamedata.GadgetAcquisitionKind;
import java.util.UUID;

public record GadgetResponse(
    UUID id, String name, String description, Integer slotCost, String imagePath,
    GadgetAcquisitionKind acquisitionKind, String acquisitionLabel) {
  public static GadgetResponse from(Gadget gadget) {
    return new GadgetResponse(
        gadget.id(), gadget.name(), gadget.description(), gadget.slotCost(), gadget.imagePath(),
        gadget.acquisitionKind(), gadget.acquisitionLabel());
  }
}
