package dev.ringlab.port.out;

import dev.ringlab.domain.auth.User;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
  Optional<User> byId(UUID id);

  Optional<User> byUsername(String username);

  boolean exists(String username, String email);

  /** Creates an account with independently unique normalized username and email.
   * Concurrent uniqueness conflicts must produce the existing semantic
   * AlreadyExistsException; unrelated storage failures must not be classified as duplicates.
   */
  void create(User user);
}
