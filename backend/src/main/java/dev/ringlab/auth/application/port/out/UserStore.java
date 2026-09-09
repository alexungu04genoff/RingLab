package dev.ringlab.auth.application.port.out;

import dev.ringlab.auth.domain.User;
import java.util.Optional;
import java.util.UUID;

public interface UserStore {
  Optional<User> byId(UUID id);

  Optional<User> byUsername(String username);

  boolean exists(String username, String email);

  void create(User user);
}
