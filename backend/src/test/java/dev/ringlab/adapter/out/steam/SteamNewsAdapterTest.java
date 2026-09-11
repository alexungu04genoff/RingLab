package dev.ringlab.adapter.out.steam;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ringlab.application.AppException;
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
      var error = assertThrows(AppException.class, () -> adapter.latest(5));
      assertEquals(503, error.status);
      assertEquals("News unavailable", error.getMessage());
    }
  }

  @Test
  void rejectsMalformedResponsesAndUnsafeLinks() {
    var malformed = new SteamNewsAdapter((appId, count, maxLength) -> null, 2486820);
    assertEquals(503, assertThrows(AppException.class, () -> malformed.latest(5)).status);
    var unsafe = new SteamNewsAdapter((appId, count, maxLength) ->
        new SteamNewsResponse(new SteamNewsResponse.AppNews(List.of(
            new SteamNewsResponse.NewsItem("1", "Title", "javascript:alert(1)", 1700000000L)))), 2486820);
    assertEquals(503, assertThrows(AppException.class, () -> unsafe.latest(5)).status);
  }
}
