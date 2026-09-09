package dev.ringlab.adapter.in.rest.gamedata;

import dev.ringlab.domain.gamedata.Gadget;
import java.util.UUID;

public record GadgetResponse(
    UUID id, String name, String description, Integer slotCost, String imagePath) {
  public static GadgetResponse from(Gadget gadget) {
    return new GadgetResponse(
        gadget.id(), gadget.name(), gadget.description(), gadget.slotCost(), gadget.imagePath());
  }
}
