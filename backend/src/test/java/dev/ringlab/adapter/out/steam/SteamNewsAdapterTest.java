package dev.ringlab.adapter.out.steam;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ringlab.application.ExternalServiceUnavailableException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SteamNewsAdapterTest {
  @Test
  void mapsSteamJsonWithoutLeakingArticleMarkup() throws Exception {
    var response = new ObjectMapper().readValue("""
        {"appnews":{"appid":2486820,"newsitems":[{
          "gid":"123","title":"Update available","url":"https://store.steampowered.com/news/123",
          "date":1700000000,"contents":"<b>Article</b>[img]image[/img]","author":"SEGA"
        }],"count":1}}
        """, SteamNewsResponse.class);
    var adapter = new SteamNewsAdapter((appId, count, maxLength) -> {
      assertEquals(2486820, appId);
      assertEquals(5, count);
      assertEquals(200, maxLength);
      return response;
    }, 2486820);

    var news = adapter.latest(5);
    assertEquals(1, news.size());
    assertEquals("123", news.getFirst().id());
    assertEquals("Update available", news.getFirst().title());
    assertEquals("https://store.steampowered.com/news/123", news.getFirst().url());
    assertEquals(Instant.ofEpochSecond(1700000000), news.getFirst().publishedAt());
  }

  @Test
  void acceptsAnEmptyNewsList() {
    var adapter = new SteamNewsAdapter((appId, count, maxLength) ->
        new SteamNewsResponse(new SteamNewsResponse.AppNews(List.of())), 2486820);
    assertTrue(adapter.latest(5).isEmpty());
  }

  @Test
  void mapsNetworkAndHttpFailuresToSafeUnavailableError() {
    for (RuntimeException failure : List.of(new ProcessingException("external body"),
        new WebApplicationException("external body", 502))) {
      var adapter = new SteamNewsAdapter((appId, count, maxLength) -> { throw failure; }, 2486820);
      var error = assertThrows(ExternalServiceUnavailableException.class, () -> adapter.latest(5));
      assertEquals("News unavailable", error.getMessage());
    }
  }

  @Test
  void rejectsMalformedResponsesAndUnsafeLinks() {
    var malformed = new SteamNewsAdapter((appId, count, maxLength) -> null, 2486820);
    assertThrows(ExternalServiceUnavailableException.class, () -> malformed.latest(5));
    var unsafe = new SteamNewsAdapter((appId, count, maxLength) ->
        new SteamNewsResponse(new SteamNewsResponse.AppNews(List.of(
            new SteamNewsResponse.NewsItem("1", "Title", "javascript:alert(1)", 1700000000L)))), 2486820);
    assertThrows(ExternalServiceUnavailableException.class, () -> unsafe.latest(5));
  }

  @Test
  void limitsBeforeConvertingItemsAndKeepsMalformedItemsWithinTheFailureBoundary() {
    var valid = new SteamNewsResponse.NewsItem("1", "Title", "https://example.test/news", 1700000000L);
    for (var invalid : java.util.Arrays.asList(
        (SteamNewsResponse.NewsItem) null,
        new SteamNewsResponse.NewsItem(null, "Title", "https://example.test/news", 1700000000L),
        new SteamNewsResponse.NewsItem("2", "Title", null, 1700000000L),
        new SteamNewsResponse.NewsItem("2", "Title", "https://example.test/news", Long.MAX_VALUE))) {
      var adapter = new SteamNewsAdapter((appId, count, maxLength) ->
          new SteamNewsResponse(new SteamNewsResponse.AppNews(java.util.Arrays.asList(valid, invalid))), 2486820);
      assertEquals(List.of("1"), adapter.latest(1).stream().map(item -> item.id()).toList());
      var error = assertThrows(ExternalServiceUnavailableException.class, () -> adapter.latest(2));
      assertEquals("News unavailable", error.getMessage());
    }
  }
}
