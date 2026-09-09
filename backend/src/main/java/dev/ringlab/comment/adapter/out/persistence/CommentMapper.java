package dev.ringlab.comment.adapter.out.persistence;

import dev.ringlab.comment.domain.Comment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jakarta-cdi")
public interface CommentMapper {
  Comment toDomain(CommentEntity entity);

  CommentEntity toEntity(Comment comment);
}
