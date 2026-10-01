package dev.ringlab.port.in;

import dev.ringlab.domain.auth.User;
import java.util.UUID;

public interface ExternalAuthUseCase {
  User login(String credential);
  void link(UUID userId, String credential);
}
