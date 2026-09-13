package dev.ringlab.domain.auth;

import java.time.Instant;
import java.util.UUID;

public record User(
    UUID id,
    String username,
    String email,
    String passwordHash,
    Instant emailVerifiedAt,
    Instant createdAt) {
  /** Compatibility constructor for existing, already-verified persisted accounts and test fixtures. */
  public User(UUID id, String username, String email, String passwordHash, Instant createdAt) {
    this(id, username, email, passwordHash, createdAt, createdAt);
  }
}
