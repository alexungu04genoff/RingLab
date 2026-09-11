package dev.ringlab.adapter.in.rest.news.response;

import dev.ringlab.domain.news.GameNewsItem;
import java.time.Instant;

public record GameNewsResponse(String id, String title, String url, Instant publishedAt) {
  public static GameNewsResponse from(GameNewsItem item) {
    return new GameNewsResponse(item.id(), item.title(), item.url(), item.publishedAt());
  }
}
