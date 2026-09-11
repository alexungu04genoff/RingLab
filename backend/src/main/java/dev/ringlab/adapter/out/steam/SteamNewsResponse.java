package dev.ringlab.adapter.out.steam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SteamNewsResponse(AppNews appnews) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AppNews(List<NewsItem> newsitems) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record NewsItem(String gid, String title, String url, Long date) {}
}
