package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.collection.CollectionExclusions;
import dev.ringlab.domain.gamedata.*;
import java.time.Duration;
import java.util.*;
import java.util.function.LongSupplier;
import static dev.ringlab.domain.build.recommendation.RecommendationResult.Outcome.*;
import dev.ringlab.domain.build.recommendation.RecommendationSearchBudget.SearchLimit;

/** Orchestrates validated, bounded Strict or Balanced search and its unchanged result explanations. */
public final class BuildRecommendationSolver {
  public record Budget(long maxWork, Duration time) {
    public Budget {
      if (maxWork < 1 || time == null || time.isNegative() || time.isZero())
        throw new IllegalArgumentException("Search budget must be positive");
    }
  }
  private final RecommendationCatalog catalog;
  private final RecommendationRequest request;
  private final RecommendationSearchBudget budget;
  private final RecommendationCandidates candidates;
  private final RecommendationCandidateOrder order;
  private final RecommendationGadgetSearch gadgets;
  private final Set<String> restrictions = new LinkedHashSet<>();
  private BaseStats currentStats;
  private ScenarioStatsResult currentScenario;
  private BalancedObjective balanced;

  public BuildRecommendationSolver(RecommendationCatalog catalog, RecommendationRequest request, Budget budget) {
    this(catalog, request, budget, System::nanoTime);
  }

  public BuildRecommendationSolver(RecommendationCatalog catalog, RecommendationRequest request, Budget budget,
      CollectionExclusions availability) {
    this(catalog, request, budget, System::nanoTime, availability);
  }

  BuildRecommendationSolver(RecommendationCatalog catalog, RecommendationRequest request, Budget budget, LongSupplier clock) {
    this(catalog, request, budget, clock, CollectionExclusions.NONE);
  }

  private BuildRecommendationSolver(RecommendationCatalog catalog, RecommendationRequest request, Budget budget,
      LongSupplier clock, CollectionExclusions availability) {
    this.catalog = Objects.requireNonNull(catalog);
    Objects.requireNonNull(availability);
    this.request = Objects.requireNonNull(request);
    this.budget = new RecommendationSearchBudget(Objects.requireNonNull(budget), clock);
    this.candidates = new RecommendationCandidates(catalog, request, availability);
    this.order = new RecommendationCandidateOrder(catalog, request);
    this.gadgets = new RecommendationGadgetSearch(catalog, request, availability, candidates, this.budget);
  }

  public RecommendationResult solve() {
    candidates.validate();
    if (!PassiveGadgetRules.VERSION.equals(catalog.version().version()))
      return result(UNAVAILABLE, "Recommendation requires the reviewed Ver. " + PassiveGadgetRules.VERSION
          + " ruleset. The selected patch has not been changed.");
    if (request.basis() == RecommendationBasis.PASSIVE) {
      restrictions.add("Only fully known base contributions and reviewed passive combinations compete; unreviewed effects and unresolved stacking are excluded.");
      restrictions.add("Race-time, conditional and utility effects are not the objective. Current gadget costs and the complete two-row plate are used.");
    } else {
      restrictions.add("Only complete base, passive and scenario totals compete for the supplied context; unknown numerical conditions and unresolved stacking are excluded.");
      restrictions.add("Utility effects do not contribute points. Current gadget costs and the complete two-row plate are used. This is modeled stat optimization, not race simulation.");
      restrictions.add("The exact Quick Starter + Sea Dog additive interaction is permitted as ASSUMED, not VERIFIED; no other scenario stacking permission is implied.");
    }
    try {
      budget.step();
      var currentType = candidates.legalType(request.current());
      if (currentType != null) {
        var evaluation = candidates.evaluate(request.current(), currentType);
        currentScenario = evaluation.scenario();
        if (evaluation.eligible()) {
          currentStats = evaluation.total();
          if (currentType == request.machineType() && candidates.available(request.current())) order.seed(request.current(), currentStats);
        }
      }

      if (request.mode() == RecommendationMode.BALANCED) {
        // Even secondary values must be known. Do not substitute base-only or zero stats.
        if (currentStats == null || !StatPriority.complete(currentStats)
            || Arrays.stream(StatPriority.values()).anyMatch(s -> s.value(currentStats).signum() < 0)) {
          order.clear();
          return result(UNAVAILABLE, "Balanced unavailable: the frozen reference must be a complete legal setup with fully supported, nonnegative " + objectiveName() + " values for all five stats.");
        }
        balanced = new BalancedObjective(currentStats, request.priorities(), request.balanced());
        order.useBalancedObjective(balanced);
      }

      // Check feasibility before data completeness: unknown contributions are not an empty legal catalog.
      var racers = candidates.racers();
      var fronts = candidates.parts(MachinePartType.FRONT, request.locked().frontPartId());
      var rears = candidates.parts(MachinePartType.REAR, request.locked().rearPartId());
      var tires = request.machineType() == RacingType.BOOST ? List.<MachinePart>of()
          : candidates.parts(MachinePartType.TIRE, request.locked().tirePartId());
      if (racers.isEmpty() || fronts.isEmpty() || rears.isEmpty()
          || (request.machineType() != RacingType.BOOST && tires.isEmpty()))
        return result(NO_LEGAL_COMPLETION, "No legal catalog completion exists for the chosen machine type and locks.");
      if (balanced != null) {
        new BalancedRecommendationSearch(catalog, request, gadgets, order, budget, restrictions, balanced)
            .search(racers, fronts, rears, tires);
        return order.best() == null ? result(NO_FEASIBLE_CANDIDATE, "Complete supported search found no candidate satisfying every loss floor, chosen machine type and lock. Limits have not been relaxed.")
            : result(ESTABLISHED, explanation(true));
      }
      if (!new StrictRecommendationSearch(catalog, request, candidates, gadgets, order, budget, restrictions)
          .search(racers, fronts, rears, tires))
        return result(UNAVAILABLE, "Legal machine parts exist, but required slots lack fully verified base stats for this patch.");
      return order.best() == null ? result(UNAVAILABLE, "Legal completions exist, but no fully evaluable "
          + (request.basis() == RecommendationBasis.PASSIVE ? "passive-stat" : "scenario-stat") + " candidate is available with these locks.")
          : result(ESTABLISHED, explanation(true));
    } catch (SearchLimit reached) {
      return result(order.best() == null ? LIMIT_WITHOUT_CANDIDATE : BEST_FOUND, order.best() == null
          ? "Search limit reached before a fully evaluated candidate was found. This does not establish infeasibility."
          : explanation(false));
    }
  }

  private String explanation(boolean complete) {
    var best = order.best();
    String prefix = complete ? "Best supported result established within the reviewed catalog and locks. " : "Best found before the search limit; optimality is not established. ";
    if (balanced != null) {
      if (complete && best.selection().equals(request.current()))
        return prefix + "No improvement: the frozen reference is already best under the complete Balanced comparator. Every floor and lock is respected.";
      var changes = new ArrayList<String>();
      for (var stat : StatPriority.values()) {
        var delta = stat.value(best.stats()).subtract(stat.value(currentStats));
        if (delta.signum() != 0) changes.add(stat + " " + (delta.signum() > 0 ? "+" : "") + delta.stripTrailingZeros().toPlainString() + " points");
      }
      return prefix + "Compared with the frozen reference: " + (changes.isEmpty() ? "all stats equal" : String.join(", ", changes))
          + ". Every loss floor and lock is respected. Rank weights determine the primary trade-off."
          + (complete && order.secondaryTieBreakDecided() ? " The secondary total decided between candidates with identical active values." : "");
    }
    if (complete && best.selection().equals(request.current())) return prefix + "Your current setup is already best under this strict priority order.";
    if (currentStats == null) return prefix + "Current " + (request.basis() == RecommendationBasis.PASSIVE ? "passive" : "scenario")
        + " stats are unavailable, so no numerical improvement over the current draft is claimed.";
    for (var priority : request.priorities()) {
      if (priority.value(best.stats()).compareTo(priority.value(currentStats)) != 0)
        return prefix + "Compared with the current setup, the first differing priority is " + priority
            + ". Earlier priorities are equal; lower stats cannot compensate for a loss in an earlier priority.";
    }
    return prefix + "All five values equal the current setup; selection changes follow the active machine constraint and deterministic tie-breaks.";
  }

  private RecommendationResult result(RecommendationResult.Outcome outcome, String reason) {
    var best = order.best();
    var scenario = request.basis() == RecommendationBasis.CURRENT_SCENARIO
        ? new RecommendationResult.ScenarioDetails(request.scenario(), catalog.rules().scenarioRuleset(), currentScenario,
            best == null ? null : candidates.evaluate(best.selection(), request.machineType()).scenario()) : null;
    return new RecommendationResult(outcome, best == null ? null : best.selection(), currentStats,
        best == null ? null : best.stats(), outcome == ESTABLISHED && best != null && best.selection().equals(request.current()),
        reason, List.copyOf(restrictions), catalog.rules().passiveRuleset(), scenario == null ? PassiveStatsCalculator.ARITHMETIC_NOTE
            : "Modeled stat points for one supplied scenario, not race simulation. Assumptions are not verified evidence; unknown effects are not zero.",
        budget.work(), budget.elapsedMillis(),
        balanced == null ? null : new RecommendationResult.BalancedDetails(balanced.minimum(), outcome == ESTABLISHED && order.secondaryTieBreakDecided()), scenario);
  }

  private String objectiveName() {
    return request.basis() == RecommendationBasis.PASSIVE ? "passive-adjusted" : "scenario-adjusted";
  }
}
