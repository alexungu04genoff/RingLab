package dev.ringlab.adapter.out.db.auth;

import dev.ringlab.application.auth.port.out.UserStore;
import dev.ringlab.domain.auth.User;
import dev.ringlab.application.AppException;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import org.hibernate.exception.ConstraintViolationException;

@ApplicationScoped
public class UserDbAdapter implements UserStore, PanacheRepositoryBase<UserDbEntity, UUID> {
  private final UserDbMapper mapper;

  public UserDbAdapter(UserDbMapper mapper) {
    this.mapper = mapper;
  }

  public Optional<User> byId(UUID id) {
    return findByIdOptional(id).map(mapper::toDomain);
  }

  public Optional<User> byUsername(String username) {
    return find("username", username).firstResultOptional().map(mapper::toDomain);
  }

  public boolean exists(String username, String email) {
    return count("username = ?1 or email = ?2", username, email) > 0;
  }

  public void create(User user) {
    try {
      persistAndFlush(mapper.toEntity(user));
    } catch (ConstraintViolationException e) {
      throw new AppException(409, "Username or email already registered");
    }
  }
}
