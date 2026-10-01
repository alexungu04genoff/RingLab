package dev.ringlab.port.in;

import dev.ringlab.domain.auth.User;
import java.util.UUID;

/** Local sign-in, account lookup and validity of an already authenticated session. */
public interface AuthUseCase {
  User login(String username, String password);
  User current(UUID id);
  void validateSession(UUID userId, String authVersion);
}
