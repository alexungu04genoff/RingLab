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
        .comparingDouble((Candidate candidate) -> wilsonScore(candidate, summaries)).reversed()
        .thenComparing(
            Comparator.comparingLong((Candidate candidate) ->
                zeroScoreEvidence(candidate, summaries)).reversed())
        .thenComparing(newest);
  }

  private static VoteSummary summary(Candidate candidate, Map<UUID, VoteSummary> summaries) {
    return summaries.getOrDefault(candidate.id(), new VoteSummary(0, 0));
  }

  private static double wilsonScore(Candidate candidate, Map<UUID, VoteSummary> summaries) {
    VoteSummary summary = summary(candidate, summaries);
    return WilsonScore.lowerBound(summary.upvotes(), summary.downvotes());
  }

  private static long zeroScoreEvidence(
      Candidate candidate, Map<UUID, VoteSummary> summaries) {
    VoteSummary summary = summary(candidate, summaries);
    return WilsonScore.lowerBound(summary.upvotes(), summary.downvotes()) == 0.0
        ? -summary.downvotes()
        : 0;
  }
}
