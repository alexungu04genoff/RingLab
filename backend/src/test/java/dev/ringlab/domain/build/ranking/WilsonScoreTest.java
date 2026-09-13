package dev.ringlab.domain.build.ranking;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WilsonScoreTest {
  @Test
  void returnsZeroWithoutVotes() {
    assertEquals(0.0, WilsonScore.lowerBound(0, 0));
  }

  @Test
  void preservesRepresentativeConfidenceValues() {
    assertEquals(0.4385, WilsonScore.lowerBound(3, 0), 0.0001);
    assertEquals(0.8741, WilsonScore.lowerBound(40, 1), 0.0001);
    assertTrue(WilsonScore.lowerBound(40, 1) > WilsonScore.lowerBound(3, 0));
    assertTrue(WilsonScore.lowerBound(20, 20) > WilsonScore.lowerBound(0, 0));
    assertEquals(0.0, WilsonScore.lowerBound(0, 5));
  }
}
