package dev.ringlab.adapter.out.db.auth;

import dev.ringlab.domain.auth.User;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "jakarta-cdi", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserDbMapper {
  User toDomain(UserDbEntity entity);

  UserDbEntity toEntity(User user);
}
