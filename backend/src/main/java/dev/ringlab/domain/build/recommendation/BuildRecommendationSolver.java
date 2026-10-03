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
  private BalancedRecommendationSearch balanced;

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
    restrictions.add("Only fully known base contributions and the closed reviewed additive passive model compete; unreviewed effects are excluded.");
    restrictions.add(request.gadgetScope() == GadgetRecommendationScope.KEEP_CURRENT
        ? "Current gadgets were kept. Their passive effects are recalculated for each proposed type."
        : "Unlocked gadgets were optimized for reviewed passive stat effects. They may be added, removed or replaced; utility, scenario effects and race strategy are not valued.");
    restrictions.add("Race-time, conditional and utility effects are not the objective. Current gadget costs and the complete two-row plate are used.");
    try {
      budget.step();
      var currentType = candidates.legalType(request.current());
      if (currentType != null) {
        var evaluation = candidates.evaluate(request.current(), currentType);
        if (evaluation.eligible()) {
          currentStats = evaluation.total();
          if (currentType == request.machineType() && candidates.available(request.current())) order.seed(request.current(), currentStats);
        }
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
      if (request.mode() == RecommendationMode.BALANCED) {
        balanced = new BalancedRecommendationSearch(catalog,request,gadgets,order,budget,restrictions);
        balanced.search(racers, fronts, rears, tires);
        return order.best() == null ? result(UNAVAILABLE, "Legal completions exist, but no complete supported passive candidate can be evaluated. Check the selected patch, parts and kept/locked gadgets.")
            : result(ESTABLISHED, explanation(true));
      }
      if (!new StrictRecommendationSearch(catalog, request, candidates, gadgets, order, budget, restrictions)
          .search(racers, fronts, rears, tires))
        return result(UNAVAILABLE, "Legal machine parts exist, but required slots lack fully verified base stats for this patch.");
      return order.best() == null ? result(UNAVAILABLE, "Legal completions exist, but no fully evaluable passive-stat candidate is available with these locks.")
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
    if (request.mode() == RecommendationMode.BALANCED) {
      return prefix + (complete
          ? "Priorities were filtered in order using the permitted sacrifice at each stage. Final survivors use active-priority order, then convenience ties. Ignored stats have no influence."
          : "This is a legal candidate observed before completion. Balanced thresholds and the final survivor winner are not proven; stage claims are withheld.");
    }
    if (complete && best.selection().equals(request.current())) return prefix + "Your current setup is already best under this strict priority order.";
    if (currentStats == null) return prefix + "Current passive stats are unavailable, so no numerical improvement over the current draft is claimed.";
    for (var priority : request.priorities()) {
      if (priority.value(best.stats()).compareTo(priority.value(currentStats)) != 0)
        return prefix + "Compared with the current setup, the first differing priority is " + priority
            + ". Earlier priorities are equal; lower stats cannot compensate for a loss in an earlier priority.";
    }
    return prefix + "All five values equal the current setup; selection changes follow the active machine constraint and deterministic tie-breaks.";
  }

  private RecommendationResult result(RecommendationResult.Outcome outcome, String reason) {
    var best = order.best();
    return new RecommendationResult(outcome, best == null ? null : best.selection(), currentStats,
        best == null ? null : best.stats(), outcome == ESTABLISHED && best != null && best.selection().equals(request.current()),
        reason, List.copyOf(restrictions), catalog.rules().passiveRuleset(), PassiveStatsCalculator.ARITHMETIC_NOTE,
        budget.work(), budget.elapsedMillis(),
        request.mode() != RecommendationMode.BALANCED ? null : new RecommendationResult.BalancedDetails(
            outcome == ESTABLISHED, outcome == ESTABLISHED && balanced != null ? balanced.stages() : List.of()));
  }

}
