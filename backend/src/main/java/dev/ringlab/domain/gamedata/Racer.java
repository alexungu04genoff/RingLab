package dev.ringlab.domain.gamedata;

import java.util.UUID;

public record Racer(UUID id, String name, String racingType, String imagePath) {}
