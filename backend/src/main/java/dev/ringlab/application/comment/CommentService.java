package dev.ringlab.application.comment;

import lombok.RequiredArgsConstructor;

import dev.ringlab.application.build.BuildService;
import dev.ringlab.domain.comment.Comment;
import dev.ringlab.application.ForbiddenException;
import dev.ringlab.application.NotFoundException;
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
    builds.get(build);
    return comments.list(build, page, size);
  }

  @Transactional
  public Comment create(UUID build, UUID author, String text) {
    builds.get(build);
    var comment = new Comment(UUID.randomUUID(), build, author, text.trim(), Instant.now());
    comments.create(comment);
    return comment;
  }

  @Transactional
  public void delete(UUID id, UUID actor) {
    var c = comments.find(id).orElseThrow(() -> NotFoundException.missing("Comment"));
    ForbiddenException.requireOwner(c.authorId(), actor);
    comments.delete(id);
  }
}
