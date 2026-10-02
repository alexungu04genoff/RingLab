package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.gamedata.BaseStats;
import java.util.*;

/** Exact objective and convenience tie-breaks, plus the incumbent and observed Balanced ties. */
final class RecommendationCandidateOrder {
  record Candidate(BuildSelection selection, BaseStats stats) {}
  static final Comparator<UUID> IDS = Comparator.comparing(UUID::toString);

  private final RecommendationCatalog catalog;
  private final RecommendationRequest request;
  private Candidate best;
  private BalancedObjective balanced;
  private boolean secondaryTieBreakDecided;

  RecommendationCandidateOrder(RecommendationCatalog catalog, RecommendationRequest request) {
    this.catalog = catalog;
    this.request = request;
  }

  Candidate best() { return best; }
  boolean secondaryTieBreakDecided() { return secondaryTieBreakDecided; }
  void seed(BuildSelection selection, BaseStats stats) { best = new Candidate(selection, stats); }
  void clear() { best = null; }
  void useBalancedObjective(BalancedObjective objective) { balanced = objective; }

  void consider(Candidate candidate) {
    if (balanced != null) {
      if (!balanced.feasible(candidate.stats())) return;
      if (best != null) {
        int active = balanced.compareActive(candidate.stats(), best.stats());
        if (active > 0) secondaryTieBreakDecided = false;
        else if (active == 0 && balanced.secondaryTotal(candidate.stats()).compareTo(balanced.secondaryTotal(best.stats())) != 0)
          secondaryTieBreakDecided = true;
      }
    }
    if (best == null || compare(candidate, best) > 0) best = candidate;
  }

  private int compare(Candidate left, Candidate right) {
    int stats = balanced == null ? StatPriority.compare(left.stats(), right.stats(), request.priorities())
        : balanced.compare(left.stats(), right.stats());
    if (stats != 0) return stats;
    int changes = Integer.compare(changes(right.selection()), changes(left.selection()));
    if (changes != 0) return changes;
    int additions = Long.compare(newGadgets(right.selection()), newGadgets(left.selection()));
    if (additions != 0) return additions;
    int costs = Integer.compare(cost(right.selection()), cost(left.selection()));
    if (costs != 0) return costs;
    return stableKey(right.selection()).compareTo(stableKey(left.selection()));
  }

  private int changes(BuildSelection selection) {
    var current = request.current();
    int components = (Objects.equals(selection.racerId(), current.racerId()) ? 0 : 1)
        + (Objects.equals(selection.frontPartId(), current.frontPartId()) ? 0 : 1)
        + (Objects.equals(selection.rearPartId(), current.rearPartId()) ? 0 : 1)
        + (Objects.equals(selection.tirePartId(), current.tirePartId()) ? 0 : 1);
    return components + (int)newGadgets(selection)
        + (int)current.gadgetIds().stream().filter(id -> !selection.gadgetIds().contains(id)).count();
  }

  private long newGadgets(BuildSelection selection) {
    return selection.gadgetIds().stream().filter(id -> !request.current().gadgetIds().contains(id)).count();
  }

  private int cost(BuildSelection selection) {
    return selection.gadgetIds().stream().mapToInt(id -> catalog.gadgets().get(id).slotCost()).sum();
  }

  private static String stableKey(BuildSelection selection) {
    return selection.racerId() + "/" + selection.frontPartId() + "/" + selection.rearPartId() + "/"
        + selection.tirePartId() + "/" + selection.gadgetIds().stream().sorted(IDS).toList();
  }

}
