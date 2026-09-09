package dev.ringlab.domain.gamedata;

import java.util.UUID;

public record Racer(UUID id, String name, RacingType racingType, String imagePath) {}
