package dev.ringlab.domain.build.recommendation;

import java.math.BigDecimal;

/** A completed, proven filtering stage. Counts refer to fully evaluable legal builds. */
public record BalancedStage(StatPriority stat, BigDecimal lossPercent, BigDecimal best,
    BigDecimal threshold, long candidatesBefore, long candidatesAfter) {
  public static BigDecimal threshold(BigDecimal best, BigDecimal lossPercent) {
    // Positive b: b*(1-loss/100). Negative b: permit further negative values. Zero stays zero.
    return best.subtract(best.abs().multiply(lossPercent.movePointLeft(2)));
  }
}
