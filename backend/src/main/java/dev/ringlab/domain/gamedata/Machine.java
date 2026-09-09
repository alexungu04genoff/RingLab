package dev.ringlab.domain.gamedata;

import java.util.UUID;

public record Machine(UUID id, String name, String racingType, String imagePath) {}
