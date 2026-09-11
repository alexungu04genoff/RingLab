package dev.ringlab.adapter.in.rest.gamedata.response;

import dev.ringlab.domain.gamedata.GameVersion;
import java.time.LocalDate;
import java.util.UUID;

public record GameVersionResponse(UUID id, String version, LocalDate releasedAt) {
  public static GameVersionResponse from(GameVersion version) {
    return new GameVersionResponse(version.id(), version.version(), version.releasedAt());
  }
}
