package dev.ringlab.comment.domain;

import java.time.Instant;
import java.util.UUID;

public record Comment(UUID id, UUID buildId, UUID authorId, String text, Instant createdAt) {}
