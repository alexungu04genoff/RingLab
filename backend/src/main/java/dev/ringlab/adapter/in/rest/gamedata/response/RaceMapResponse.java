package dev.ringlab.adapter.in.rest.gamedata.response;

import dev.ringlab.domain.gamedata.RaceMap;
import java.util.UUID;

public record RaceMapResponse(UUID id, String name, RaceMap.Category category,
                              String contentPack, String imagePath, int catalogOrder) {
  public static RaceMapResponse from(RaceMap map) {
    return new RaceMapResponse(map.id(), map.name(), map.category(), map.contentPack(),
        map.imagePath(), map.catalogOrder());
  }
}
