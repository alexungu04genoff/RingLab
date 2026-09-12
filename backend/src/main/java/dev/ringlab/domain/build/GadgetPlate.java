package dev.ringlab.domain.build;

import java.util.Comparator;
import java.util.List;

public final class GadgetPlate {
  public static final int ROW_COUNT = 2;
  public static final int ROW_CAPACITY = 3;
  public static final int TOTAL_CAPACITY = ROW_COUNT * ROW_CAPACITY;

  private GadgetPlate() {}

  public static boolean canFit(List<Integer> slotCosts) {
    if (slotCosts == null || slotCosts.stream().anyMatch(GadgetPlate::isInvalidCost)) {
      return false;
    }

    var descendingCosts = slotCosts.stream()
        .sorted(Comparator.reverseOrder())
        .toList();
    return canFit(descendingCosts, 0, 0, 0);
  }

  private static boolean isInvalidCost(Integer cost) {
    return cost == null || cost < 1 || cost > ROW_CAPACITY;
  }

  private static boolean canFit(List<Integer> costs, int index, int firstRow, int secondRow) {
    if (index == costs.size()) {
      return true;
    }

    int cost = costs.get(index);
    if (firstRow + cost <= ROW_CAPACITY
        && canFit(costs, index + 1, firstRow + cost, secondRow)) {
      return true;
    }
    return secondRow + cost <= ROW_CAPACITY
        && canFit(costs, index + 1, firstRow, secondRow + cost);
  }
}
