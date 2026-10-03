package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.gamedata.BaseStats;
import java.util.*;

/** Active lexicographic comparison followed by the shared convenience ordering. */
final class RecommendationCandidateOrder {
  record Candidate(BuildSelection selection, BaseStats stats) {}
  static final Comparator<UUID> IDS = Comparator.comparing(UUID::toString);

  private final RecommendationCatalog catalog;
  private final RecommendationRequest request;
  private Candidate best;

  RecommendationCandidateOrder(RecommendationCatalog catalog, RecommendationRequest request) {
    this.catalog = catalog;
    this.request = request;
  }

  Candidate best() { return best; }
  void seed(BuildSelection selection, BaseStats stats) { best = new Candidate(selection, stats); }
  void clear() { best = null; }

  void consider(Candidate candidate) {
    if (best == null || compare(candidate, best) > 0) best = candidate;
  }

  private int compare(Candidate left, Candidate right) {
    int stats = StatPriority.compare(left.stats(), right.stats(), request.activePriorities());
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
