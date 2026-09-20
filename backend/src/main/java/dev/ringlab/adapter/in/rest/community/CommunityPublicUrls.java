package dev.ringlab.adapter.in.rest.community;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class CommunityPublicUrls {
  private final String site;
  private final String assets;

  @Inject
  public CommunityPublicUrls(@ConfigProperty(name = "ringlab.public-base-url") String site,
      @ConfigProperty(name = "ringlab.community.asset-origin") String assets) {
    this.site = origin(site);
    this.assets = origin(assets);
  }

  private static String origin(String value) {
    var uri = URI.create(value);
    if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
        || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
        || uri.getFragment() != null || !(uri.getPath().isEmpty() || uri.getPath().equals("/")))
      throw new IllegalArgumentException("Public URLs must be HTTP(S) origins without credentials or paths");
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  public String build(UUID id) { return site + "/builds/" + id; }

  public String artwork(String path) {
    return path != null && path.matches("/assets/racers/[a-z0-9-]+\\.(png|webp|jpg)")
        ? assets + path + "?v=left-facing-artwork" : null;
  }
}
