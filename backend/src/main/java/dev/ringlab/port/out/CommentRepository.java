package dev.ringlab.port.out;

import dev.ringlab.domain.comment.Comment;
import java.util.*;

public interface CommentRepository {
  record Page(List<Comment> items, long total) {}

  Page list(UUID buildId, int page, int size);

  Optional<Comment> find(UUID id);

  void create(Comment comment);

  void delete(UUID id);
}
