package dev.ringlab.application.comment;

import lombok.RequiredArgsConstructor;

import dev.ringlab.application.build.BuildService;
import dev.ringlab.domain.comment.Comment;
import dev.ringlab.application.ForbiddenException;
import dev.ringlab.application.NotFoundException;
import dev.ringlab.application.ValidationException;
import dev.ringlab.port.out.CommentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
@RequiredArgsConstructor
public class CommentService {
  private final CommentRepository comments;
  private final BuildService builds;

  public CommentRepository.Page list(UUID build, int page, int size) {
    if (page < 0 || size < 1 || size > 50)
      throw new ValidationException("Page must be nonnegative and size must be between 1 and 50");
    builds.get(build);
    return comments.list(build, page, size);
  }

  @Transactional
  public Comment create(UUID build, UUID author, String text) {
    if (author == null) throw new ValidationException("Missing author ID");
    if (text == null || text.isBlank() || text.length() > 2000)
      throw new ValidationException("Comment must be nonblank and at most 2000 characters");
    builds.get(build);
    var comment = new Comment(UUID.randomUUID(), build, author, text.trim(), Instant.now());
    comments.create(comment);
    return comment;
  }

  @Transactional
  public void delete(UUID id, UUID actor) {
    if (id == null) throw new ValidationException("Missing comment ID");
    var c = comments.find(id).orElseThrow(() -> NotFoundException.missing("Comment"));
    ForbiddenException.requireOwner(c.authorId(), actor);
    comments.delete(id);
  }
}
