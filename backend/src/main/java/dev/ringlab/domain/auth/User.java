package dev.ringlab.domain.auth;

import java.time.Instant;
import java.util.UUID;

public record User(
    UUID id, String username, String email, String passwordHash, Instant createdAt) {}
