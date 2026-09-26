package dev.ringlab.adapter.in.rest.build.response;

import dev.ringlab.adapter.in.rest.gamedata.response.RaceMapResponse;
import dev.ringlab.domain.gamedata.RaceMap;
import java.util.*;

public record MapRecommendationsResponse(Mode mode, List<RaceMapResponse> maps) {
  public enum Mode { ALL, SELECTED }
  public MapRecommendationsResponse { maps = List.copyOf(maps); }

  public static MapRecommendationsResponse from(Set<UUID> ids, List<RaceMap> catalog) {
    var maps = catalog.stream().filter(map -> ids.contains(map.id()))
        .sorted(Comparator.comparingInt(RaceMap::catalogOrder).thenComparing(RaceMap::id))
        .map(RaceMapResponse::from).toList();
    if (maps.size() != ids.size()) throw new IllegalStateException("Missing referenced map catalog entry");
    return new MapRecommendationsResponse(ids.isEmpty() ? Mode.ALL : Mode.SELECTED, maps);
  }
}
