package dev.ringlab.adapter.in.rest.community;

import dev.ringlab.domain.gamedata.PassiveStatsResult;
import dev.ringlab.port.in.CommunitySnapshot;
import dev.ringlab.port.in.CommunityUseCase;
import jakarta.ws.rs.core.*;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommunityRestResourceTest {
  @Test void conditionalResponsesDoNotProjectOrCalculateSnapshotEntries() {
    // Even an entry that cannot be projected is safe on the conditional-response path.
    var entry = new CommunitySnapshot.Entry(null, null, null, null, null, null, null,
        List.of(), null, null, null, List.of());
    var snapshot = new CommunitySnapshot(UUID.randomUUID(), Instant.EPOCH, List.of(entry));
    CommunityUseCase community = new CommunityUseCase() {
      public CommunitySnapshot get() { return snapshot; }
      public PassiveStatsResult passiveStats(CommunitySnapshot.Entry entry) {
        throw new AssertionError("A 304 response must not calculate stats");
      }
    };
    var resource = new CommunityRestResource(community,
        new CommunityPublicUrls("https://example.test", "https://example.test"));
    var uri = (UriInfo) Proxy.newProxyInstance(UriInfo.class.getClassLoader(), new Class<?>[]{UriInfo.class},
        (proxy, method, args) -> new MultivaluedHashMap<String, String>());
    for (boolean discord : new boolean[]{false, true}) {
      String expectedTag = snapshot.revision() + (discord ? "-discord-v1" : "-json-v1");
      var request = (Request) Proxy.newProxyInstance(Request.class.getClassLoader(), new Class<?>[]{Request.class},
          (proxy, method, args) -> {
            assertEquals(new EntityTag(expectedTag), args[0]);
            return Response.notModified();
          });
      try (var response = discord ? resource.discord(request, uri) : resource.top(request, uri)) {
        assertEquals(304, response.getStatus());
        assertNull(response.getEntity());
        assertEquals(expectedTag, response.getEntityTag().getValue());
        assertEquals("public, no-cache, must-revalidate", response.getHeaderString("Cache-Control"));
      }
    }
  }
}
