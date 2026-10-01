package dev.ringlab.port.in;

import dev.ringlab.domain.comment.Comment;
import java.util.List;
import java.util.UUID;

public interface CommentUseCase {
  record Page(List<Comment> items, long total) {}
  Page list(UUID build, int page, int size);
  Comment create(UUID build, UUID author, String text);
  void delete(UUID id, UUID actor);
}
