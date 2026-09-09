package dev.ringlab.build.adapter.out.persistence;

import dev.ringlab.build.domain.Build;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jakarta-cdi")
public interface BuildMapper {
  Build toDomain(BuildEntity entity);

  BuildEntity toEntity(Build build);
}
