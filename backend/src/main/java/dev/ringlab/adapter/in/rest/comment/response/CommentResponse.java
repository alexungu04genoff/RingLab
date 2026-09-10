package dev.ringlab.adapter.in.rest.comment.response;

import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
    UUID id, UUID buildId, UUID authorId, String author, String text, Instant createdAt) {}
