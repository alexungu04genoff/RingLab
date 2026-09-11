package dev.ringlab.adapter.out.steam;

import dev.ringlab.application.AppException;
import dev.ringlab.domain.news.GameNewsItem;
import dev.ringlab.port.out.GameNewsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import java.net.URI;
import java.time.Instant;
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
      return response.appnews().newsitems().stream().limit(count).map(item -> {
        if (item == null || item.gid() == null || item.title() == null || item.date() == null)
          throw new IllegalArgumentException("Incomplete news item");
        URI url = URI.create(item.url() == null ? "" : item.url());
        if (url.getHost() == null || !("https".equalsIgnoreCase(url.getScheme())
            || "http".equalsIgnoreCase(url.getScheme())))
          throw new IllegalArgumentException("Invalid news URL");
        return new GameNewsItem(item.gid(), item.title(), item.url(), Instant.ofEpochSecond(item.date()));
      }).toList();
    } catch (ProcessingException | WebApplicationException | IllegalArgumentException
        | java.time.DateTimeException e) {
      log.warn("Steam news unavailable");
      throw new AppException(503, "News unavailable");
    }
  }
}
