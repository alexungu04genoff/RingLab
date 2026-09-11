package dev.ringlab.domain.gamedata;

import java.time.LocalDate;
import java.util.UUID;

public record GameVersion(UUID id, String version, LocalDate releasedAt) {}
