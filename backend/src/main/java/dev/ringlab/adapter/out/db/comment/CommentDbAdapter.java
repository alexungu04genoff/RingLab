package dev.ringlab.adapter.out.db.comment;

import dev.ringlab.domain.comment.Comment;
import dev.ringlab.port.out.CommentRepository;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;

@ApplicationScoped
public class CommentDbAdapter
    implements CommentRepository, PanacheRepositoryBase<CommentDbEntity, UUID> {
  private final CommentDbMapper mapper;

  public CommentDbAdapter(CommentDbMapper mapper) {
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
