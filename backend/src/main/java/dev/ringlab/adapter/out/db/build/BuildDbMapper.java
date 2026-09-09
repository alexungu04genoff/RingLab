package dev.ringlab.adapter.out.db.build;

import dev.ringlab.domain.build.Build;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "jakarta-cdi", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BuildDbMapper {
  Build toDomain(BuildDbEntity entity);

  BuildDbEntity toEntity(Build build);
}
