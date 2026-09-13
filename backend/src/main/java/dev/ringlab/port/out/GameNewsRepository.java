package dev.ringlab.port.out;

import dev.ringlab.domain.news.GameNewsItem;
import java.util.List;

public interface GameNewsRepository {
  /**
   * Returns at most {@code count} latest news items in the source's latest-first order. External
   * transport or malformed-response failures are translated to an unavailable-news error.
   */
  List<GameNewsItem> latest(int count);
}
