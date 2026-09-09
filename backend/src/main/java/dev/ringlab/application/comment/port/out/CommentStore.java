package dev.ringlab.application.comment.port.out;

import dev.ringlab.domain.comment.Comment;
import java.util.*;

public interface CommentStore {
  List<Comment> list(UUID buildId, int page, int size);

  Optional<Comment> find(UUID id);

  void create(Comment comment);

  void delete(UUID id);
}
