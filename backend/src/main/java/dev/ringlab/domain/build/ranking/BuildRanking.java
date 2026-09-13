package dev.ringlab.domain.build.ranking;

import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.vote.VoteSummary;
import java.util.Comparator;
import java.util.Map;

public final class BuildRanking {
  private BuildRanking() {}

  public static Comparator<Build> comparator(
      BuildSort sort, Map<java.util.UUID, VoteSummary> summaries) {
    Comparator<Build> newest = Comparator.comparing(Build::createdAt).reversed()
        .thenComparing(build -> build.id().toString());
    if (sort == BuildSort.NEWEST) return newest;

    Comparator<Build> score = Comparator
        .comparingLong((Build build) -> summary(build, summaries).score()).reversed()
        .thenComparing(newest);
    if (sort == BuildSort.SCORE) return score;

    return Comparator
        .comparingInt((Build build) -> signGroup(summary(build, summaries).score())).reversed()
        .thenComparing(
            Comparator.comparingDouble((Build build) -> {
              VoteSummary summary = summary(build, summaries);
              return WilsonScore.lowerBound(summary.upvotes(), summary.downvotes());
            }).reversed())
        .thenComparing(
            Comparator.comparingLong((Build build) -> summary(build, summaries).score()).reversed())
        .thenComparing(newest);
  }

  private static VoteSummary summary(Build build, Map<java.util.UUID, VoteSummary> summaries) {
    return summaries.getOrDefault(build.id(), new VoteSummary(0, 0));
  }

  private static int signGroup(long score) {
    return Long.compare(score, 0);
  }
}
