package dev.ringlab.adapter.out.db.auth;

import dev.ringlab.domain.auth.User;
import dev.ringlab.port.out.UserRepository;
import dev.ringlab.application.AlreadyExistsException;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import org.hibernate.exception.ConstraintViolationException;

@ApplicationScoped
public class UserDbAdapter implements UserRepository, PanacheRepositoryBase<UserDbEntity, UUID> {
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
      // Names are defined by the username/email UNIQUE constraints in Flyway V1.
      if ("23505".equals(e.getSQLState())
          && ("users_username_key".equals(e.getConstraintName())
              || "users_email_key".equals(e.getConstraintName()))) {
        throw new AlreadyExistsException("Username or email already registered");
      }
      throw e;
    }
  }
}
