package dev.ringlab.gamedata.domain;

import java.util.UUID;

public record GameItem(
    UUID id,
    String name,
    String racingType,
    String description,
    Integer slotCost,
    String imagePath) {}
