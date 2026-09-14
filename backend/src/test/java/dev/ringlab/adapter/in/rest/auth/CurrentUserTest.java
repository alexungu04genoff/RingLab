package dev.ringlab.adapter.in.rest.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.ringlab.application.AuthenticationException;
import dev.ringlab.domain.auth.User;
import dev.ringlab.port.out.UserRepository;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;

class CurrentUserTest {
  @Test
  void acceptsOnlyAValidSubjectForAnAccountThatStillExists() {
    UUID existing = UUID.randomUUID();
    UserRepository users = repositoryContaining(existing);

    assertEquals(existing, new CurrentUser(jwt(existing.toString()), users).id());
    for (String subject : new String[] {"not-a-uuid", UUID.randomUUID().toString()}) {
      var error = assertThrows(AuthenticationException.class,
          () -> new CurrentUser(jwt(subject), users).id());
      assertEquals("Account unavailable", error.getMessage());
    }
  }

  private static JsonWebToken jwt(String subject) {
    return (JsonWebToken) Proxy.newProxyInstance(
        JsonWebToken.class.getClassLoader(), new Class<?>[] {JsonWebToken.class},
        (proxy, method, args) -> method.getName().equals("getSubject") ? subject : null);
  }

  private static UserRepository repositoryContaining(UUID existing) {
    return (UserRepository) Proxy.newProxyInstance(
        UserRepository.class.getClassLoader(), new Class<?>[] {UserRepository.class},
        (proxy, method, args) -> method.getName().equals("byId")
            ? Optional.ofNullable(existing.equals(args[0])
                ? new User(existing, "account", "account@example.test", "hash", java.time.Instant.EPOCH)
                : null)
            : throwUnsupported(method.getName()));
  }

  private static Object throwUnsupported(String method) {
    throw new UnsupportedOperationException(method);
  }
}
