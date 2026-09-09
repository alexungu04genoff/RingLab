package dev.ringlab.adapter.out.db.comment;

import dev.ringlab.domain.comment.Comment;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "jakarta-cdi", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CommentDbMapper {
  Comment toDomain(CommentDbEntity entity);

  CommentDbEntity toEntity(Comment comment);
}
