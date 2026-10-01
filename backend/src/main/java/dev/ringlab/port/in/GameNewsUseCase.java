package dev.ringlab.port.in;

import dev.ringlab.domain.news.GameNewsItem;
import java.util.List;

public interface GameNewsUseCase {
  List<GameNewsItem> latest();
}
