package dev.ringlab.domain.gamedata;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BaseStatsBreakdownTest {
  private final UUID racer = UUID.randomUUID(), front = UUID.randomUUID(),
      rear = UUID.randomUUID(), tire = UUID.randomUUID();

  @Test
  void retainsDecimalPrecisionAndPropagatesOnlyTheUnknownField() {
    var result = BaseStatsBreakdown.calculate(racer, front, rear, tire,
        Map.of(racer, stats("1.25", BigDecimal.ONE)),
        Map.of(front, stats("0", null), rear, stats("2.375", BigDecimal.ONE),
            tire, stats("0.125", BigDecimal.ONE)));

    assertEquals(new BigDecimal("1.25"), result.character().speed());
    assertEquals(new BigDecimal("2.500"), result.machine().speed());
    assertEquals(new BigDecimal("3.750"), result.total().speed());
    assertNull(result.machine().handling());
    assertNull(result.total().handling());
    assertEquals(BigDecimal.ONE, result.character().handling());
    assertEquals(new BigDecimal("4"), result.total().boost());
  }

  @Test
  void omitsUnselectedTiresButTreatsSelectedMissingRowsAsUnknown() {
    var racers = Map.of(racer, stats("1.25", BigDecimal.ONE));
    var parts = Map.of(front, stats("0", BigDecimal.ONE), rear, stats("2.375", BigDecimal.ONE));
    var withoutTire = BaseStatsBreakdown.calculate(racer, front, rear, null, racers, parts);
    assertEquals(new BigDecimal("2.375"), withoutTire.machine().speed());
    assertEquals(new BigDecimal("3.625"), withoutTire.total().speed());
    var missingTire = BaseStatsBreakdown.calculate(racer, front, rear, tire, racers, parts);
    assertEquals(BaseStats.UNKNOWN, missingTire.machine());
    assertEquals(BaseStats.UNKNOWN, missingTire.total());
    assertEquals(new BigDecimal("1.25"), missingTire.character().speed());
  }

  @Test
  void unknownCharacterDoesNotEraseKnownMachineAndMissingFrontIsNotZero() {
    var parts = Map.of(front, stats("0", BigDecimal.ONE), rear, stats("0", BigDecimal.ONE));
    var noCharacter = BaseStatsBreakdown.calculate(null, front, rear, null, Map.of(), parts);
    assertEquals(BaseStats.UNKNOWN, noCharacter.character());
    assertEquals(BigDecimal.ZERO, noCharacter.machine().speed());
    assertEquals(BaseStats.UNKNOWN, noCharacter.total());
    assertEquals(BaseStats.UNKNOWN,
        BaseStatsBreakdown.calculate(null, null, rear, null, Map.of(), parts).machine());
  }

  private BaseStats stats(String speed, BigDecimal handling) {
    return new BaseStats(new BigDecimal(speed), BigDecimal.ONE, handling, BigDecimal.ONE, BigDecimal.ONE);
  }
}
