package dev.ringlab.adapter.out.db.comment;

import dev.ringlab.domain.comment.Comment;
import dev.ringlab.application.NotFoundException;
import org.hibernate.exception.ConstraintViolationException;
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
    try {
      persistAndFlush(mapper.toEntity(c));
    } catch (ConstraintViolationException exception) {
      if ("23503".equals(exception.getSQLState())
          && "comments_build_id_fkey".equals(exception.getConstraintName())) {
        throw NotFoundException.missing("Build");
      }
      throw exception;
    }
  }

  public void delete(UUID id) {
    deleteById(id);
  }
}
