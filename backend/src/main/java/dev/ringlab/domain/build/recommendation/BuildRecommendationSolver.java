package dev.ringlab.domain.build.recommendation;

import dev.ringlab.domain.build.GadgetPlate;
import dev.ringlab.domain.gamedata.*;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.LongSupplier;
import static dev.ringlab.domain.build.recommendation.RecommendationResult.Outcome.*;

/** Exact search within reviewed passive rules; see docs/auto-builder.md for the separability proof. */
public final class BuildRecommendationSolver {
  public record Budget(long maxWork, Duration time) {
    public Budget {
      if (maxWork < 1 || time == null || time.isNegative() || time.isZero())
        throw new IllegalArgumentException("Search budget must be positive");
    }
  }
  private record Candidate(BuildSelection selection, BaseStats stats) {}
  private static final Comparator<UUID> IDS = Comparator.comparing(UUID::toString);
  private static final BaseStatsBreakdown ZERO = new BaseStatsBreakdown(
      PassiveGadgetRules.ZERO, PassiveGadgetRules.ZERO, PassiveGadgetRules.ZERO);

  private final RecommendationCatalog catalog;
  private final RecommendationRequest request;
  private final Budget budget;
  private final LongSupplier clock;
  private final long started;
  private final Set<String> restrictions = new LinkedHashSet<>();
  private long work;
  private Candidate best;
  private BaseStats currentStats;
  private BalancedObjective balanced;
  private boolean secondaryTieBreakDecided;
  private record Component(UUID id, BaseStats stats) {}
  private record Components(List<List<Component>> slots, List<BaseStats> remainingMaximum) {}

  public BuildRecommendationSolver(RecommendationCatalog catalog, RecommendationRequest request, Budget budget) {
    this(catalog, request, budget, System::nanoTime);
  }

  BuildRecommendationSolver(RecommendationCatalog catalog, RecommendationRequest request, Budget budget, LongSupplier clock) {
    this.catalog = Objects.requireNonNull(catalog);
    this.request = Objects.requireNonNull(request);
    this.budget = Objects.requireNonNull(budget);
    this.clock = clock;
    this.started = clock.getAsLong();
  }

  public RecommendationResult solve() {
    validate();
    if (!PassiveGadgetRules.VERSION.equals(catalog.version().version()))
      return result(UNAVAILABLE, "Recommendation requires the reviewed Ver. " + PassiveGadgetRules.VERSION
          + " ruleset. The selected patch has not been changed.");
    restrictions.add("Only fully known base contributions and reviewed passive combinations compete; unreviewed effects and unresolved stacking are excluded.");
    restrictions.add("Race-time, conditional and utility effects are not the objective. Current gadget costs and the complete two-row plate are used.");
    try {
      step();
      var currentType = legalType(request.current());
      if (currentType != null) {
        var evaluation = evaluate(request.current(), currentType);
        if (evaluation.coverage() == PassiveStatsResult.Coverage.CALCULATED) {
          currentStats = evaluation.adjusted();
          if (currentType == request.machineType()) best = new Candidate(request.current(), currentStats);
        }
      }

      if (request.mode() == RecommendationMode.BALANCED) {
        // Even secondary values must be known. Do not substitute base-only or zero stats.
        if (currentStats == null || !StatPriority.complete(currentStats)
            || Arrays.stream(StatPriority.values()).anyMatch(s -> s.value(currentStats).signum() < 0)) {
          best = null;
          return result(UNAVAILABLE, "Balanced unavailable: the frozen reference must be a complete legal setup with fully supported, nonnegative passive-adjusted values for all five stats.");
        }
        balanced = new BalancedObjective(currentStats, request.priorities(), request.balanced());
      }

      // Check feasibility before data completeness: unknown contributions are not an empty legal catalog.
      var racers = catalog.racers().values().stream()
          .filter(r -> request.locked().racerId() == null || r.id().equals(request.locked().racerId()))
          .sorted(Comparator.comparing(Racer::id, IDS)).toList();
      var fronts = parts(MachinePartType.FRONT, request.locked().frontPartId());
      var rears = parts(MachinePartType.REAR, request.locked().rearPartId());
      var tires = request.machineType() == RacingType.BOOST ? List.<MachinePart>of()
          : parts(MachinePartType.TIRE, request.locked().tirePartId());
      if (racers.isEmpty() || fronts.isEmpty() || rears.isEmpty()
          || (request.machineType() != RacingType.BOOST && tires.isEmpty()))
        return result(NO_LEGAL_COMPLETION, "No legal catalog completion exists for the chosen machine type and locks.");
      if (balanced != null) return solveBalanced(racers, fronts, rears, tires);
      UUID front = bestComponent(fronts.stream().map(MachinePart::id).toList(), catalog.partStats(), request.current().frontPartId());
      UUID rear = bestComponent(rears.stream().map(MachinePart::id).toList(), catalog.partStats(), request.current().rearPartId());
      UUID tire = request.machineType() == RacingType.BOOST ? null
          : bestComponent(tires.stream().map(MachinePart::id).toList(), catalog.partStats(), request.current().tirePartId());
      if (front == null || rear == null || (request.machineType() != RacingType.BOOST && tire == null))
        return result(UNAVAILABLE, "Legal machine parts exist, but required slots lack fully verified base stats for this patch.");

      // Rules depend on racer TYPE, never racer ID. Null is its own group, evaluated honestly by the calculator.
      var types = new ArrayList<RacingType>(Arrays.asList(RacingType.values()));
      types.add(null);
      for (var type : types) {
        UUID racer = bestComponent(racers.stream().filter(r -> r.racingType() == type).map(Racer::id).toList(),
            catalog.racerStats(), request.current().racerId());
        if (racer == null) continue;
        var base = new BuildSelection(racer, front, rear, tire, request.locked().gadgetIds());
        var lockedEffects = passive(type, request.locked().gadgetIds());
        if (lockedEffects.coverage() != PassiveStatsResult.Coverage.CALCULATED) {
          lockedEffects.effects().stream().filter(e -> e.status() == PassiveStatsResult.Status.UNSUPPORTED
              || e.status() == PassiveStatsResult.Status.REQUIRES_SELECTION)
              .forEach(e -> restrictions.add("Excluded racer type " + (type == null ? "unknown" : type)
                  + " with locked " + e.gadgetName() + ": " + e.explanation()));
          continue;
        }
        search(base, type, optionalGadgets(type), 0, new ArrayList<>(request.locked().gadgetIds()), null);
      }
      return best == null ? result(UNAVAILABLE, "Legal completions exist, but no fully evaluable passive-stat candidate is available with these locks.")
          : result(ESTABLISHED, explanation(true));
    } catch (SearchLimit reached) {
      return result(best == null ? LIMIT_WITHOUT_CANDIDATE : BEST_FOUND, best == null
          ? "Search limit reached before a fully evaluated candidate was found. This does not establish infeasibility."
          : explanation(false));
    }
  }

  private List<UUID> optionalGadgets(RacingType type) {
    var optional = new ArrayList<UUID>();
    for (var gadget : catalog.gadgets().values().stream().sorted(Comparator.comparing(Gadget::id, IDS)).toList()) {
      step();
      if (request.locked().gadgetIds().contains(gadget.id()) || !GadgetPlate.canFit(Collections.singletonList(gadget.slotCost()))) continue;
      var alone = passive(type, List.of(gadget.id()));
      if (alone.coverage() != PassiveStatsResult.Coverage.CALCULATED) continue;
      // Include secondary gains too. Only an all-five-zero new gadget can be omitted.
      if (request.current().gadgetIds().contains(gadget.id())
          || StatPriority.compare(alone.adjustments(), PassiveGadgetRules.ZERO, List.of(StatPriority.values())) != 0)
        optional.add(gadget.id());
    }
    return optional;
  }

  private void search(BuildSelection base, RacingType racerType, List<UUID> optional, int start, List<UUID> selected, Components components) {
    step();
    if (!GadgetPlate.canFit(selected.stream().map(id -> catalog.gadgets().get(id).slotCost()).toList())) return;
    var effects = passive(racerType, selected);
    // For fixed types, adding gadgets cannot cure unknown effects or an unresolved applied-modifier stack.
    if (effects.coverage() != PassiveStatsResult.Coverage.CALCULATED) return;
    if (components == null) {
      var selection = new BuildSelection(base.racerId(), base.frontPartId(), base.rearPartId(), base.tirePartId(), ordered(selected));
      consider(new Candidate(selection, BaseStats.sum(List.of(baseStats(selection).total(), effects.adjustments()))));
    } else {
      searchComponents(components, 0, effects.adjustments(), new UUID[4], ordered(selected));
    }
    for (int index = start; index < optional.size(); index++) {
      selected.add(optional.get(index));
      search(base, racerType, optional, index + 1, selected, components);
      selected.removeLast();
    }
  }

  private RecommendationResult solveBalanced(List<Racer> racers, List<MachinePart> fronts,
      List<MachinePart> rears, List<MachinePart> tires) {
    var frontChoices = components(fronts.stream().map(MachinePart::id).toList(), catalog.partStats(), request.current().frontPartId());
    var rearChoices = components(rears.stream().map(MachinePart::id).toList(), catalog.partStats(), request.current().rearPartId());
    var tireChoices = request.machineType() == RacingType.BOOST ? List.of(new Component(null, BaseStats.ZERO))
        : components(tires.stream().map(MachinePart::id).toList(), catalog.partStats(), request.current().tirePartId());
    var types = new ArrayList<RacingType>(Arrays.asList(RacingType.values()));
    types.add(null);
    for (var type : types) {
      var racerChoices = components(racers.stream().filter(r -> r.racingType() == type).map(Racer::id).toList(),
          catalog.racerStats(), request.current().racerId());
      var slots = List.of(racerChoices, frontChoices, rearChoices, tireChoices);
      if (slots.stream().anyMatch(List::isEmpty)) continue;
      var maximum = new ArrayList<BaseStats>(Collections.nCopies(5, BaseStats.ZERO));
      for (int index = 3; index >= 0; index--)
        maximum.set(index, BaseStats.sum(List.of(maximum.get(index + 1), maximum(slots.get(index)))));
      search(null, type, optionalGadgets(type), 0, new ArrayList<>(request.locked().gadgetIds()), new Components(slots, maximum));
    }
    return best == null ? result(NO_FEASIBLE_CANDIDATE, "Complete supported search found no candidate satisfying every loss floor, chosen machine type and lock. Limits have not been relaxed.")
        : result(ESTABLISHED, explanation(true));
  }

  private List<Component> components(List<UUID> ids, Map<UUID, BaseStats> values, UUID current) {
    var choices = new ArrayList<Component>();
    for (var id : ids) {
      step();
      if (StatPriority.complete(values.get(id))) choices.add(new Component(id, values.get(id)));
      else restrictions.add("Some racers or parts have missing base values for the selected patch and are excluded.");
    }
    // Search promising choices first, without discarding a trade-off in any component.
    choices.sort((left, right) -> {
      int compared = balanced.compare(right.stats(), left.stats());
      if (compared != 0) return compared;
      int kept = Boolean.compare(Objects.equals(right.id(), current), Objects.equals(left.id(), current));
      return kept != 0 ? kept : IDS.compare(left.id(), right.id());
    });
    return choices;
  }

  private static BaseStats maximum(List<Component> choices) {
    var values = new EnumMap<StatPriority, BigDecimal>(StatPriority.class);
    for (var stat : StatPriority.values())
      values.put(stat, choices.stream().map(c -> stat.value(c.stats())).max(BigDecimal::compareTo).orElseThrow());
    return new BaseStats(values.get(StatPriority.SPEED), values.get(StatPriority.ACCELERATION),
        values.get(StatPriority.HANDLING), values.get(StatPriority.POWER), values.get(StatPriority.BOOST));
  }

  private void searchComponents(Components components, int slot, BaseStats partial, UUID[] ids, List<UUID> gadgets) {
    step();
    var upper = BaseStats.sum(List.of(partial, components.remainingMaximum().get(slot)));
    // Each stat's independent maximum is an optimistic complete-loadout bound. Positive
    // coefficients make Q monotone. Strict inequality preserves ALL later tie-breaks.
    if (!balanced.feasible(upper) || (best != null && balanced.compareActive(upper, best.stats()) < 0)) return;
    if (slot == 4) {
      consider(new Candidate(new BuildSelection(ids[0], ids[1], ids[2], ids[3], gadgets), partial));
      return;
    }
    for (var choice : components.slots().get(slot)) {
      ids[slot] = choice.id();
      searchComponents(components, slot + 1, BaseStats.sum(List.of(partial, choice.stats())), ids, gadgets);
    }
  }

  private void consider(Candidate candidate) {
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

  private List<UUID> ordered(List<UUID> selected) {
    var result = new ArrayList<UUID>();
    request.current().gadgetIds().stream().filter(selected::contains).forEach(result::add);
    selected.stream().filter(id -> !result.contains(id)).sorted(IDS).forEach(result::add);
    return result;
  }

  private UUID bestComponent(List<UUID> ids, Map<UUID, BaseStats> values, UUID current) {
    UUID winner = null;
    for (var id : ids) {
      step();
      if (!StatPriority.complete(values.get(id))) {
        restrictions.add("Some racers or parts have missing base values for the selected patch and are excluded.");
        continue;
      }
      int compared = winner == null ? 1 : StatPriority.compare(values.get(id), values.get(winner), request.priorities());
      if (compared > 0 || (compared == 0 && (id.equals(current)
          || (!winner.equals(current) && IDS.compare(id, winner) < 0)))) winner = id;
    }
    return winner;
  }

  private List<MachinePart> parts(MachinePartType slot, UUID locked) {
    return catalog.parts().values().stream().filter(part -> part.type() == slot
        && (locked == null || part.id().equals(locked)) && catalog.machines().containsKey(part.sourceMachineId())
        && catalog.machines().get(part.sourceMachineId()).racingType() == request.machineType())
        .sorted(Comparator.comparing(MachinePart::id, IDS)).toList();
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

  private PassiveStatsResult passive(RacingType racerType, List<UUID> ids) {
    return PassiveStatsCalculator.calculate(ZERO, catalog.version().version(), racerType,
        request.machineType(), ids.stream().map(catalog.gadgets()::get).toList(), true);
  }

  private PassiveStatsResult evaluate(BuildSelection selection, RacingType type) {
    return PassiveStatsCalculator.calculate(baseStats(selection), catalog.version().version(),
        catalog.racers().get(selection.racerId()).racingType(), type,
        selection.gadgetIds().stream().map(catalog.gadgets()::get).toList(), true);
  }

  private BaseStatsBreakdown baseStats(BuildSelection selection) {
    return BaseStatsBreakdown.calculate(selection.racerId(), selection.frontPartId(), selection.rearPartId(),
        selection.tirePartId(), catalog.racerStats(), catalog.partStats());
  }

  private RacingType legalType(BuildSelection selection) {
    if (selection.racerId() == null || selection.frontPartId() == null || selection.rearPartId() == null
        || !GadgetPlate.canFit(selection.gadgetIds().stream().map(id -> catalog.gadgets().get(id).slotCost()).toList())) return null;
    try {
      var type = MachineCompatibility.requireCompatible(id -> Optional.ofNullable(catalog.machines().get(id)),
          catalog.parts().get(selection.frontPartId()), catalog.parts().get(selection.rearPartId()),
          selection.tirePartId() == null ? null : catalog.parts().get(selection.tirePartId()));
      return type != RacingType.BOOST && selection.tirePartId() == null ? null : type;
    } catch (IllegalArgumentException invalid) { return null; }
  }

  private void validate() {
    if (!catalog.version().id().equals(request.gameVersionId())) throw new IllegalArgumentException("Patch does not match the catalog snapshot");
    validateSelection(request.current());
    validateSelection(request.locked());
    var locks = request.locked();
    var current = request.current();
    requireKept("Racer", locks.racerId(), current.racerId());
    requireKept("Front", locks.frontPartId(), current.frontPartId());
    requireKept("Rear", locks.rearPartId(), current.rearPartId());
    requireKept("Tire", locks.tirePartId(), current.tirePartId());
    if (!current.gadgetIds().containsAll(locks.gadgetIds())) throw new IllegalArgumentException("Locked gadgets must be currently selected");
    for (var id : Arrays.asList(locks.frontPartId(), locks.rearPartId(), locks.tirePartId())) {
      if (id == null) continue;
      var part = catalog.parts().get(id);
      if (part.type() == MachinePartType.TIRE && request.machineType() == RacingType.BOOST)
        throw new IllegalArgumentException("Unlock the Tire before choosing Boost: Boost has no tire part");
      var source = catalog.machines().get(part.sourceMachineId());
      if (source == null || source.racingType() != request.machineType())
        throw new IllegalArgumentException("Locked " + part.type() + " is incompatible with " + request.machineType() + "; unlock it or choose its source machine type");
    }
    if (!GadgetPlate.canFit(locks.gadgetIds().stream().map(id -> catalog.gadgets().get(id).slotCost()).toList()))
      throw new IllegalArgumentException("Locked gadgets have unknown/invalid costs or do not fit the two-row Gadget Plate");
  }

  private void validateSelection(BuildSelection selection) {
    if (selection.racerId() != null && !catalog.racers().containsKey(selection.racerId())) throw new IllegalArgumentException("Unknown racer ID");
    var ids = Arrays.asList(selection.frontPartId(), selection.rearPartId(), selection.tirePartId());
    var slots = List.of(MachinePartType.FRONT, MachinePartType.REAR, MachinePartType.TIRE);
    for (int i = 0; i < ids.size(); i++) {
      if (ids.get(i) == null) continue;
      var part = catalog.parts().get(ids.get(i));
      if (part == null) throw new IllegalArgumentException("Unknown " + slots.get(i) + " part ID");
      if (part.type() != slots.get(i)) throw new IllegalArgumentException("Part ID does not match the " + slots.get(i) + " slot");
    }
    if (selection.gadgetIds().size() > 6 || new HashSet<>(selection.gadgetIds()).size() != selection.gadgetIds().size())
      throw new IllegalArgumentException("Select distinct gadget IDs (at most six)");
    if (!catalog.gadgets().keySet().containsAll(selection.gadgetIds())) throw new IllegalArgumentException("Unknown gadget ID");
  }

  private static void requireKept(String slot, UUID locked, UUID current) {
    if (locked != null && !locked.equals(current)) throw new IllegalArgumentException("Locked " + slot + " must be currently selected");
  }

  private String explanation(boolean complete) {
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
          + (complete && secondaryTieBreakDecided ? " The secondary total decided between candidates with identical active values." : "");
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
    return new RecommendationResult(outcome, best == null ? null : best.selection(), currentStats,
        best == null ? null : best.stats(), outcome == ESTABLISHED && best != null && best.selection().equals(request.current()),
        reason, List.copyOf(restrictions), PassiveGadgetRules.RULESET, PassiveStatsCalculator.ARITHMETIC_NOTE,
        work, Math.max(0, (clock.getAsLong() - started) / 1_000_000),
        balanced == null ? null : new RecommendationResult.BalancedDetails(balanced.minimum(), outcome == ESTABLISHED && secondaryTieBreakDecided));
  }

  private void step() {
    if (work >= budget.maxWork() || clock.getAsLong() - started >= budget.time().toNanos() || Thread.currentThread().isInterrupted())
      throw new SearchLimit();
    work++;
  }

  private static final class SearchLimit extends RuntimeException {
    SearchLimit() { super(null, null, false, false); }
  }
}
