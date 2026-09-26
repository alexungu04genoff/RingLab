package dev.ringlab.adapter.in.rest.build;

import dev.ringlab.adapter.in.rest.auth.CurrentUser;
import dev.ringlab.application.build.SavedBuildService;
import dev.ringlab.application.gamedata.BaseStatsService;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.gamedata.BaseStatsBreakdown;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SavedBuildRestResourceTest {
  @Test void optionalStatsFailurePreservesPrivateListAndDoesNotLeakTheCause() {
    var actorId = UUID.randomUUID();
    var service = new SavedBuildService(null,null) {
      @Override public Page list(UUID actor, String search, UUID patch, int page, int size) {
        assertEquals(actorId,actor); return new Page(List.of(),12);
      }
    };
    var actor = new CurrentUser(null,null) { @Override public UUID id() { return actorId; } };
    var stats = new BaseStatsService(null,null) {
      @Override public Map<UUID,BaseStatsBreakdown> buildPage(List<Build> builds) {
        throw new IllegalStateException("Internal database detail");
      }
    };
    var response = new SavedBuildRestResource(service,actor,null,stats).list(null,null,1,12);
    var page = (SavedBuildRestResource.SavedPage) response.getEntity();
    assertEquals(12,page.total()); assertEquals(1,page.page()); assertTrue(page.items().isEmpty());
    assertNull(page.statsByBuildId()); assertEquals("Could not load base stats. Please try again.",page.statsError());
    assertEquals("private, no-store",response.getHeaderString("Cache-Control"));
  }
}
