package dev.ringlab.domain.vote;

public record VoteSummary(long upvotes, long downvotes) {
  public long score() {
    return upvotes - downvotes;
  }
}
