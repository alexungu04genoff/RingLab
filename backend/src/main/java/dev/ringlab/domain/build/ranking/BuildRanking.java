package dev.ringlab.domain.build.ranking;

import dev.ringlab.domain.vote.VoteSummary;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

public final class BuildRanking {
  private BuildRanking() {}

  public record Candidate(UUID id, Instant createdAt) {}

  public static Comparator<Candidate> comparator(
      BuildSort sort, Map<UUID, VoteSummary> summaries) {
    Comparator<Candidate> newest = Comparator.comparing(Candidate::createdAt).reversed()
        .thenComparing(candidate -> candidate.id().toString());
    if (sort == BuildSort.NEWEST) return newest;

    Comparator<Candidate> score = Comparator
        .comparingLong((Candidate candidate) -> summary(candidate, summaries).score()).reversed()
        .thenComparing(newest);
    if (sort == BuildSort.SCORE) return score;

    return Comparator
        .comparingInt((Candidate candidate) -> signGroup(summary(candidate, summaries).score())).reversed()
        .thenComparing(
            Comparator.comparingDouble((Candidate candidate) -> {
              VoteSummary summary = summary(candidate, summaries);
              return WilsonScore.lowerBound(summary.upvotes(), summary.downvotes());
            }).reversed())
        .thenComparing(
            Comparator.comparingLong((Candidate candidate) -> summary(candidate, summaries).score()).reversed())
        .thenComparing(newest);
  }

  private static VoteSummary summary(Candidate candidate, Map<UUID, VoteSummary> summaries) {
    return summaries.getOrDefault(candidate.id(), new VoteSummary(0, 0));
  }

  private static int signGroup(long score) {
    return Long.compare(score, 0);
  }
}
