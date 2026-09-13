package dev.ringlab.port.out;

import dev.ringlab.domain.auth.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
  Optional<User> byId(UUID id);

  Optional<User> byUsername(String username);

  boolean exists(String username, String email);

  /**
   * Creates a user whose username and email have already been normalized. Only username or email
   * uniqueness conflicts are translated to the semantic duplicate-account error.
   */
  void create(User user);
}
