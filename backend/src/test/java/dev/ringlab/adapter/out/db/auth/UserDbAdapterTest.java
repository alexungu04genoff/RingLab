package dev.ringlab.adapter.out.db.auth;

import static org.junit.jupiter.api.Assertions.*;

import dev.ringlab.application.AlreadyExistsException;
import dev.ringlab.domain.auth.User;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class UserDbAdapterTest {
  @Test
  void translatesOnlyUsernameAndEmailUniqueViolations() {
    for (String constraint : new String[] {"users_username_key", "users_email_key"}) {
      var error = assertThrows(AlreadyExistsException.class,
          () -> failingAdapter(violation("23505", constraint)).create(user()));
      assertEquals("Username or email already registered", error.getMessage());
    }
  }

  @Test
  void preservesUnexpectedIntegrityFailures() {
    for (var failure : new ConstraintViolationException[] {
        violation("23505", "users_pkey"), violation("23502", null),
        violation("23514", "users_username_key"), violation("23505", null)}) {
      assertSame(failure, assertThrows(ConstraintViolationException.class,
          () -> failingAdapter(failure).create(user())));
    }
  }

  private UserDbAdapter failingAdapter(ConstraintViolationException failure) {
    return new UserDbAdapter(Mappers.getMapper(UserDbMapper.class)) {
      @Override
      public void persistAndFlush(UserDbEntity entity) {
        throw failure;
      }
    };
  }

  private ConstraintViolationException violation(String state, String constraint) {
    return new ConstraintViolationException("Integrity failure", new SQLException("Failure", state), constraint);
  }

  private User user() {
    return new User(UUID.randomUUID(), "account", "account@example.test", "unused", Instant.now());
  }
}
