package dev.ringlab.adapter.out.db.build;

import dev.ringlab.domain.build.Build;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jakarta-cdi")
public interface BuildDbMapper {
  Build toDomain(BuildDbEntity entity);

  BuildDbEntity toEntity(Build build);
}
