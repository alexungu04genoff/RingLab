package dev.ringlab.adapter.in.rest.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ringlab.adapter.in.rest.build.request.BuildRequest;
import dev.ringlab.adapter.in.rest.build.response.MapRecommendationsResponse;
import dev.ringlab.domain.gamedata.RaceMap;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapRecommendationsContractTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test void distinguishesOmittedEmptyAndSelectedAndRejectsInvalidJsonValues() throws Exception {
    assertNull(json.readValue("{}", BuildRequest.class).draft().recommendedMapIds());
    assertEquals(List.of(), json.readValue("{\"recommendedMapIds\":[]}", BuildRequest.class).draft().recommendedMapIds());
    var id = UUID.randomUUID();
    assertEquals(List.of(id), json.readValue("{\"recommendedMapIds\":[\"" + id + "\"]}",
        BuildRequest.class).draft().recommendedMapIds());
    for (var value : List.of("null", "{}", "42", "\"id\"", "[null]", "[42]", "[\"1-1-1-1-1\"]", "[\"unknown\"]"))
      assertThrows(com.fasterxml.jackson.core.JsonProcessingException.class,
          () -> json.readValue("{\"recommendedMapIds\":" + value + "}", BuildRequest.class));
  }

  @Test void displaysSetsInCatalogOrderAndKeepsFullCatalogSelectionExplicit() {
    var first = new RaceMap(UUID.randomUUID(), "First", RaceMap.Category.MAIN_COURSE, null, null, 1);
    var second = new RaceMap(UUID.randomUUID(), "Second", RaceMap.Category.CROSSWORLD, null, null, 2);
    var catalog = List.of(second, first);
    var result = MapRecommendationsResponse.from(Set.of(second.id(), first.id()), catalog);
    assertEquals(MapRecommendationsResponse.Mode.SELECTED, result.mode());
    assertEquals(List.of(first.id(), second.id()), result.maps().stream().map(m -> m.id()).toList());
    var all = MapRecommendationsResponse.from(Set.of(), catalog);
    assertEquals(MapRecommendationsResponse.Mode.ALL, all.mode());
    assertTrue(all.maps().isEmpty());
    assertThrows(IllegalStateException.class,
        () -> MapRecommendationsResponse.from(Set.of(UUID.randomUUID()), catalog));
  }
}
