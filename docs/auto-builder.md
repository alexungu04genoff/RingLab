# Auto-builder v1

Auto-builder proposes an unsaved loadout. It is a deterministic numerical search,
not a language model, race simulation, popularity ranking or map recommendation.
The existing editor owns publishing. No recommendation request writes builds,
bookmarks, votes, comments or catalog data.

Strict remains the default. [Balanced mode](balanced-auto-builder.md) adds hard
per-stat loss floors and a weighted trade-off objective, using the same popup,
endpoint, locks, detached catalog, budgets and Apply-to-draft flow.

## Strict objective and ties

The priority list must contain Acceleration, Speed, Handling, Boost and Power
exactly once. The default order is that sequence. The domain compares the
`PassiveStatsCalculator`'s five **passive-adjusted** `BigDecimal` values
lexicographically, using `compareTo`. One point in an earlier stat wins against
any improvement in later stats. Decimal scale and UI bar widths do not affect
the comparison. Strict uses no weights or numerical rounding in selection.
Values may exceed 100; effective in-game caps remain unestablished.

If all five values tie, prefer:

1. Fewer changed component IDs plus the symmetric difference of gadget ID sets
   (an addition and a removal each count as one change).
2. Fewer new gadget IDs.
3. Less total Gadget Plate slot consumption.
4. Lexicographically smaller canonical UUID strings, in racer/front/rear/tire
   order, followed by the sorted gadget ID set.

Gadget display-order changes are not numerical improvements or item changes.
Retained gadgets keep their relative order from the draft; new gadgets are
appended in canonical UUID order. A legal, fully scored current setup of the
requested machine type is evaluated first as an incumbent. Completed search
cannot make it worse; a fully tied legal baseline is retained and reported as
already best. An incomplete search can retain it only as best found.

## Locks and machine constraints

Locks start empty and belong to the editor's route, remix source, authenticated
user and session generation. They are never published, persisted or remixed.
Racer, Front, Rear, applicable Tires and each selected known gadget have real
accessible toggle buttons. Locked selects/checkboxes cannot change. Stock
replacement is disabled while any part is locked; incompatible type changes
are rejected. Unlocking remains an explicit editor action.

The machine group toggle operates on those same individual part locks. It has
unlocked, partly locked and locked states. It requires a complete valid setup,
including a mixed-source setup; it never converts one to stock. Applying a
proposal preserves locks without locking new selections.

The chosen machine type is fixed. Speed, Acceleration, Handling and Power
require Front/Rear/Tire of that source-machine type. Boost requires only
Front/Rear and always returns `tirePartId: null`. A locked tire conflicts with
Boost. Racer type is independent. The server resolves slot/type/ID facts from
its catalog and rejects unknown IDs, wrong slots, locks not present in current
selections, duplicate gadgets and locked gadgets that cannot fit both rows of
three slots. Total cost alone is insufficient: three two-slot gadgets do not fit.

## Architecture and algorithm

`domain/build/recommendation` contains immutable selections, request, catalog
snapshot and result records, exact stat comparison and `BuildRecommendationSolver`.
These classes use only the JDK and domain. The compatibility rule now lives in
`domain/gamedata/MachineCompatibility`; the existing application facade preserves
its validation messages and semantic exceptions for existing callers.

`RecommendationCatalogLoader` reads the chosen patch, all four catalog lists and
the two versioned contribution maps once through `GameDataRepository` and
`BaseStatsRepository`, and detaches immutable maps in a short transaction. Catalog
facts are read-only at runtime; migrations are not run concurrently with search.
`BuildRecommendationService` runs the solver after that transaction returns and
uses `NOT_SUPPORTED` to keep even an enclosing transaction out of CPU search.
The REST adapter authenticates the current account and maps transport records.
Persistence never selects the winning build. Architecture tests protect these
boundaries and forbid optimizer/comparator execution from adapters.

The preserved **Strict** search uses the following reduction for each fixed machine type:

1. Check hard locks and whether legal component choices exist, independently of
   whether their base values are complete.
2. Choose the best fully known part for each required slot. Group eligible racers
   by their own racing type (unknown type is a separate group), then select the
   best fully known racer in each group.
3. For each group, enumerate gadget subsets that extend the locks. Evaluate
   combinations with the existing passive calculator and complete plate validator.
4. Compare supported candidates and the incumbent with the exact objective and
   tie-breaks. Preserve retained display order in the final selection.

This reduction is exact for the current rules: base contributions add; gadget
adjustments inspect only racer **type** and machine **type**, never component IDs.
Within a fixed pair of types the same gadget adjustment is added to every base
combination. Lexicographic order is translation invariant, so independent maxima
for each component maximize their sum. If the sums tie, each independently
maximal component's entire vector ties too. Component change counts add and the
stable ID order decomposes by slot. Gadgets are still searched jointly for every
racer type, so racer-dependent kits cannot be optimized using the wrong racer.
Changing these rule dependencies requires revisiting the proof and exhaustive
comparison tests; this is deliberately not a generic optimizer framework.

New gadgets with an all-zero evaluated vector are omitted: they cannot improve
the objective and lose the addition/slot tie-breaks. Current utility gadgets
remain candidates because avoiding a removal is an earlier tie-break. Branches
with an invalid plate or unsupported combination are pruned. For fixed types,
adding an item cannot repair an unknown effect or an unresolved applied-modifier
stack. Disjoint stat adjustments, all machine-tuner pairs, and the explicitly
reviewed Acceleration Tuner 2 / Acceleration Machine Kit passive pair are supported.
Pair permissions use fixed effect identities and are not transitive: adding a third
cannot restore support for a rejected pair. No pruning assumes
that an individually strongest gadget is globally best.

No full component Cartesian product is allocated. With R racers, P parts, G
eligible optional gadgets, at most six type groups and V visited subset nodes,
work is approximately O(R + P + 6G + 6V), plus bounded six-item plate/stat work
and stable catalog sorting. Before pruning, V is bounded by subsets through seven
items (the seventh positive-cost item is immediately rejected). Working memory is
O(R + P + G) plus a small recursion stack. No commercial solver or infrastructure
dependency is added.

## Supported data and honest outcomes

Only the explicit `crossworlds-1.4.1-passive-2026-09-27.1` ruleset is supported.
Other selected patches return unavailable without fallback. Only fully known
base vectors and `CALCULATED` passive results compete. Missing values are never
zero; unresolved stacking and unreviewed gadgets cannot be optimized as base-only.
Locked unsupported effects include a named reason in the result. A group-specific
restriction identifies the excluded racer type rather than falsely claiming the
winning group's locks were unsupported.

Reviewed conditional/non-stat gadgets can remain locked, but their race-time or
utility benefit is explicitly outside the objective. No current lap, held rings,
collision, transformation, map or activation is invented. Penalties are retained.
Current catalog costs apply to the plate; historical/versioned costs remain a
data limitation. “Best supported” means within this documented model and catalog,
not objectively best in a real race. See [passive gadget evidence](passive-gadget-sources.md).

The response distinguishes:

| Outcome | Meaning |
| --- | --- |
| `ESTABLISHED` | Completed search established the best supported candidate. |
| `BEST_FOUND` | A valid candidate exists, but search hit a budget. |
| `NO_LEGAL_COMPLETION` | Complete feasibility check found no catalog completion. |
| `NO_FEASIBLE_CANDIDATE` | Complete Balanced supported search found no candidate meeting all floors and hard constraints. |
| `UNAVAILABLE` | Required patch, base contributions or locked effects cannot be fully evaluated. |
| `LIMIT_WITHOUT_CANDIDATE` | Budget ended before finding a supported candidate; not proof of infeasibility. |

Current stats use the same selected patch and passive basis as the proposal. If
the current setup is incomplete, illegal or partially supported, all current
comparison values display unavailable rather than fabricated improvements.
Explanations identify the first actual differing priority relative to the current
setup, the fixed machine type, locks and search coverage. They do not assert an
unobserved tie between competing candidates.

## API, budgets and cancellation

`POST /api/build-recommendations` requires the normal bearer-authenticated user.
Responses have `Cache-Control: private, no-store`. The frontend proxy permits
this explicit route while keeping development-fixture endpoints blocked.

```json
{
  "gameVersionId": "<selected catalog UUID>",
  "machineType": "SPEED",
  "priorities": ["ACCELERATION", "SPEED", "HANDLING", "BOOST", "POWER"],
  "current": {
    "racerId": null, "frontPartId": null, "rearPartId": null,
    "tirePartId": null, "gadgetIds": []
  },
  "locked": {
    "racerId": null, "frontPartId": null, "rearPartId": null,
    "tirePartId": null, "gadgetIds": []
  }
}
```

Incomplete current selections are allowed; title/description/maps are not request
inputs. This applies to Strict; Balanced explicitly requires a complete supported
reference. Omitting `mode` preserves Strict. Balanced adds `mode: "BALANCED"`,
active `priorities` and a typed `balanced` configuration; see the focused guide.
The server supplies stats, costs, compatibility and rules. The response
contains `outcome`, nullable `selection`, `currentStats`, `recommendedStats`,
`alreadyBest`, `reason`, `restrictions`, `ruleset`, `note`, `work`, `elapsedMillis`.
Validation uses existing safe 400 errors; unsupported data is a typed 200 outcome.

The REST method is `@Blocking` (worker execution). The singleton service admits
at most two simultaneous calculations and immediately returns the existing safe
503 error when occupied. Each search has server-owned 100,000-step and two-second
budgets, plus interruption checks. The time budget covers in-memory solving,
not catalog I/O. Dedicated per-user token buckets allow six requests/minute by
default (`RATE_LIMIT_BUILD_RECOMMENDATION`); rejection uses existing 429 and
`Retry-After`. There is no queue, automatic retry, job persistence or polling.

The frontend sends one asynchronous request after Calculate, with an honest
activity message. Cancel, unmount, user/session change and mobile resize abort
fetch and invalidate its request identity. An old response cannot reopen or
replace a newer result. Browser cancellation does not promise to stop server CPU;
the server's independent budgets remain the cancellation bound.

## Editor and professor demonstration

The existing desktop breakpoint is **1001 CSS pixels** (`max-width: 1000px` is
the existing narrower layout). Controls and dialog use viewport media queries,
never user-agent detection. Below it, recommendation UI disappears, an active
request/dialog closes, locks reset and the draft remains intact for manual editing.
The desktop action stays in the preview's sticky area. One native modal dialog
provides background inertness, Escape and focus return. An explicit Tab/Shift+Tab
guard wraps between enabled controls, including entry from the initially focused
heading; this also prevents native browser focus from leaving the modal at either end.
Colored stat badges can be dragged to reorder priorities. Compact Move up/down
buttons also support the entire workflow from the keyboard without dragging.
Lock icons sit beside their field-label text, with separate accessible buttons.

Apply verifies session/editor identity and all calculation-relevant draft fields
and locks. It changes only unlocked selections and the explicitly chosen machine
type, revalidates the resulting catalog/plate and reconciles the stock indicator.
Title, description, patch, recommended maps and remix provenance come from the
live draft. Cancel changes nothing; Back to priorities discards the proposal.

Safe demonstration on an isolated database and local preview:

1. Sign in to a disposable local account, open a new unsaved build and select
   Ver. 1.4.1. A title/description is unnecessary for calculation.
2. Choose Sonic the Hedgehog and a Speed setup. Select Speed Tuner 1 and Speed
   Tuner 2 (stable gadget suffixes 52/53, each current cost one). Lock the racer,
   both gadgets and the compatible tire.
3. Open Recommend, inspect the locks, move Speed above Acceleration and calculate.
   Inspect changes and negative as well as positive stat deltas, then Cancel.
4. Calculate again and Apply to draft. Verify title/maps/patch and locks remain.
   Do not publish; no build/community records have been written.
5. With the tire still locked, choose Boost in the popup and inspect the precise
   conflict. Cancel, unlock the tire in the editor and calculate Boost. The
   proposed setup contains exactly Front/Rear and no tire.
6. Use keyboard Tab/Shift+Tab and Escape, and shrink the viewport while calculating.
   The editor remains usable and the unsaved draft remains intact.

## Strict implementation verification history

The measurements below describe the original Strict implementation. Current
Balanced verification and its frozen real-catalog demonstration are recorded in
[Balanced mode](balanced-auto-builder.md).

Pure solver tests compare exact arithmetic and independently enumerate small
Cartesian fixtures, including fixed-type effects and deterministic subset ties.
Application tests count one-time catalog reads, validate error translation and
exercise two simultaneous permits. API tests use an isolated test database and
assert no build/vote/comment/bookmark changes, authenticated access, hard locks,
Boost, tampered inputs, unavailable patches and dedicated rate limits. Frontend
tests cover helpers, locks, configuration/request/cancel/retry/apply, stale contexts,
session replacement and mobile resize; native browser checks cover actual modal
focus behavior.

Measured on the isolated Windows/Java 21/PostgreSQL 17.6 environment on
2026-09-27, with the existing catalog (52 racers, 174 parts, 79 gadgets, four
patches): the API test locking Sonic, Speed Tuner 1/2 and a compatible Speed tire
established a result in **122 search steps / 4 ms solver time**. This is one local
measurement, not an end-to-end latency guarantee. Additional requests through the
local Vite proxy to the packaged JVM measured:

| Constraints / machine type | Outcome | Search steps | Solver time | HTTP round trip |
| --- | --- | ---: | ---: | ---: |
| Unlocked / Speed | Established | 1,202 | 54 ms | 136 ms |
| Unlocked / Acceleration | Established | 1,199 | 22 ms | 51 ms |
| Unlocked / Boost | Established | 1,212 | 15 ms | 43 ms |
| Sonic + Speed Tuner 1/2 + Speedster tire locked / Speed | Established | 122 | 3 ms | 30 ms |
| Identical locked request repeated | Established | 122 | 1 ms | 26 ms |

The repeated locked selection was identical; build count remained zero after
all five read-only calls. Verification work was also active on this computer;
these measurements are illustrative local observations. The pure exhaustive comparison
test checks 20 seeded small catalogs; all 12 solver tests together took 482 ms in
the final backend verification.

The full backend test run covered 310 tests. An initial global mock-mail override
prevented the existing local SMTP capture, and two new API fixtures used the
short name “Sonic” instead of the catalog name. After correcting those test
conditions, the affected acceptance/API/solver classes (34 tests) and all 18
packaged API tests passed. The other full-suite classes had already passed;
no unrelated suite was rerun as a workaround. The unchanged JaCoCo gates passed:
97.93% instruction, 88.52% branch and 97.97% line coverage.

After the inline padlock and draggable badge adjustments, the complete frontend
coverage run passed **292 tests in 34 files** with 93.22% statements/lines,
91.54% branches and 87.81% functions; existing thresholds were unchanged.
`npm run build` passed TypeScript checking and Vite production compilation.
The coverage command used `--maxWorkers=2` to keep local resource use bounded.

Live browser checks confirmed colored-badge drag reordering, a locked-tire Boost
conflict with Calculate disabled, and mixed-source machine locks changing from
partial to all locked to unlocked without changing the selected parts. Speed Apply
preserved author metadata and locks. Boost result/Apply kept Sonic and selected
Jaws Rocket Front/Rear with no tire. Resizing an open dialog to 400 CSS pixels
closed it, removed locks and Recommend, preserved metadata and all selections,
and left manual selects and gadget checkboxes enabled. The native browser's focus
escape at the modal endpoints prompted the explicit focus-wrap guard described
above, covered by focused configuration/result keyboard tests.
After that isolated guard change, all 10 dialog tests passed and the frontend
build passed; the full coverage percentages above describe the earlier 292-test
run, not a repeated full-suite run after this guard.

Use disposable databases for backend verification. The existing acceptance and
packaged tests capture email on an ephemeral **loopback** SMTP server; do not
force `quarkus.mailer.mock=true` over that test resource, because it prevents its
verification-token capture. Normal preview still uses mock mail. No real SMTP
service or original development service is needed.

Changes are intentionally uncommitted in the dedicated worktree. Review the diff,
verification reports and demo before manually integrating with the original
workspace. No schema migration, fixture refresh, new dependency or Git history
operation is part of this feature. Coverage remains at
`backend/target/site/jacoco/index.html` and `frontend/coverage/index.html`.

Optional manual checks, run from the indicated project directory:

```powershell
# From backend/: fast pure tests; no database or running application required
mvn "-Dtest=BuildRecommendationSolverTest,BuildRecommendationServiceTest" test

# From frontend/: focused modal/lock/merge behavior
npm test -- src/BuildRecommendationDialog.test.tsx src/recommendation.test.ts src/pages/BuildEditor.test.tsx

# From frontend/: complete coverage gates and production build
npm run test:coverage -- --maxWorkers=2
npm run build
```

The pure backend classes can also be run individually using IntelliJ's gutter
Run/Debug actions or Run with Coverage. Any broader backend verification must
point explicitly at a disposable test database; keep the existing development
services and community database untouched.
