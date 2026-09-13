package dev.ringlab.port.out;

import dev.ringlab.domain.news.GameNewsItem;
import java.util.List;

public interface GameNewsRepository {
  /** At most count latest news items, newest first, or an empty list when none exist.
   * External failures are translated to the semantic ExternalServiceUnavailableException.
   */
  List<GameNewsItem> latest(int count);
}
