package dev.ringlab.vote.domain;

import java.util.UUID;

public record Vote(UUID userId, UUID buildId, int value) {
  public Vote {
    if (value != -1 && value != 1) throw new IllegalArgumentException("Vote must be -1 or 1");
  }
}
