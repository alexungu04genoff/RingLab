package dev.ringlab.adapter.in.rest.auth.response;

import dev.ringlab.domain.auth.User;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String username, String email, Instant createdAt) {
  public static UserResponse from(User user) {
    return new UserResponse(user.id(), user.username(), user.email(), user.createdAt());
  }
}
