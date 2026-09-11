package dev.ringlab.adapter.in.rest.news;

import dev.ringlab.adapter.in.rest.ErrorRestExceptionMapper;
import dev.ringlab.application.AppException;
import dev.ringlab.application.news.GameNewsService;
import dev.ringlab.domain.news.GameNewsItem;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NewsRestResourceTest {
  @Test
  void mapsDomainItemsToPublicResponses() {
    var item = new GameNewsItem("123", "Update", "https://example.com/news", Instant.EPOCH);
    var resource = new NewsRestResource(new GameNewsService(count -> List.of(item)));
    var response = resource.latest().getFirst();
    assertEquals(item.id(), response.id());
    assertEquals(item.title(), response.title());
    assertEquals(item.url(), response.url());
    assertEquals(item.publishedAt(), response.publishedAt());
  }

  @Test
  void unavailableNewsUsesExistingSafeErrorMapping() {
    var resource = new NewsRestResource(new GameNewsService(count -> {
      throw new AppException(503, "News unavailable");
    }));
    var error = assertThrows(AppException.class, resource::latest);
    try (var response = new ErrorRestExceptionMapper().toResponse(error)) {
      assertEquals(503, response.getStatus());
      assertEquals(new ErrorRestExceptionMapper.ErrorResponse("News unavailable"), response.getEntity());
    }
  }
}
