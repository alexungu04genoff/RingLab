package dev.ringlab.application.news;

import dev.ringlab.application.AppException;
import dev.ringlab.domain.news.GameNewsItem;
import dev.ringlab.port.out.GameNewsRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GameNewsServiceTest {
  @Test
  void requestsFiveLatestItemsFromThePort() {
    var items = List.of(new GameNewsItem("1", "News", "https://example.com/news", Instant.EPOCH));
    GameNewsRepository repository = count -> {
      assertEquals(5, count);
      return items;
    };
    assertEquals(items, new GameNewsService(repository).latest());
  }

  @Test
  void preservesEmptyResultsAndControlledFailure() {
    assertTrue(new GameNewsService(count -> List.of()).latest().isEmpty());
    var failure = new AppException(503, "News unavailable");
    var service = new GameNewsService(count -> { throw failure; });
    assertSame(failure, assertThrows(AppException.class, service::latest));
  }
}
