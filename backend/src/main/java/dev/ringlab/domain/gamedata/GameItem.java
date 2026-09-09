package dev.ringlab.domain.gamedata;

import java.util.UUID;

public record GameItem(
    UUID id,
    String name,
    String racingType,
    String description,
    Integer slotCost,
    String imagePath) {}
