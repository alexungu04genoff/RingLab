package dev.ringlab.domain.auth;

import java.time.Instant;
import java.util.UUID;

public record ExternalIdentity(UUID userId, String provider, String subject, Instant createdAt) {}
