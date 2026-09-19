package dev.ringlab.domain.gamedata;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BaseStatsTest {
  private BaseStats block(String value) {
    var n = new BigDecimal(value);
    return new BaseStats(n, n, n, n, n);
  }

  @Test
  void addsAllFourContributionsWithoutRoundingAndPreservesRealZero() {
    assertEquals(block("17.5"), BaseStats.sum(List.of(block("10"), block("2.5"), block("4"), block("1"))));
    assertEquals(block("0"), BaseStats.sum(List.of(block("0"), block("0"), block("0"), block("0"))));
  }

  @Test
  void onlyTheStatWithAnUnknownContributionBecomesUnknown() {
    var partial = new BaseStats(BigDecimal.ONE, BigDecimal.ONE, null, BigDecimal.ONE, BigDecimal.ONE);
    var result = BaseStats.sum(List.of(block("10"), partial, block("4"), block("1")));
    assertEquals(new BigDecimal("16"), result.speed());
    assertEquals(new BigDecimal("16"), result.acceleration());
    assertNull(result.handling());
    assertEquals(new BigDecimal("16"), result.power());
    assertEquals(new BigDecimal("16"), result.boost());
  }

  @Test
  void absentRecordOrEmptyCompositionCannotBecomeZero() {
    assertEquals(BaseStats.UNKNOWN, BaseStats.sum(List.of(block("10"), BaseStats.UNKNOWN, block("4"), block("1"))));
    assertEquals(BaseStats.UNKNOWN, BaseStats.sum(List.of()));
  }
}
