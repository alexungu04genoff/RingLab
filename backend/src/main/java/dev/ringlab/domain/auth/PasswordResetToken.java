package dev.ringlab.domain.auth;

import java.time.Instant;
import java.util.UUID;

public record PasswordResetToken(UUID userId, String tokenHash, Instant createdAt, Instant expiresAt) {}
