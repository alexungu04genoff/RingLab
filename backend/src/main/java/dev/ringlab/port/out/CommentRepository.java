package dev.ringlab.port.out;

import dev.ringlab.domain.comment.Comment;
import java.util.*;

public interface CommentRepository {
  record Page(List<Comment> items, long total) {}

  /**
   * Returns the requested zero-based page in canonical creation-time then ID order. Total is the
   * number of comments for the build before pagination.
   */
  Page list(UUID buildId, int page, int size);

  Optional<Comment> find(UUID id);

  void create(Comment comment);

  void delete(UUID id);
}
