package dev.ringlab.comment.application;

import dev.ringlab.build.application.BuildService;
import dev.ringlab.comment.application.port.out.CommentStore;
import dev.ringlab.comment.domain.Comment;
import dev.ringlab.shared.application.AppException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class CommentService {
  private final CommentStore comments;
  private final BuildService builds;

  public CommentService(CommentStore comments, BuildService builds) {
    this.comments = comments;
    this.builds = builds;
  }

  public List<Comment> list(UUID build, int page, int size) {
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
    var c = comments.find(id).orElseThrow(() -> AppException.missing("Comment"));
    AppException.requireOwner(c.authorId(), actor);
    comments.delete(id);
  }
}
