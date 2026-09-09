package dev.ringlab.auth.adapter.out.persistence;

import dev.ringlab.auth.domain.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jakarta-cdi")
public interface UserMapper {
  User toDomain(UserEntity entity);

  UserEntity toEntity(User user);
}
