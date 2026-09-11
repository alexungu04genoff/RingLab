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

  public Page list(UUID build, int page, int size) {
    var query = find("buildId = ?1 order by createdAt,id", build);
    var items = query.page(page, size).list().stream().map(mapper::toDomain).toList();
    return new Page(items, count("buildId", build));
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
