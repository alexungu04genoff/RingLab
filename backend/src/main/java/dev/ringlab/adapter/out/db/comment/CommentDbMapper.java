package dev.ringlab.adapter.out.db.comment;

import dev.ringlab.domain.comment.Comment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jakarta-cdi")
public interface CommentDbMapper {
  Comment toDomain(CommentDbEntity entity);

  CommentDbEntity toEntity(Comment comment);
}
