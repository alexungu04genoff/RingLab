package dev.ringlab.domain.gamedata;

import java.util.UUID;

public record Gadget(UUID id, String name, String description, Integer slotCost, String imagePath) {}
