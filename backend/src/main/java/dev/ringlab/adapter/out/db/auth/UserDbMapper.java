package dev.ringlab.adapter.out.db.auth;

import dev.ringlab.domain.auth.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "jakarta-cdi")
public interface UserDbMapper {
  User toDomain(UserDbEntity entity);

  UserDbEntity toEntity(User user);
}
