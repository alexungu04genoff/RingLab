package dev.ringlab.adapter.in.rest.gamedata.response;

import dev.ringlab.domain.gamedata.Racer;
import java.util.UUID;

public record RacerResponse(UUID id, String name, String racingType, String imagePath) {
  public static RacerResponse from(Racer racer) {
    return new RacerResponse(
        racer.id(), racer.name(), racer.racingType().name(), racer.imagePath());
  }
}
