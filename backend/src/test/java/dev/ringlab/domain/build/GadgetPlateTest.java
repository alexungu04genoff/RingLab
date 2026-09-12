package dev.ringlab.domain.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GadgetPlateTest {
  @Test
  void acceptsEverySupportedPlateShape() {
    for (var costs : List.of(
        List.<Integer>of(),
        List.of(1),
        List.of(3),
        List.of(1, 1, 1),
        List.of(2, 1),
        List.of(3, 3),
        List.of(3, 2, 1),
        List.of(3, 1, 1, 1),
        List.of(2, 2, 1, 1),
        List.of(1, 1, 1, 1, 1, 1))) {
      assertTrue(GadgetPlate.canFit(costs), () -> "Expected valid costs: " + costs);
    }
  }

  @Test
  void validityDoesNotDependOnPresentationOrder() {
    assertTrue(GadgetPlate.canFit(List.of(1, 2, 2, 1)));
    assertTrue(GadgetPlate.canFit(List.of(2, 1, 1, 2)));
    assertTrue(GadgetPlate.canFit(List.of(2, 1, 2, 1)));
  }

  @Test
  void rejectsCombinationsThatCannotBePartitionedAcrossTwoRows() {
    for (var costs : List.of(
        List.of(2, 2, 2),
        List.of(3, 3, 1),
        List.of(3, 2, 2),
        List.of(2, 2, 1, 1, 1),
        List.of(1, 1, 1, 1, 1, 1, 1))) {
      assertFalse(GadgetPlate.canFit(costs), () -> "Expected invalid costs: " + costs);
    }
  }

  @Test
  void rejectsUnknownAndOutOfRangeCosts() {
    assertFalse(GadgetPlate.canFit(null));
    assertFalse(GadgetPlate.canFit(java.util.Arrays.asList(1, null)));
    assertFalse(GadgetPlate.canFit(List.of(0)));
    assertFalse(GadgetPlate.canFit(List.of(-1)));
    assertFalse(GadgetPlate.canFit(List.of(4)));
  }
}
