# Balanced auto-builder

Balanced extends the completed desktop recommendation flow. Strict remains the
default, including requests that omit `mode`. Strict gives an earlier priority
absolute precedence. Balanced lets improvements compensate for **permitted**
losses using a fixed weighted score, while enforcing every loss floor separately.
Neither mode changes the draft until Apply; neither saves or publishes it.

## Reference, inputs and arithmetic

Opening the popup freezes the actual racer, parts, gadget order and patch. The
existing passive-stats endpoint displays that reference; the recommendation
server independently resolves and calculates it again from those selections.
Client-supplied numerical stats are never trusted. Both reference and candidates
use the selected patch and the reviewed passive calculator. The reference must
be legal, complete, fully supported and nonnegative in **all five stats**, even
secondary stats. Otherwise Balanced is unavailable with an explanation; it never
falls back to Strict. Incomplete drafts may still use Strict.

Mode changes, reordering, recalculation and Back to priorities keep that reference.
Only applying and deliberately opening a new popup establishes a new reference.
Losses therefore never compound. A draft/lock change requires reopening before
calculating; the existing editor/session/identity guards also protect Apply.

Each stat occurs exactly once in active priorities or the secondary section.
There must be at least one active stat. Every active stat has a finite maximum
loss percentage in `[0,100]`, initially **0%**. Removing/restoring a stat preserves
its previous percentage and its place in the full order. Losses for secondary
stats are omitted from the request. Unknown names, duplicate/missing stats,
missing/extra loss entries and nonfinite percentages are rejected by the server.

For active stat `i`, with reference `b_i` and maximum loss `L_i`:

```
minimum_i = b_i * (1 - L_i / 100)
candidate_i >= minimum_i
```

The check uses unrounded `BigDecimal` values. Boost 80 with 5% permits 76, rejects
75.99 and does not require any loss. A 0% floor allows all improvements. A gain
elsewhere cannot override a failed floor. These are percentages of actual stat
values, never percentages of a UI bar or a discovered search range.

## Objective and complete comparison order

For `n` active stats in chosen order, weights are **n, n−1, …, 1**. This is the
explicit v1 policy; changing rank changes the weights. Loss allowances do not.

```
Q(x) = SUM active i: weight_i * (s_i(x) - b_i) / denominator_i
denominator_i = b_i when b_i > 0; otherwise 1 stat point
```

With a zero reference, the floor is zero and changes display in points, not fake
percentage gains. The one-point denominator prevents Infinity/NaN. Normalization
stays fixed across every candidate and recalculation in the session.

The comparison is:

1. Satisfy all locks, fixed machine type, composition, Gadget Plate, supported
   data requirements and every active loss floor.
2. Higher `Q`.
3. Equal `Q`: higher active values lexicographically in the user's priority order.
4. Identical active values: higher **sum of all secondary values**.
5. Fewer changed component IDs plus gadget additions/removals.
6. Fewer new gadgets, then lower total slot cost.
7. The existing canonical racer/front/rear/tire UUID key and sorted gadget IDs.

Secondary stats have no floor or primary weight. They are never deliberately
minimized and cannot defeat a better primary result. Power 75 beats 10 when all
active values are identical; secondary Handling/Power `(65,65)` beats `(20,100)`.
These cases are synthetic **tests**, not altered game data.

`BalancedObjective` multiplies score comparisons by the positive product of the
fixed denominators and omits the constant baseline term. Each coefficient is
`weight_i * product(other denominators)`. This is exactly equivalent to Q ordering,
requires no division, and keeps arbitrarily close decimal differences distinct.
No rounded display value, epsilon or tiny secondary weight participates.

An established result reports a secondary decision only after actually comparing
different secondary totals at the winning active vector. A later better active
vector resets that evidence. Interrupted searches do not claim an established
secondary decision. The valid reference is seeded as an incumbent whenever its
machine type matches the request (locks must already be present in that reference).
If it remains best under the complete comparator, the result says no improvement.

## Constraint-aware search and correctness

Strict retains its proven `bestComponent()` reduction. Balanced cannot use it:
an individually preferred part can violate a complete-loadout floor, while a
locally weaker complementary part can restore feasibility and produce the winner.
The crafted joint-component regression compares that case with an independent
exhaustive result.

Balanced groups racers by racing type and enumerates supported gadget subsets
using the existing calculator and complete two-row plate validation. For each
fixed racer/machine type and gadget set, it searches racer/front/rear/tire choices
depth-first, ordering promising components first. BOOST uses a null/zero absent
tire contribution and still rejects a locked tire. Racer type is independent.

For every remaining component slot it precomputes the maximum of **each stat**.
Adding these maxima to the partial vector and fixed gadget adjustment gives an
optimistic complete-loadout bound, even when no real combination achieves it.
If this bound fails a floor, every completion fails. If its Q/active-order value
is strictly worse than the incumbent, every completion loses. Positive fixed
coefficients justify that monotonic comparison, including negative component
contributions or passive penalties. Equal bounds are kept: secondary totals and
all final tie-breakers can still matter. No component is discarded merely for
having a worse individual score.

The full Cartesian product is never materialized. Memory consists of the detached
catalog, sorted component lists, five suffix vectors, current incumbent and short
component/gadget recursion stacks. Worst-case combinations can still be large;
every visited search node consumes the existing **100,000-work / two-second**
budget. Two concurrent permits, interruption checks and REST rate limits remain
unchanged. No timeout was raised. There are no per-candidate database/API reads.

The shared gadget exclusions retain the existing proof: unsupported fixed-type
modifier stacks cannot be repaired by adding another gadget. New gadgets whose
entire five-stat adjustment is zero cannot improve any numerical comparator and
lose addition/cost ties. Currently selected utility gadgets remain eligible.
Unsupported secondary values are excluded, not replaced with zero. Conditional
and non-stat benefits remain outside the objective even when those gadgets are
locked; locks themselves remain mandatory.

Outcomes distinguish `ESTABLISHED`, `BEST_FOUND`, `NO_LEGAL_COMPLETION`,
`NO_FEASIBLE_CANDIDATE` (complete supported search), `UNAVAILABLE` (including an
unsupported reference/ruleset), and `LIMIT_WITHOUT_CANDIDATE`. Reaching a limit
never proves infeasibility and never relaxes a floor.

## API and UI

The same authenticated `POST /api/build-recommendations` accepts:

```json
{
  "gameVersionId": "50000000-0000-0000-0000-000000000001",
  "machineType": "BOOST",
  "mode": "BALANCED",
  "priorities": ["BOOST", "SPEED", "ACCELERATION", "HANDLING"],
  "balanced": {
    "maximumLossPercent": { "BOOST": 5, "SPEED": 10, "ACCELERATION": 25, "HANDLING": 50 },
    "secondary": ["POWER"]
  },
  "current": {
    "racerId": "60000000-0000-4000-8000-000000000018",
    "frontPartId": "10000000-0000-4000-8000-000000000009",
    "rearPartId": "20000000-0000-4000-8000-000000000009",
    "tirePartId": null,
    "gadgetIds": ["70000000-0000-4000-8000-000000000060", "70000000-0000-4000-8000-000000000061"]
  },
  "locked": {
    "racerId": "60000000-0000-4000-8000-000000000018",
    "frontPartId": null, "rearPartId": null, "tirePartId": null, "gadgetIds": []
  }
}
```

The optional response `balanced` contains the server-computed `minimum` map and
`secondaryTieBreakDecided` evidence. Existing result fields are preserved.

The desktop popup shows the frozen selections, current values, maximum losses,
minimums and result changes. Secondary result rows say “Tie-break only — no
minimum”. Positive references show signed actual percentages; zero references
show points. Very small nonzero changes retain their sign. Priority arrow buttons
provide a keyboard alternative to dragging. At least one priority must remain.
Mode, rank, losses, secondary membership or machine changes invalidate/abort old
calculations and proposals. Late responses cannot replace newer configurations.
Below 1001px, existing cancellation/lock-reset/manual-editor behavior is preserved.

The editor waits for the patch catalog before enabling the popup. Its reference
keeps the selected patch ID; delayed catalog details can still resolve that same
ID. When Calculate is disabled, a reason beside the button identifies the missing
patch, machine type, supported reference, invalid loss or changed draft. Zero
losses are valid. Changing the editor draft requires closing and reopening the
popup to establish a fresh reference.

Strict and Balanced both show the same rounded starting-setup summary. Racer and
machine selections place their type badge beside the larger selection name. The
summary has an information icon explaining that the reference stays fixed, and
collapses after a result so it does not crowd the comparison.

Balanced configuration uses compact tables with shared headings for priorities,
loss limits, minimums and tie-break-only stats instead of repeating labels in
every row. Balanced results put the stat comparison first, followed by aligned Current and
Recommended selections with racer/machine type badges. Unchanged slots and ordered
gadget lists show “No change”. The starting setup and detailed explanation collapse
after calculation. The result body scrolls between a fixed heading and action area,
and each new result starts at the top.

Apply changes only allowed loadout selections and chosen machine type. It keeps
title, description, patch, map recommendations, remix provenance and locks.

## Professor walkthrough and frozen demo

Managed key: `optimizer-miku-balanced-v1`.
Title: **[Optimizer Demo] Miku — Boost trade-off baseline**.
Development template ID: **`20d86571-17e8-47c0-b544-107a15dfb8e8`** —
[open the template](http://localhost:5173/builds/20d86571-17e8-47c0-b544-107a15dfb8e8).
It was created in the verified existing **development** database and an idempotent
second apply retained the same ID. The normal development Java/Vite/PostgreSQL
listener process IDs remained unchanged. The template has zero votes and is
absent from the global top three.

Selections are frozen in `backend/scripts/optimizer-miku-balanced-v1.json`:
canonical Hatsune Miku, TYPE-J Iota Front/Rear, no tire, Boost Tuner 1 and 2,
Ver. 1.4.1. Bounded discovery evaluated 30 real references (ten stock BOOST pairs
with empty, Tuner 1, or Tuner 1+2 plates). No game values or scoring rules changed.

1. On desktop, search **optimizer**, open the template and choose **Remix**.
2. Keep the clone unsaved. Lock **Miku** and select machine type **BOOST**.
3. Open Recommend, choose **Balanced**, and reorder **Boost > Speed > Acceleration > Handling**.
4. Set maximum losses to **5%, 10%, 25%, 50%**, respectively. Move **Power** to
   “I don't care / Tie-break only”.
5. Calculate; compare the small Boost loss with the gains and all four minimums.
6. Use Back and recalculate to demonstrate that reference/floors stay fixed.
7. Apply to the unsaved clone. The template remains unchanged; saving is separate.

Observed supported winner: **Triple Fan Front/Rear**, same Miku and both Boost
Tuners. The reference itself has a valid reviewed gadget stack.

| Stat | Frozen reference | Maximum loss | Minimum | Recommended | Actual change |
| --- | ---: | ---: | ---: | ---: | ---: |
| Boost | 116 | 5% | 110.2 | 112 | −3.4483% |
| Speed | 29 | 10% | 26.1 | 31 | +6.8966% |
| Acceleration | 46 | 25% | 34.5 | 47 | +2.1739% |
| Handling | 57 | 50% | 28.5 | 61 | +7.0175% |
| Power | 24 | Secondary only | None | 24 | 0% |

The first bounded measurement established this result in **435 work steps / 4 ms**.
The 30 locked-Miku discovery searches all established optima in **387–1,105 steps /
2–52 ms**, on Java 21/PostgreSQL 17.11 in the isolated local test environment.
These are local observations, not latency guarantees; catalog I/O is outside the
solver timer. Limits are unchanged.

The later unlocked benchmark, with the same four priorities/allowances and no
item locks, completed on every machine type:

| Machine type | Outcome | Work | Solver time |
| --- | --- | ---: | ---: |
| SPEED | Established | 15,700 | 72 ms |
| ACCELERATION | Established | 19,914 | 94 ms |
| HANDLING | Established | 7,814 | 42 ms |
| POWER | Established | 74,515 | 273 ms |
| BOOST | Established | 7,268 | 63 ms |

The frozen example's final normal-API isolated check established it in 435 steps /
49 ms. A read-only request against the actual saved development fixture reproduced
the table above in **435 steps / 15 ms**, with `secondaryTieBreakDecided: false`.
Power did not decide this recommendation.

The seed tool's `-OptimizerDemoOnly` path permits one template and one reserved
author, with zero votes/comments. It rejects broad refresh/ranking flags, reuses
the existing loopback/process/Compose/database-identity checks, saves the community
backup and uses the existing manifest/fingerprint conflict checks. It never
overwrites manual edits or recreates a removed managed template. The reserved
`[Optimizer Demo]` title prefix is excluded from the global community top three
by the existing eligibility convention; ordinary browsing still finds it.

The original community backup is
`backend/.ringlab-demo-state.json.before-20260927T213023321.json`; the idempotent
rerun saved `backend/.ringlab-demo-state.json.before-20260927T213126436.json`.
The existing ignored manifest records the template identity/fingerprint. Comparing
those snapshots confirmed every pre-existing row in the captured tables unchanged:
245 existing builds, 307 existing build-gadget links, 1,744 votes and 562 comments.
Only one build and its two gadget links were added, together with its reserved
author. No votes/comments, broad refresh, schema change or database reset occurred.

```powershell
# From the repository root; preview/preflight first, then only this authorized fixture.
./backend/scripts/SeedDemoData.ps1 -OptimizerDemoOnly
./backend/scripts/SeedDemoData.ps1 -OptimizerDemoOnly -Apply
```

## Verification

Pure tests cover exact floors, independent limits, partial use of allowances,
weight updates, zero/unsupported references, exact Q ties, secondary sums, complete
ties, incumbents and interrupted/no-feasible outcomes. An independent exhaustive
oracle enumerates all parts/racers/gadget subsets in 24 seeded small catalogs,
using separate fraction arithmetic and the full comparator. Existing Strict
regressions remain intact. API tests reject tampered configuration and calculate
reference values on the server without writing builds or engagement. The frozen
fixture is separately created through normal build rules in an isolated database.

Frontend tests cover frozen recalculations, restore, validation, zero/percentage
display, stale configurations/responses, cancellation, Apply preservation and
desktop-to-mobile behavior. Seed tests cover the one-template scope, fixed identity,
canonical racer, forbidden broad flags and offline safety.

Implementation verification snapshot: working tree on **`1ac4cb620f3fb688557eb6b9baa9dbe8f59fe7b2`**,
including the uncommitted Balanced changes. Concurrent editor/badge/CSS changes in
the shared checkout were preserved. A SHA-256 manifest of the 24 changed source,
test and fixture-tool files is in `backend/target/balanced-verified-files.sha256`
(manifest digest `37DABDF9A9C6E47E2BDBD2E4A5749AE1500E522E90E8DF1C17416842428E5E7B`).

Verification on 2026-09-28:

- Relevant backend run: 43 checks passed; the remaining new fixture check initially
  assumed HTTP 201 instead of RingLab's existing HTTP 200 creation contract. After
  correcting only that assertion, the isolated fixture check passed and Maven
  packaging succeeded. Together all **44 relevant checks** passed. No backend
  implementation changed after that combined run.
- Frontend: final full coverage passed **303 tests in 34 files**; **93.21%**
  statements/lines, **91.47%** branches and **86.86%** functions. Thresholds are
  unchanged. The initial TypeScript build caught an unsupported test-query option;
  removing it preserved the assertion's exact-name matching. The final TypeScript
  and Vite build passed, including the concurrent stylesheet edits.
- `SeedDemoData.Tests.ps1` passed; offline real-catalog preview showed exactly one
  author/template and no engagement. Development apply plus repeat proved creation
  and idempotence with the backups and preservation checks above.
- Existing compiler warnings concern deprecated/unchecked code in unrelated test
  classes. Broader unrelated backend suites and `mvn verify` were intentionally not
  run. No backend coverage claim is inferred from an accumulated JaCoCo file.
- Native visual browser verification was **not performed**: automatic approval
  review blocked starting the isolated packaged-preview processes with “blocked
  by policy”. Automated modal keyboard, async, Apply and desktop/mobile tests passed.

After those checks, the Balanced priority cards were changed to compact, aligned
rows in `BuildRecommendationDialog.tsx` and `balancedRecommendation.css`. All 48
focused dialog, recommendation and editor tests passed, and the frontend build
passed again. The full coverage results and source manifest above describe the
earlier implementation snapshot. The existing browser preview remained below the
desktop breakpoint despite a viewport override, so visual confirmation of this
layout correction was initially left as a manual check.

A subsequent calculation-availability fix passed all **44 focused tests** in
`BuildRecommendationDialog.test.tsx` and `pages/BuildEditor.test.tsx`, plus the
frontend build. Tests cover delayed patch metadata, zero-loss calculation and
the blocker/reopen flow. Browser verification against the existing development
stack then succeeded: the Miku draft showed all five reference values, calculated
with five 0% losses, and returned the retained setup. The compact layout and the
visible disabled-button reason were inspected at desktop width. No result was
applied or saved. Full suites were not rerun for this frontend fix.

The Balanced result presentation follow-up passed focused dialog checks: 19 tests
passed initially; after correcting a test assertion to exclude artwork fallback
initials, both affected tests passed on the focused rerun. The final frontend build
passed. Browser checks at 1008px and 1463px confirmed readable type badges, unchanged
labels and no horizontal overflow; changed and retained results were both previewed
without applying or saving them.

The compact configuration-table follow-up passed all **20 dialog tests** and the
frontend production build. Browser verification at the 1008px desktop boundary
showed five roughly 50px priority rows with one shared set of headings, no horizontal
overflow, live minimum updates and a separate compact tie-break-only table.

The shared starting-setup follow-up also passed all **20 dialog tests** and the
frontend production build. Browser verification confirmed that Strict shows the
larger rounded summary with racer and machine type badges beside their names, without
requesting Balanced-only reference stats. Balanced retained the same summary and its
compact priority table.

The recommendation dialog now uses a wider desktop layout. Strict priority rows show
the frozen setup's current passive stat values and place their reorder controls at the
right edge; Balanced keeps the same values in its Current column. The editor reuses its
already-loaded stat result, while Balanced retains its fallback reference-stat load.

No commit, push, deployment, production access, existing-service restart or tunnel
change was performed. Frontend coverage is at `frontend/coverage/index.html`.

Reproduce backend verification only against a disposable database, for example:

```powershell
# From backend/, with isolated PostgreSQL already listening at this port:
mvn '-Dtest=BalancedObjectiveTest,BalancedSolverTest,BuildRecommendationSolverTest,BuildRecommendationServiceTest,ArchitectureTest,BuildRecommendationIntegrationTest,BalancedCatalogIntegrationTest,CommunitySelectionTest' '-Dquarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:55439/ringlab_balanced_test' '-Dquarkus.datasource.username=ringlab' '-Dquarkus.datasource.password=ringlab' '-Dquarkus.datasource.devservices.enabled=false' '-Dquarkus.http.test-port=18089' package
./scripts/SeedDemoData.Tests.ps1

# From frontend/:
npm run test:coverage -- --maxWorkers=2
npm run build
```

Pure classes also run directly through IntelliJ's gutter without infrastructure:
`BalancedObjectiveTest`, `BalancedSolverTest`, `BuildRecommendationSolverTest`,
`BuildRecommendationServiceTest`. Broader unrelated backend acceptance/packaged
suites are a separate manual check against an isolated database.
