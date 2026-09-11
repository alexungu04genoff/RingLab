package dev.ringlab.adapter.in.rest.news;

import dev.ringlab.adapter.in.rest.news.response.GameNewsResponse;
import dev.ringlab.application.news.GameNewsService;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import lombok.RequiredArgsConstructor;

@Path("/api/news")
@Produces(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class NewsRestResource {
  private final GameNewsService news;

  @GET
  public List<GameNewsResponse> latest() {
    return news.latest().stream().map(GameNewsResponse::from).toList();
  }
}
