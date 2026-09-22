package dev.ringlab.adapter.in.rest.community;

import dev.ringlab.adapter.in.rest.build.response.*;
import dev.ringlab.adapter.in.rest.gamedata.response.*;
import dev.ringlab.application.community.*;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.domain.vote.VoteSummary;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CommunityExportTest {
  @Test void urlsUseTrustedOriginsAndRejectUnsafeArtwork() {
    var urls = new CommunityPublicUrls("https://site.example/", "https://assets.example");
    assertTrue(urls.build(UUID.randomUUID()).startsWith("https://site.example/builds/"));
    assertEquals("https://assets.example/assets/racers/tails.png?v=left-facing-artwork", urls.artwork("/assets/racers/tails.png"));
    for (var path : List.of("https://attacker/image.png", "//attacker/image.png", "/assets/racers/../key.png", "/assets/racers/a.png?x=1"))
      assertNull(urls.artwork(path));
    assertNull(urls.artwork(null));
    for (var origin : List.of("ftp://site.example", "https://user@site.example", "https://site.example/path", "https://site.example?q=1"))
      assertThrows(IllegalArgumentException.class, () -> new CommunityPublicUrls(origin, "https://assets.example"));
  }

  @Test void formatsSameOrderUnknownStatsAndNoPrivateFields() throws Exception {
    var entries = new ArrayList<CommunitySnapshot.Entry>();
    for (int i = 0; i < 3; i++) {
      var source = new Machine(UUID.randomUUID(), "Source", RacingType.BOOST, null);
      var front = new MachinePart(UUID.randomUUID(), source.id(), MachinePartType.FRONT);
      var rear = new MachinePart(UUID.randomUUID(), source.id(), MachinePartType.REAR);
      var racer = new Racer(UUID.randomUUID(), "Guest", RacingType.SPEED, "/assets/racers/tails.png");
      var b = new Build(UUID.randomUUID(), "@everyone [click](https://bad.example) **title**", "x".repeat(10000),
          UUID.randomUUID(), racer.id(), front.id(), rear.id(), null, null, null, List.of(), Instant.EPOCH, Instant.EPOCH);
      entries.add(new CommunitySnapshot.Entry(b, "author", racer, new CommunitySnapshot.Part(front, source),
          new CommunitySnapshot.Part(rear, source), null, null,
          List.of(new Gadget(UUID.randomUUID(), "😀".repeat(1000), null, 1, null)), new VoteSummary(3, 1),
          new BaseStatsBreakdown(BaseStats.UNKNOWN, BaseStats.UNKNOWN, BaseStats.UNKNOWN), null));
    }
    var snapshot = new CommunitySnapshot(UUID.randomUUID(), Instant.EPOCH, entries);
    var response = TopBuildsResponse.from(snapshot, new CommunityPublicUrls("http://localhost:5173", "http://localhost:5173"));
    var message = DiscordTopBuildsFormatter.format(response);
    assertEquals(response.items().stream().map(TopBuildsResponse.Item::buildUrl).toList(),
        message.embeds().stream().map(DiscordTopBuildsFormatter.Embed::url).toList());
    assertEquals(3, message.embeds().stream().map(DiscordTopBuildsFormatter.Embed::url).distinct().count());
    assertTrue(message.allowed_mentions().parse().isEmpty());
    assertTrue(message.embeds().stream().mapToInt(e -> e.title().length() + e.description().length()).sum() <= 6000);
    for (var embed : message.embeds()) {
      assertTrue(embed.title().startsWith("#"));
      assertTrue(embed.title().length() <= 256);
      assertTrue(embed.description().length() <= 4096);
      assertFalse(embed.title().contains("@everyone"));
      assertFalse(embed.title().contains("https://"));
      assertFalse(embed.title().contains("["));
      assertFalse(embed.title().contains("**"));
      assertTrue(embed.description().contains("None (Boost)"));
    }
    assertNull(response.items().getFirst().stats().speed());
    assertNull(response.items().getFirst().build().tirePart());
    var json = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsString(response);
    assertFalse(json.contains("email"));
    assertFalse(json.contains("password"));
    assertFalse(json.contains("myVote"));
    assertFalse(json.contains("revision"));
  }

  @Test void emptyIsAValidMessageAndSanitizingHandlesControlCharacters() {
    var message = DiscordTopBuildsFormatter.format(new TopBuildsResponse(1, "best-rated", "overall", "all", Instant.EPOCH, List.of()));
    assertFalse(message.content().isBlank());
    assertTrue(message.embeds().isEmpty());
    assertEquals("a b c", DiscordTopBuildsFormatter.safe("a\nb\u202Ec", 100));
    assertEquals("😀…", DiscordTopBuildsFormatter.safe("😀😀😀", 4));
  }
}
