package dev.ringlab.comment.application.port.out;

import dev.ringlab.comment.domain.Comment;
import java.util.*;

public interface CommentStore {
  List<Comment> list(UUID buildId, int page, int size);

  Optional<Comment> find(UUID id);

  void create(Comment comment);

  void delete(UUID id);
}
