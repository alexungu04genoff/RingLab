package dev.ringlab.domain.comment;

import java.time.Instant;
import java.util.UUID;

public record Comment(UUID id, UUID buildId, UUID authorId, String text, Instant createdAt) {}
