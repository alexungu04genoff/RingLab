package dev.ringlab.comment.adapter.out.persistence;

import dev.ringlab.comment.application.port.out.CommentStore;
import dev.ringlab.comment.domain.Comment;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;

@ApplicationScoped
public class JpaCommentStore implements CommentStore, PanacheRepositoryBase<CommentEntity, UUID> {
  private final CommentMapper mapper;

  public JpaCommentStore(CommentMapper mapper) {
    this.mapper = mapper;
  }

  public List<Comment> list(UUID build, int page, int size) {
    return find("buildId = ?1 order by createdAt,id", build).page(page, size).list().stream()
        .map(mapper::toDomain)
        .toList();
  }

  public Optional<Comment> find(UUID id) {
    return findByIdOptional(id).map(mapper::toDomain);
  }

  public void create(Comment c) {
    persist(mapper.toEntity(c));
  }

  public void delete(UUID id) {
    deleteById(id);
  }
}
