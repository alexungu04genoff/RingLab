package dev.ringlab.port.out;

import dev.ringlab.domain.news.GameNewsItem;
import java.util.List;

public interface GameNewsRepository {
  List<GameNewsItem> latest(int count);
}
