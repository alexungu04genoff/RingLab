package dev.ringlab.domain.build.ranking;

public final class WilsonScore {
  private static final double Z = 1.96;

  private WilsonScore() {}

  public static double lowerBound(long upvotes, long downvotes) {
    long total = upvotes + downvotes;
    if (total == 0 || upvotes == 0) return 0.0;

    double proportion = (double) upvotes / total;
    double zSquared = Z * Z;
    double numerator = proportion + zSquared / (2.0 * total)
        - Z * Math.sqrt(
            proportion * (1.0 - proportion) / total
                + zSquared / (4.0 * total * total));
    return numerator / (1.0 + zSquared / total);
  }
}
