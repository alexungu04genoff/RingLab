package dev.ringlab.adapter.out.steam;

import dev.ringlab.application.ExternalServiceUnavailableException;
import dev.ringlab.domain.news.GameNewsItem;
import dev.ringlab.port.out.GameNewsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.jbosslog.JBossLog;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
@JBossLog
public class SteamNewsAdapter implements GameNewsRepository {
  private final SteamNewsClient client;
  private final int appId;

  @Inject
  public SteamNewsAdapter(@RestClient SteamNewsClient client,
      @ConfigProperty(name = "steam.app-id") int appId) {
    this.client = client;
    this.appId = appId;
  }

  @Override
  public List<GameNewsItem> latest(int count) {
    try {
      var response = client.latest(appId, count, 200);
      if (response == null || response.appnews() == null || response.appnews().newsitems() == null)
        throw new IllegalArgumentException("Missing news list");
      var items = response.appnews().newsitems().stream().limit(count).toList();
      var news = new ArrayList<GameNewsItem>();
      for (int index = 0; index < items.size(); index++) {
        try {
          news.add(newsItem(items.get(index)));
        } catch (IllegalArgumentException | java.time.DateTimeException invalid) {
          // Upstream exception messages can contain URLs or response content.
          log.warnf("Skipped malformed Steam news item at position %d (%s)",
              index + 1, invalid.getClass().getSimpleName());
        }
      }
      if (!items.isEmpty() && news.isEmpty())
        throw new IllegalArgumentException("No usable news items");
      return List.copyOf(news);
    } catch (ProcessingException | WebApplicationException | IllegalArgumentException e) {
      log.warnf("Steam news unavailable (%s, upstream status %s)",
          e.getClass().getSimpleName(),
          e instanceof WebApplicationException http && http.getResponse() != null
              ? http.getResponse().getStatus() : "unavailable");
      throw new ExternalServiceUnavailableException("News unavailable");
    }
  }

  private GameNewsItem newsItem(SteamNewsResponse.NewsItem item) {
    if (item == null || item.gid() == null || item.title() == null || item.date() == null)
      throw new IllegalArgumentException("Incomplete news item");
    URI url = URI.create(item.url() == null ? "" : item.url());
    if (url.getHost() == null || !("https".equalsIgnoreCase(url.getScheme())
        || "http".equalsIgnoreCase(url.getScheme())))
      throw new IllegalArgumentException("Invalid news URL");
    return new GameNewsItem(item.gid(), item.title(), item.url(), Instant.ofEpochSecond(item.date()));
  }
}
