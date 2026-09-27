package dev.ringlab.adapter.in.rest.build;

import dev.ringlab.adapter.in.rest.auth.CurrentUser;
import dev.ringlab.application.build.SavedBuildService;
import dev.ringlab.application.gamedata.PassiveStatsService;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.gamedata.PassiveStatsResult;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SavedBuildRestResourceTest {
  @Test void optionalStatsFailurePreservesPrivateListAndDoesNotLeakTheCause() {
    var actorId = UUID.randomUUID();
    var service = new SavedBuildService(null,null,null) {
      @Override public Page list(UUID actor, String search, UUID patch, UUID mapId, boolean includeAllMaps, int page, int size) {
        assertEquals(actorId,actor); return new Page(List.of(),12);
      }
    };
    var actor = new CurrentUser(null,null) { @Override public UUID id() { return actorId; } };
    var stats = new PassiveStatsService(null,null) {
      @Override public Map<UUID,PassiveStatsResult> buildPage(List<Build> builds) {
        throw new IllegalStateException("Internal database detail");
      }
    };
    var response = new SavedBuildRestResource(service,actor,null,stats,null).list(null,null,null,"true",1,12);
    var page = (SavedBuildRestResource.SavedPage) response.getEntity();
    assertEquals(12,page.total()); assertEquals(1,page.page()); assertTrue(page.items().isEmpty());
    assertNull(page.statsByBuildId()); assertEquals("Could not load base stats. Please try again.",page.statsError());
    assertEquals("private, no-store",response.getHeaderString("Cache-Control"));
  }
}
