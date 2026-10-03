# Balanced auto-builder

Balanced considers your priorities in order. For each stat, it keeps builds within
the sacrifice you allow, then evaluates the next priority among the remaining builds.
An eliminated build never returns. Your current setup is only a comparison and a
convenience preference; you can start empty or lock just a racer or part.

Strict remains pure lexicographic optimization. Balanced with all five stats active
at 0% chooses the same numerical winner and uses the same convenience ordering.

## Contract and legal population

The request requires the supported patch (1.4.1), an explicit machine type, current
IDs (possibly all null), locks, and an order for the five stats. Each stat is either
active with a sacrifice in `[0,100)`, or explicitly ignored. For API compatibility,
100% normalizes to Ignore and the old `secondary` field aliases `ignored`. The old
active-only order plus an ignored tail is accepted; new clients send all five in order.

Candidates obey the same constraints as Strict: ownership, racer and individual
part locks, gadget locks, Gadget Plate fit, source-machine type and complete required
parts. BOOST requires Front and Rear with no Tire; other types require all three.
Only complete supported vanilla totals compete. Missing required data is never zero,
including missing values of ignored stats. There is no requirement that current stats
be available or nonnegative.

The objective is base racer + required parts + reviewed always-active passive gadget
adjustments. Phase 2's closed 23-effect signed additive policy is unchanged. Scenario
context, laps, held rings, WATER/FLIGHT, triggered effects and utility values do not
contribute. See [passive contract](auto-builder.md#passive-only-objective).

`KEEP_CURRENT` preserves the exact gadget identity list, including empty, and reevaluates
its effects for each candidate's types. `OPTIMIZE_UNLOCKED` searches supported combinations
around gadget locks and ownership. The shared Phase 2 traversal omits a newly added
all-five-zero gadget: removing it preserves stats, improves convenience and frees space.
Current/locked zero-effect gadgets remain eligible. Stage counts describe this searched
population, excluding those redundant new zero-effect combinations.

## Exact sequential thresholds

Technically this is **sequential constrained lexicographic optimization using
per-priority sacrifice thresholds over the surviving candidate set**.

For each active priority, let `b` be its exact maximum among current survivors and
`L` its configured sacrifice percentage:

```text
floor = b - abs(b) * L / 100
keep candidate iff candidate[stat] >= floor
```

All arithmetic uses exact `BigDecimal`, without rounding, epsilon or floating-point
comparison. For `b >= 0` this equals `b * (1 - L/100)`. At zero the floor is zero.
For `b = -10` and `L = 20`, the floor is -12: -10, -11 and -12 qualify; -12.01 fails.
Raw stats are not clamped. This signed sacrifice definition is optimizer policy,
not a Sonic game mechanic. At 0% only exact current-stage maxima survive.

Ignored stats create no threshold and have no role in filtering, final comparisons
or ties. Legacy 100% is Ignore, not a floor of zero. All Ignore uses convenience only.

After every stage, compare surviving exact vectors lexicographically in original
active priority order. No sum or weighted score is involved. Exact active-vector ties use:

1. Fewer changed component IDs plus gadget additions/removals.
2. Fewer newly added gadgets.
3. Lower total gadget slot cost.
4. Smaller stable ID key (racer/front/rear/tire, then sorted gadget IDs).

### Worked acceptance example

Order: Boost 5%, Speed 10%, Acceleration 25%, Handling 50%, Power Ignore.

| Build | Boost | Speed | Acceleration | Handling | Power |
| --- | ---: | ---: | ---: | ---: | ---: |
| A | 100 | 80 | 60 | 50 | 0 |
| B | 98 | 100 | 70 | 40 | 9999 |
| C | 95 | 92 | 100 | 20 | 0 |
| D | 95 | 91 | 80 | 60 | -9999 |
| E | 94 | 1000 | 1000 | 1000 | 1000 |

| Stage | Best among survivors | Floor | Before → after |
| --- | ---: | ---: | --- |
| Boost | 100 | 95 | 5 → 4 |
| Speed | 100 | 90 | 4 → 3 |
| Acceleration | 100 | 75 | 3 → 2 |
| Handling | 60 | 30 | 2 → 1 |

D wins. E cannot set later maxima after elimination. Power has no influence.
Focused tests also check exact 95 versus 94.99, final active-order comparison,
negative/zero maxima, all Ignore, ownership and incomplete locked setups.

## Production proof and bounded search

`BalancedRecommendationSearch` groups complete components by racer type. For each
active priority it streams the legal gadget/component population under previously
proven floors, finding a new maximum. A final pass applies all floors and finds the
active lexicographic winner. A stage's after-count is the following pass's before-count.
Memory contains component catalogs and at most five stages, not every candidate.

Each component subtree has coordinate-wise suffix minima, maxima and an exact
Cartesian count. Fixed types and gadget IDs give a constant passive adjustment:

- If any upper bound fails a previously proven floor, no completion can survive.
- If every lower bound meets its floor, every completion survives. Add its Cartesian
  count and select independent slot maxima for this pass. For the final pass use the
  active lexicographic order. A sum attains its maximum only when each independent
  contribution does; within numeric ties, keeping current IDs and then smaller IDs
  preserves the additive change count and stable key.
- Otherwise recurse into that subtree. Bounds are never clamped and no aggregate
  score is used. Ignored values never participate in these comparisons.

The incumbent must satisfy all completed floors before a new pass. If it does not,
replace it with the preceding maximum's witness, which always survives its own floor.
A truncated pass can therefore retain a legal candidate without pretending it is
the final winner. The current build may seed this fallback only if legal, supported
and owned; it never defines a floor.

Budgets remain 100,000 work steps, two seconds and two simultaneous calculations.
Work charges component preparation, per-pass maxima, shared gadget traversal and
each subtree visit. No limit was raised. Established results are deterministic
regardless of catalog/map order. Real elapsed-time truncation may differ by machine;
work-step cutoffs are reproducible.

| Outcome | Meaning |
| --- | --- |
| ESTABLISHED | All relevant passes completed; thresholds and winner are proven. |
| BEST_FOUND | A fully evaluable legal candidate was found; proof was interrupted. |
| LIMIT_WITHOUT_CANDIDATE | A limit interrupted search before a usable candidate. |
| NO_LEGAL_COMPLETION | Structural candidate pools prove no legal completion. |
| UNAVAILABLE | No complete supported candidate can be evaluated honestly. |
| NO_FEASIBLE_CANDIDATE | Reserved API outcome for a proven empty feasible set; the current sequential model cannot empty a nonempty supported population because each stage retains a maximum witness. |

For every non-established outcome, `balanced.proven` is false and `stages` is empty.
In particular, BEST_FOUND withholds all threshold claims, even if some early stages
completed. It is never relabeled as infeasible or presented as best possible.

## API and frontend

```json
{
  "gameVersionId": "<1.4.1 UUID>", "machineType": "BOOST",
  "mode": "BALANCED", "gadgetScope": "KEEP_CURRENT",
  "priorities": ["BOOST", "SPEED", "ACCELERATION", "HANDLING", "POWER"],
  "balanced": {
    "maximumLossPercent": {"BOOST": 5, "SPEED": 10, "ACCELERATION": 25, "HANDLING": 50},
    "ignored": ["POWER"]
  },
  "current": {"racerId": null, "frontPartId": null, "rearPartId": null, "tirePartId": null, "gadgetIds": []},
  "locked": {"racerId": null, "frontPartId": null, "rearPartId": null, "tirePartId": null, "gadgetIds": []}
}
```

Established response details have this compact shape:

```json
{"balanced":{"proven":true,"stages":[
  {"stat":"BOOST","lossPercent":5,"best":100,"threshold":95,"candidatesBefore":312,"candidatesAfter":47}
]}}
```

The dialog retains all five reorderable rows, with a sacrifice input and Ignore
checkbox. Ignoring a stat preserves its position and remembered percentage. All
Ignore is allowed. There is no reference-stat fetch or completeness gate. The server
still validates locks, ownership, scope and supported data. Unknown kept effects
remain unavailable rather than being silently removed.

Current and Recommended stats are compared only when current totals are complete.
Otherwise show Recommended alone. The result details display proven stages or explain
that thresholds are withheld. Apply still only updates the unsaved draft. Configuration,
account, patch, locks and collection changes invalidate stale proposals using the
unchanged Phase 1 collection revision checks.

## Independent oracle and verification

`RecommendationExhaustiveOracle` enumerates complete Cartesian products and gadget
bit masks, applies legal scope/ownership/lock/plate checks, calls the trusted passive
calculator, materializes every supported candidate, and directly filters survivor
lists. Its formula and convenience comparator are independent; it does not use
production traversal, pruning, stage reduction or candidate ordering.

`BalancedSolverTest` compares 240 seeded catalogs under mixed sacrifices and again
at all-zero sacrifice (480 oracle comparisons). Every zero-loss case also matches
Strict's exact stats and selection. Each mixed case is repeated with reversed
catalog ordering. Fixtures cover all five machine types, BOOST/no tire, 2–4 racers,
up to four alternatives per part slot, mixed racer types, signed decimal contributions,
both gadget scopes, locks, ownership exclusions, and overlapping reviewed passives.
Separate tests check each work cutoff, timed interruption and stage counts.

The integration benchmark exercises the unchanged production budgets with imported
catalog data. Unresolved Dragon Brave, Jaws Rocket, Sakura Board, TYPE-W Windy,
Triple Fan and Substitute Item facts are not changed. Ambiguous game values do not
define the synthetic correctness tests. See [validation](validation.md) for executed
checks and performance measurements.
