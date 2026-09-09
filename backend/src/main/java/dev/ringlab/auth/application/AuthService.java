package dev.ringlab.auth.application;

import dev.ringlab.auth.application.port.out.UserStore;
import dev.ringlab.auth.domain.User;
import dev.ringlab.shared.application.AppException;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.*;

@ApplicationScoped
public class AuthService {
  private final UserStore users;
  // Equal-cost check for nonexistent accounts avoids a cheap username timing oracle.
  private final String dummyHash = BcryptUtil.bcryptHash("ringlab-dummy-password");

  public AuthService(UserStore users) {
    this.users = users;
  }

  @Transactional
  public User register(String username, String email, String password) {
    username = username.toLowerCase(Locale.ROOT);
    email = email.toLowerCase(Locale.ROOT);
    if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new AppException(400, "Password must be at most 72 UTF-8 bytes");
    if (users.exists(username, email))
      throw new AppException(409, "Username or email already registered");
    User user =
        new User(
            UUID.randomUUID(), username, email, BcryptUtil.bcryptHash(password), Instant.now());
    users.create(user);
    return user;
  }

  public User login(String username, String password) {
    var user = users.byUsername(username.toLowerCase(Locale.ROOT));
    boolean valid = BcryptUtil.matches(password, user.map(User::passwordHash).orElse(dummyHash));
    if (user.isEmpty() || !valid) throw new AppException(401, "Invalid username or password");
    return user.get();
  }

  public User current(UUID id) {
    return users.byId(id).orElseThrow(() -> AppException.missing("User"));
  }
}
