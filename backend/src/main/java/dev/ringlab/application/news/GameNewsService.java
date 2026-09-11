package dev.ringlab.application.news;

import dev.ringlab.domain.news.GameNewsItem;
import dev.ringlab.port.out.GameNewsRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class GameNewsService {
  private final GameNewsRepository news;

  public List<GameNewsItem> latest() {
    return news.latest(5);
  }
}
