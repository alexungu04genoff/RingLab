package dev.ringlab.domain.auth;

import java.time.Instant;
import java.util.UUID;

/** A persisted digest of a single-use email-verification secret. */
public record EmailVerificationToken(UUID userId, String tokenHash, Instant expiresAt, Instant createdAt) {}
