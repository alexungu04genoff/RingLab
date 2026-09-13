package dev.ringlab.port.out;

import dev.ringlab.domain.comment.Comment;
import java.util.*;

public interface CommentRepository {
  record Page(List<Comment> items, long total) {}

  /** Comments for this build ordered by createdAt ascending, then UUID ascending
   * (canonical textual/unsigned order), before zero-based pagination. Total counts all
   * matching comments, including on an empty/out-of-range page. Requires page >= 0,
   * 1 <= size <= 50. Concurrent writes may occur between item and count reads.
   */
  Page list(UUID buildId, int page, int size);

  Optional<Comment> find(UUID id);

  void create(Comment comment);

  void delete(UUID id);
}
