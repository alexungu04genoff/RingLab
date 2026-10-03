# Verification

## Phase 5B: safe catalog corrections — 2026-10-03

Baseline: clean main `a3bf329`; Phase 5A was audit-only and its `.tools` evidence
remains untracked. See [the complete change/evidence ledger](game-data-phase5b.md).

- `mvn -f backend/pom.xml verify`: **534 tests + 20 packaged API tests**, zero
  failures/errors/skips on the final run, including ArchitectureTest, migration
  rehearsal, recommendation oracle and the closed additive passive checks.
- Fresh JaCoCo: **97.27% instructions, 89.84% branches, 97.47% lines**. Existing
  gates passed; reports remain in `backend/target/site/jacoco/`.
- `npm run test:coverage -- --maxWorkers=2 --minWorkers=1`: **396 tests in 42 files**,
  all passing; **95.24% statements/lines, 91.99% branches, 89.67% functions**.
- Frontend `npm run build` passed; `git diff --check` passed.
- Packaged importer VALIDATE → PLAN → APPLY → PLAN against the disposable
  `ringlab_phase5b_test` database: **zero inserts, updates or deletes**, stable token.
  The upgrade test independently migrates V34 → V35 around a stored legacy plate
  and proves that its gadget identities/order survive the cost correction.
- SHA-256 checks against the Phase 5A baseline preserved nine historical/part/
  scenario/map CSVs byte-for-byte, including every existing machine-part stat row.
  All 99 original effect rows remain unchanged.

The initial concurrent full runs hit frontend timing limits and two backend
connection-acquisition timeouts under memory pressure. They were rerun sequentially
with fewer frontend workers; no timeouts, coverage thresholds or search budgets
were increased. One old release-date API expectation was corrected to December 18.
Existing deprecated/unchecked test-compilation notices remain non-fatal. No
production database or deployment workflow was used.

To reproduce, use the disposable database/JWT environment described below, then
the commands above. The new pure unit class is `Phase5bCatalogTest`; additional
legacy-save coverage is in `BuildServiceTest`. Production recovery implications
are documented alongside V35 before any future deployment.

## Phase 4: recommendation integration and presentation — 2026-10-03

Baseline: clean `d854034` on main (Phases 1–3, including the exhaustive oracle).
The isolated `codex/recommendation-final-polish` checkout leaves the running development
checkout and deployment untouched. No backend production code, game data, search limits,
passive policy or recommendation mathematics changed.

### Acceptance coverage and API review

- `BuildRecommendationIntegrationTest` exercises both modes with empty BOOST and SPEED
  drafts, required tire composition, racer-only and front-only locks, complete applied
  selections, KEEP_CURRENT empty/nonempty passive-and-utility plates, and OPTIMIZE_UNLOCKED
  with a gadget lock. Excluding the nominal winning racer forces a different owned result.
- The scenario test calculates lap 1 and lap 2 independently, explicitly using NORMAL
  form, then repeats the same recommendation and checks unchanged selections/totals.
  Unspecified vehicle form correctly produces a partial scenario result; the first test
  run exposed this omitted fixture condition, which was corrected without changing rules.
- `BuildRecommendationContractTest` sends synthetic decimals through JSON request parsing,
  REST resource → application → domain → response JSON. At a 95 floor, 95.00 survives and
  94.99 does not; survivor counts and the next stage's maximum are checked. A deterministic
  work cutoff also verifies BEST_FOUND with `proven:false` and no stage claims through REST.
- Existing independent oracle, Strict equivalence, additive passive, ownership revision,
  application capacity and packaged API tests remain part of full verification.
- No endpoint or transport field was removed. `secondary` as an Ignore alias and legacy
  100% normalization are deliberate tested Phase 3 compatibility, not dead scoring logic.
  Current totals remain optional presentation fields. Scenario/basis request fields remain
  rejected. All six outcome contracts remain distinct.

### Frontend behavior and interaction

Both modes now share selection comparisons with Added/Changed/Kept labels, visible kept
items and per-gadget additions/removals/locks. Complete totals enable Current/Recommended;
incomplete drafts show Recommended alone. Strict explains first differing priority;
Balanced lists active losses, ignored stats, proven stage details and final comparison.
Outcome headings distinguish proof, search limit, structural impossibility and missing data.
UNAVAILABLE no longer displays best-found/search-limit wording for unproven stages.

Apply shows specific draft/lock, collection-change, pending-write and collection-load
blockers. Identical refreshes remain applicable. Canceled Apply checks cannot overwrite
newer state with late errors, duplicate Apply is suppressed, and a session replacement
cannot leave a settled calculation permanently busy. Scenario Preview explicitly states
that its race conditions do not affect recommendations.

Recommendations are enabled at all widths. Resize tests verify preserved locks/dialog/
requests and mobile reopening. CSS provides narrow priority rows, labeled loss/Ignore,
flexible comparisons, 44px mobile controls, wrapping actions and a separately scrolling
modal body. Keyboard tests cover order controls, focus wrapping, Escape/cancel and focus
return. Closed-details descendants are excluded from the focus guard. Desktop-only
gating, resize-reset state, duplicated selection rendering and obsolete comparison CSS
were removed; two focused result components keep responsibilities explicit.

Automated checks cover these interactions in jsdom, not a visual browser renderer.
Starting an isolated browser-test stack was rejected by automatic approval review with
only “blocked by policy”; no browser visual pass is claimed. Manual follow-up: inspect
configuration, result, long names, scroll/focus and Apply at 1280px, 768px and 390px on an
authorized local preview. No new browser framework was introduced.

### Verification record

Frontend: 393 tests in 42 files passed. Coverage is 95.24% statements/lines, 91.99%
branches and 89.67% functions; existing gates passed. Production build passed.
Backend verification uses `mvn -f backend/pom.xml verify` with the disposable database
`ringlab_recommendation_phase4_20261003`, disabled dev services and test port 8087.
The normal development database is not used. Final backend results are recorded below
for the completed run. Reports remain `backend/target/site/jacoco/index.html` and
`frontend/coverage/index.html`.

- `mvn -f backend/pom.xml verify`: passed, 527 Surefire tests and 20 packaged API tests,
  including all four ArchitectureTest checks and the independent Balanced oracle and
  Strict equivalence fixtures. The first full run had only the scenario fixture failure
  described above; the corrected full rerun has no failures or skipped tests.
- JaCoCo: 98.03% instructions, 90.50% branches, 97.99% lines. Recommendation domain:
  99.52% instructions, 95.25% branches, 99.80% lines. Existing gates passed unchanged.
- `npm run test:coverage -- --maxWorkers=2`: passed, 393 tests / 42 files. Focused dialog,
  collection and editor checks also passed; old heading assertions were updated for the
  explicit BEST_FOUND title and numbered priorities.
- `npm run build`: passed after final responsive/markup cleanup. `git diff --check`: passed.
- Read-only final review checked UI/backend wording, Ignore, ownership/locks, gadget scope,
  proof claims and scenario separation. Backend production sources have no diff. The
  recommendation atlas diagram matches its standalone source; unrelated atlas sections
  were not regenerated. No deployment or change to the live local checkout occurred.

The existing limitation remains: an unrestricted optimized gadget search can return
BEST_FOUND at 100,000 work steps instead of proving the complete population. Two seconds
and two simultaneous calculations remain unchanged. Dragon Brave, Jaws Rocket, Sakura
Board, TYPE-W Windy, Triple Fan and Substitute Item cost discrepancies remain untouched.

## Phase 3: sequential-survivor Balanced — 2026-10-03

Started from clean main `7b4e0e4`, after verifying Phase 1 passive-only recommendations
and collection revisions and Phase 2 additive passives / gadget scope. Work is isolated
on `codex/balanced-survivor-thresholds`; no deployment is part of this phase.

The old current-reference floors, weighted score and secondary total are removed.
Balanced now accepts empty/partial drafts and proves successive maxima over surviving
candidates with exact signed thresholds. Explicit Ignore removes all numerical influence.
The streaming implementation uses safe suffix bounds and Cartesian subtree counts;
it retains the existing 100,000-step/two-second/two-calculation limits.

### API validation and proof contract

- Required: supported patch, machine type, current/locked selections, five-stat order,
  and Balanced configuration. Current IDs may be null; locks must match selected IDs.
- `balanced.maximumLossPercent` supplies exactly the active stats, each in `[0,100)`.
  `balanced.ignored` contains distinct ignored stats. All Ignore is valid.
- Compatibility: legacy `secondary` aliases `ignored`, active-only order can append
  the ignored tail, and 100% normalizes to Ignore. Duplicate/missing stats, unknown
  names, negative/out-of-range/nonfinite percentages are rejected.
- Current stats are optional presentation data. Unsupported candidate totals remain
  excluded, and unsupported kept gadget data produces UNAVAILABLE, never zero.
- Established responses contain `balanced: {proven: true, stages: [...]}`. Each stage
  exposes stat, sacrifice, exact best, threshold and survivor counts. Non-established
  responses contain `proven: false, stages: []`; truncated searches never claim
  infeasibility or proven maxima. No candidate list is exposed.
- Both Quarkus REST tests and the shared packaged API contract exercise empty Balanced
  requests, BOOST/no tire and structured proven stages. REST tests also cover legacy
  100%/secondary compatibility, all Ignore and server-authoritative current stats.

`BalancedConfigurationTest`, `BalancedSolverTest` and `RecommendationExhaustiveOracle`
cover exact boundaries, negative/zero maxima, ignored stats, ordered final survivors,
locks, ownership, all Ignore and budget cutoffs. The oracle independently materializes
small catalogs, evaluates trusted passive totals, filters survivor lists and compares
convenience ties. There are 480 oracle comparisons across 240 seeded catalogs, 240
Strict-at-zero-loss equivalence checks, and 240 reversed-catalog determinism checks.
They cover all machine types, both scopes, decimals, overlapping passives, part/racer/
gadget locks and racer/source-machine/gadget exclusions. Imported game facts are unchanged.

Frontend tests exercise incomplete drafts without a reference fetch, explicit Ignore,
loss validation, preserved ordering/settings, proven versus withheld stage displays,
optional Current comparison, collection revision safety and unchanged gadget scopes.

Verification commands: `mvn -f backend/pom.xml verify` against a disposable PostgreSQL
database, `npm run test:coverage -- --maxWorkers=2`, `npm run build`, and `git diff --check`.
The existing PostgreSQL container is reused without modifying the development database.
Coverage reports remain `backend/target/site/jacoco/index.html` and `frontend/coverage/index.html`.

Real-catalog empty-draft benchmark (same production limits, measured during verification):

| Machine type | Keep current: work / ms | Optimize unlocked: work / ms |
| --- | --- | --- |
| SPEED | 5,805 / 7, ESTABLISHED | 100,000 / 477, BEST_FOUND |
| ACCELERATION | 6,203 / 12, ESTABLISHED | 100,000 / 249, BEST_FOUND |
| HANDLING | 3,707 / 3, ESTABLISHED | 100,000 / 349, BEST_FOUND |
| POWER | 4,227 / 4, ESTABLISHED | 100,000 / 215, BEST_FOUND |
| BOOST | 1,920 / 1, ESTABLISHED | 100,000 / 287, BEST_FOUND |

Elapsed times are observations from this machine, not guarantees. The unrestricted
gadget population remains a material limit: all five OPTIMIZE_UNLOCKED requests
returned a legal candidate without completing proof. Their threshold lists were empty.
The existing budgets were not increased to force establishment.

Final results:

- `mvn -f backend/pom.xml verify`: **passed**, 523 Surefire tests and 20 packaged API
  tests, including architecture, application/service, passive/scenario and recommendation
  tests. The disposable database URL, credentials, disabled dev services and test port
  8087 were supplied as environment/system properties. No application limits changed.
- JaCoCo: **98.03% instructions, 90.50% branches, 97.99% lines**; existing gates passed.
  Recommendation domain: **99.52% instructions, 95.25% branches, 99.80% lines**.
- Frontend: **381 tests in 42 files passed**. Coverage: **95.20% statements/lines,
  91.93% branches, 89.38% functions**; existing gates passed.
- `npm run build`: passed. `git diff --check`: passed.
- During transition, old backend weighted-objective assertions and an editor test
  looking for the removed Balanced Current column were updated. Final runs have no failures.
- No deployment, database migration, catalog import or game-fact change was performed.

## Phase 2: closed passive addition and gadget scope — 2026-10-03

Started from clean main `6551bee`, after Phase 1 was committed and fast-forwarded.
The shared passive calculator replaces the old overlap whitelist (tuners plus one
exact tuner/kit pair) with a closed 23-effect signed-addition policy. Import facts
and CSV history are unchanged. Calculation notes expose the separate Java policy
`crossworlds-1.4.1-passive-additive-v1` and `SUPPORTED_BY_COMMUNITY_CALCULATOR`.
Official evidence is not claimed for the broader stacking model.

Domain/API/UI `gadgetScope` defaults to `KEEP_CURRENT`; `OPTIMIZE_UNLOCKED` enables
gadget subset search. Tests cover preserved empty/numeric/utility/conditional plates,
order, type-dependent recalculation, invalid/unowned/unknown kept plates, gadget
removal/replacement, locks, ownership, plate layout, independent exhaustive oracle
comparison and deterministic ties. Both Strict and Balanced honor the scope; changing
the UI scope cancels the old proposal. Phase 1 collection checks remain covered.

`ClosedPassiveAdditiveTest` enumerates **3,214 legal subsets × 25 type pairs** and
checks both forward and reversed gadget orders: **160,700 calculator evaluations**.
Expected values are independently resolved from repository canonical vectors.
Future gadget/effect identities cannot gain permission from a CSV label.
The frozen legacy fact tables remain untouched: single-effect and metadata parity
stay covered, obsolete overlap-pair assertions are replaced by exhaustive additive
coverage, and scenario policy parity uses the same new passive input on both sides.

Focused backend command:
`mvn -f backend/pom.xml -Dtest=PassiveStatsCalculatorTest,ClosedPassiveAdditiveTest,BuildRecommendationSolverTest,BalancedSolverTest,BalancedObjectiveTest,RuleFactsParityTest,ScenarioStatsCalculatorTest,ArchitectureTest,GameDataRuleImportTest test`.
All 85 focused cases passed across the initial run and the 24-case solver correction.
Full `mvn -f backend/pom.xml verify` then passed **523 Surefire tests and 19 packaged
API tests**, with no failures or skips, including ArchitectureTest and both scope
contracts. It used disposable PostgreSQL database `ringlab_recommendation_phase2_20261003`,
test port 8087, disabled dev services and ephemeral test keys. JaCoCo measured
**98.02% instructions, 90.33% branches, 97.98% lines**; gates stayed 90%/80%/90%.
Reports remain at `backend/target/site/jacoco/index.html` and `jacoco.xml`.

Frontend `npm run test:coverage -- --maxWorkers=2` passed **379 tests in 42 files**:
95.21% statements/lines, 91.88% branches, 89.43% functions, with gates unchanged.
`npm run build` and `git diff --check` passed. Initial failures were corrected
test expectations for the new default, scope explanation and intentionally expanded
stacking, plus an unsupported Testing Library option in a new test. Existing
deprecated/unchecked compiler notices and the packaged runner's ignored logging
configuration warning remain non-fatal.

No scenario calculator, Balanced objective, search budget, canonical machine-part
values, Substitute Item cost, migration or dependency changed. Updated docs include
auto-builder, Balanced scope, passive evidence/audit, Guide, architecture and the
atlas's existing recommendation diagram.

## Passive-only recommendation API and collection revisions

`POST /api/build-recommendations` optimizes base stats plus reviewed always-active
passive adjustments. Race-state and triggered effects are intentionally excluded
and remain available in Scenario Preview. The removed `basis` and `scenario`
fields, including null values, are rejected as unknown top-level fields with HTTP 400.
No scenario diagnostics are serialized. Existing ID, lock, ownership, patch, plate,
authentication, Strict and Balanced behavior remains unchanged.

Standalone preview still uses `ScenarioContextRequest`, its existing exact numeric
validation, scenario facts and Quick Starter + Sea Dog assumption. No shared preview
contract was removed.

Focused command:
`mvn -f backend/pom.xml "-Dtest=BuildRecommendationSolverTest,BalancedSolverTest,BalancedObjectiveTest,ArchitectureTest" test`.
`RecommendationExhaustiveOracle` independently enumerates legal owned component
products and gadget subsets, calls public calculators and implements the comparator
without production search/pruning. Trials vary racer/machine types, locks,
ownership, priorities, Balanced floors and catalog iteration order.
`BuildRecommendationIntegrationTest` checks passive totals, removed-field rejection and
validation. `ApiContract.passiveRecommendationContractRejectsRemovedScenarioFieldsAfterPackaging` also runs
through `PackagedApiIT`. Full verification: `mvn -f backend/pom.xml verify`;
coverage remains at `backend/target/site/jacoco/index.html`.

Collection tests verify normalized racer/machine/gadget exclusion sets: identical,
reordered, duplicate and no-op reads keep the revision; a successful changed state
increments it once. Refresh returns the authoritative data and revision together.
Apply waits for a shared in-flight focus refresh and still rejects real changes,
failed reads, pending mutations and changed sessions/drafts/locks/configuration.
Focused frontend files: `Collection.test.tsx`, `CollectionRecommendation.test.tsx`
and `BuildRecommendationDialog.test.tsx`. Run `npm run test:coverage -- --maxWorkers=2`
and `npm run build` from frontend for the complete requested verification.

Verification on 2026-10-03 used an isolated worktree based on main `ff69aba`.
The focused solver/architecture command passed 36 tests. Focused frontend collection/
recommendation verification passed 51 tests in three files; full frontend coverage
passed 377 tests in 42 files. Coverage measured 95.19% statements/lines, 91.87%
branches and 89.38% functions, with existing gates unchanged. `npm run build` passed.
The initial build exposed a missing required remix field in the new test fixture;
the fixture now includes it. No application contract was relaxed.

Full `mvn -f backend/pom.xml verify` executed 518 Surefire tests against a separate
disposable PostgreSQL 17.11 database. The new passive-contract test initially failed:
its fixed recommendation total overlooked optional passive tuners, its numeric
matcher assumed a float, and its follow-up query encoded a gadget list incorrectly.
The corrected test asserts the known starting total, preservation of both locked
conditional gadgets, and equality of all recommended stats with the standalone
passive endpoint. Production code was unchanged during these fixture corrections.
Completion command `mvn -f backend/pom.xml -Dtest=BuildRecommendationIntegrationTest verify`
passed all eight affected tests, all 19 packaged API tests, packaging and coverage
checks. The other 510 passing tests were not repeated after the final test-only fix.
Final reports contain 518 passing Surefire cases plus 19 packaged cases, no skips.
ArchitectureTest, exhaustive passive solver characterization, shared Scenario Preview
and rule-fact parity tests passed. JaCoCo measured 98.01% instructions, 90.33%
branches and 97.98% lines across these runs; 90%/80%/90% gates were unchanged.

The database was `ringlab_recommendation_cleanup_20261003` on the existing local
PostgreSQL container, with HTTP test port 8087 and dev services disabled. Test keys
were ephemeral; the normal development database and services were not changed.
The disposable database was removed after verification. `git diff --check` passed.
Existing deprecated/unchecked test compiler notices and the packaged runner's ignored
logging-category setting remain non-fatal. No commit, push or deployment was performed.

### Historical verification of the removed scenario objective

Verification on 2026-10-02: the final full `mvn -f backend/pom.xml verify` passed
527 unit/integration tests and 19 packaged API tests, with no failures or skips.
A subsequent focused run of `ScenarioRecommendationTest` passed all 10 tests,
including the final explicit equal-total retention/cost/UUID tie case; it also
regenerated JaCoCo and passed the unchanged coverage gates. Coverage: instructions
97.25%, branches 89.97%, lines 97.52%. `git diff --check` passed. Earlier attempts
exposed a stale compiled nested context DTO (removed from generated output) and a
new Balanced test fixture missing secondary stats (corrected); neither remains a blocker.
Ignored logs: `.tools/scenario-verify-complete.log` and `.tools/scenario-final-ties.log`.

## Post-refactor hardening — 2026-10-02

Reviewed main `3a8030a` with the rule-fact import and solver decomposition together.
The concrete runtime issue was repeated loading of the same immutable reviewed rule
snapshot: six SELECTs per successful call. `ReviewedGadgetRules` now shares the first
successful detached snapshot per application instance; missing/error loads remain
retryable. Four pure tests cover load count, concurrent callers and both retry paths.
The focused command `mvn -f backend/pom.xml "-Dtest=ReviewedGadgetRulesTest,ArchitectureTest" test`
passed all eight tests.

Then `mvn -f backend/pom.xml verify` passed **513 Surefire tests + 18 packaged API
tests**, with no failures or skips, against disposable PostgreSQL 17.11. This included
all nine importer integration tests, fresh V1–V34 canonical CSV/no-op parity, late
FK/unique rollback, future-version isolation, the five frozen-rule characterization
tests, all solver tests and ArchitectureTest. Solver, importer, rule policy and
migrations were left unchanged; no coverage gate or assertion was weakened.

Fresh JaCoCo measured **98.01% instructions, 90.33% branches and 97.97% lines**;
the existing 90% / 80% / 90% gates passed. The cache has all instructions and both
branches covered. Verification used a fresh source copy under
`.tools/hardening-verification/` so the running development server's locked build
files and obsolete pre-decomposition class files could not affect compilation or
coverage. HTML/XML reports are at
`.tools/hardening-verification/backend/target/site/jacoco/index.html` and `jacoco.xml`.
Previous coverage execution data was excluded from this run.

Static documentation checks reconciled the 241 Java files, 19 services, 18 input
ports, 17 outbound ports, 26 application tables, 14 entities and four mappers,
and confirmed all 31 embedded/standalone Mermaid pairs match. Stale counts,
ruleset ID, current-head wording and community rule-loading dependencies were
patched without regenerating the atlas. Earlier verification/baseline history
remains explicitly historical.

Existing deprecated/unchecked test compiler notices and the Windows packaged-runner
logging-category warning remain non-fatal. Frontend source and REST contracts were
untouched, so frontend verification was not rerun. No commit, push or deployment
was performed; the disposable test database was removed after verification.
For a manual rerun, use the focused command above (or IntelliJ's gutter actions),
then the disposable datasource environment example below and `mvn -f backend/pom.xml verify`.

## Versioned gadget rule facts — 2026-10-02

The frozen test-only rules/calculators from main `5655220` first passed five
characterization tests against unchanged production code. The same tests then
compared imported facts and calculations against that independent oracle: all
79 gadgets, known/unknown racer and machine types, all passive pairs, all scenario
conditions across true/false/unknown contexts, utility effects, unsupported patches,
invalid loadouts, unknown base stats and the explicit reviewed/assumed interactions.
There are 29,781 result comparisons plus complete metadata/order/source comparisons.
The old authored tables remain only in `src/test/java/dev/ringlab/characterization`.

The focused 196-test run passed, followed by a fresh full
`mvn -f backend/pom.xml verify`: **505 Surefire tests + 18 packaged API tests**, no
failures or skips. All prior assertions were retained. Fifty-five new test cases
cover parity, rule parsing/validation, immutable plans, source relationships and
PostgreSQL atomicity. All nine importer integration tests passed, including V1–V34
canonical no-op, runtime snapshot equality, a new version/rule snapshot, old rule
calculation preservation, FK failure after early catalog/rule writes and a real
rule-source uniqueness rollback. API, recommendation and ArchitectureTest passed.

Fresh JaCoCo: **97.31% instructions, 89.80% branches, 97.52% lines**, compared with
the phase-one 97.12% / 89.20% / 97.29%. Existing 90% / 80% / 90% gates remain
unchanged. Old execution data was archived before the full run. HTML/XML reports
remain under `backend/target/site/jacoco/`.

The standalone packaged CLI passed offline VALIDATE and PLAN → APPLY → PLAN against
the disposable PostgreSQL database, with zero content writes and a stable approval
token. The new bundle contains 99 passive facts,
10 scenario facts, 274 source rows and one metadata row. The application image
already includes this version directory; no Docker/CI changes were required.
Frontend code and REST DTOs were untouched, so frontend tests/build were not rerun.
Existing deprecated/unchecked compiler notices and the Windows packaged-runner
logging-category warning remain non-fatal.

For a manual full rerun use the environment example below, replacing its disposable
database name with `ringlab_rule_test` (or your own isolated test database), then
run `mvn -f backend/pom.xml verify`. The relevant new classes are
`RuleFactsParityTest`, `GameDataRuleCsvTest`, `GameDataRuleImportTest` and the expanded
`GameDataImportIntegrationTest`. The first three can run from IntelliJ without
PostgreSQL; the integration suite requires the disposable datasource. No commit,
push or deployment is part of this task.

## CSV game-data importer — 2026-10-02

Full `mvn -f backend/pom.xml verify` passed against disposable PostgreSQL 17:
450 Surefire tests and 18 packaged API tests, with no failures or skips. This
includes 86 new importer cases covering strict CSV parsing, detached validation,
safe/unsafe plans, approval-token drift, real FK/unique constraints, transaction
rollback, repeat no-op imports, and original-version calculation for an old build
after importing a new version. Existing test assertions were retained.

Fresh JaCoCo execution data measured **97.12% instructions, 89.20% branches and
97.29% lines**. All existing coverage gates passed unchanged. Reports are in
`backend/target/site/jacoco/index.html` and `jacoco.xml`. Packaged API tests are
separate from the instrumented Surefire coverage. Existing deprecated/unchecked
test compiler notices and the Windows packaged-runner logging-category warning
remain non-fatal.

The packaged standalone command also passed offline VALIDATE and PLAN → APPLY
→ PLAN against a separate scratch database containing only the final V1–V33
catalog. All 1,319 CSV records matched; both plans had zero content writes and
the same approval token. No production or normal development data was imported.

For a manual full rerun, start a disposable PostgreSQL database (never the normal
application database), generate temporary keys with
`java backend/scripts/GenerateJwtKeys.java .tools/import-test-jwt`, and run from
the repository root with the actual disposable connection settings:

```powershell
$env:QUARKUS_DATASOURCE_JDBC_URL = 'jdbc:postgresql://localhost:55433/ringlab_test'
$env:QUARKUS_DATASOURCE_USERNAME = 'ringlab'
$env:QUARKUS_DATASOURCE_PASSWORD = 'ringlab_test'
$env:QUARKUS_DATASOURCE_DEVSERVICES_ENABLED = 'false'
$env:DB_URL = $env:QUARKUS_DATASOURCE_JDBC_URL
$env:DB_USER = $env:QUARKUS_DATASOURCE_USERNAME
$env:DB_PASSWORD = $env:QUARKUS_DATASOURCE_PASSWORD
$env:JWT_PUBLIC_KEY = (Resolve-Path .tools/import-test-jwt/public.pem).Path
$env:JWT_PRIVATE_KEY = (Resolve-Path .tools/import-test-jwt/private.pem).Path
$env:PUBLIC_BASE_URL = 'http://localhost:8081'
mvn -f backend/pom.xml verify
```

Archive any previous JaCoCo execution file before measuring a fresh run.
`GameDataCsvReaderTest`, `GameDataImportCommandTest` and
`GameDataImportServiceTest` also run directly in IntelliJ without infrastructure;
`GameDataImportIntegrationTest` requires the disposable datasource.

Production Compose configuration validation passed. Building
`docker build -f backend/Dockerfile -t ringlab-backend:game-data-review .` was
blocked while downloading a base-image layer (`tls: bad record MAC`); no download
retry was attempted. The container image and its one-shot entrypoint still need
verification once that environment issue is resolved. Frontend tests/build were
not rerun because frontend code and REST contracts did not change. No commit,
push or deployment was performed.

## Current tooling

Backend verification uses Java 21 and Maven:

```text
mvn -f backend/pom.xml verify
```

JaCoCo runs as part of `verify` and writes HTML and XML reports under
`backend/target/site/jacoco/`. Maven enforces 90% instruction, 80% branch and 90% line coverage.
Reports can contain accumulated execution sessions, so their percentages
must not automatically be treated as coverage from the current run. Before a fresh measurement,
archive the existing `backend/target/jacoco-quarkus.exec`; both agents must still
append within the same verification run. The dated backend results in
[refactoring-progress.md](refactoring-progress.md) record the 2026-09-22 fresh-database run;
they do not verify later working-tree changes.

The backend suite includes pure domain/application unit tests, REST resource and
exception-mapping tests, persistence/repository contract integration tests,
Quarkus HTTP acceptance tests, Flyway migration tests, and a packaged-application
API test. `ArchitectureTest` uses ArchUnit to enforce that domain code is free of
framework and outer-layer dependencies, application code does not depend on
adapters/REST/JPA infrastructure, ports do not depend on application/adapters,
and persistence does not own build-ranking/Wilson policy. Input and output ports use only
JDK/domain/port types. Inbound adapters invoke input ports, with only seven exact semantic
application error types allowed; they cannot reach application implementations or output
ports. Outbound adapters cannot reach input ports. Negative and positive boundary fixtures
exercise the rules.

Frontend verification uses Vitest and the V8 coverage provider:

```text
cd frontend
npm test
npm run test:coverage
npm run build
```

`npm run test:coverage` prints terminal coverage and writes HTML, JSON, and
LCOV reports under `frontend/coverage/`. Before the explicit reporter configuration
was added, the 2026-09-13 V8 run measured 68.37%
statements, 76.78% branches, 58.51% functions, and 68.37% lines. Frontend tests
cover API/session behavior, build form and sharing helpers, comment pagination, and page-level
interactions including Explore, Compare Builds, Remix, and machine setup/Gadget
Plate feedback. Vitest currently enforces 80% statements, branches and lines, and 65% functions.
The percentages above are historical measurements, not the current baseline.

## Continuous integration

`.github/workflows/ci.yml` runs on pushes and pull requests. The backend job
uses Java 21 with the Maven cache, a disposable PostgreSQL 17 service and generated
temporary JWT keys, and executes `mvn -f backend/pom.xml verify`, which includes
architecture tests. CI supplies its disposable PostgreSQL service through datasource environment
variables. Local database-dependent verification must use a disposable database, never the
normal development database. The frontend job uses Node 22 with the npm
cache, executes `npm ci`, `npm run test:coverage`, and `npm run build`.

Generated build and coverage output remains untracked: `backend/target/`,
`frontend/coverage/`, and `frontend/dist/` are ignored.

## Historical run records

### Explicit input ports — 2026-10-01

Verification used a separate disposable PostgreSQL 17 container and ephemeral test keys.
The normal development database and stack were not used for tests or refreshed for deployment.

- Focused command: `mvn -f backend/pom.xml -Dtest=ArchitectureTest,AuthRestResourceTest,CurrentUserTest,BuildServiceTest,BuildRestResourceTest,BuildResponseAssemblerTest,SavedBuildServiceTest,VoteServiceTest,CommunityExportTest,CommunityRestResourceTest,CommunitySnapshotCacheTest test` — 66 passed.
- Full command: `mvn -f backend/pom.xml verify` — all 364 Surefire tests executed; 363 passed,
  and one comment-list assertion still compared the old output-port page type with the new
  input-port page. The test was corrected to assert the same returned comments, count and
  repository query parameters. No production behavior or test expectation was relaxed.
- Completion command: `mvn -f backend/pom.xml -Dtest=CommentServiceTest verify` — all eight
  affected unit tests and all 18 `PackagedApiIT` tests passed; packaging and coverage checks passed.
  The other 356 passing Surefire tests were not repeated because only this test assertion changed.

The full run includes the new registration/resend mail failure tests: account/token data is
committed and visible in a fresh transaction before delivery, even when mail fails. The existing
password-reset regression now invokes the input port and asserts delivery inside an active
transaction plus rollback of failed token replacement. JWT, Google, recommendations, stats,
community, collection, social features, queries, migration and rate-limit tests also passed.

Coverage was measured after archiving the old execution file, using the full run plus the
targeted correction. The existing 90% instruction / 80% branch / 90% line gates passed without
changes. The report is `backend/target/site/jacoco/index.html`; packaged API tests are separate
from the instrumented Surefire coverage. No frontend test/build was needed: frontend source,
dependencies and API contracts did not change.

Measured coverage: **97.21% instructions, 88.98% branches, 97.68% lines**.

Existing compiler notices remain for deprecated/unchecked test APIs. The packaged test runner
emits an ignored logging-category configuration warning on Windows. These did not fail checks.
Constraint-race tests intentionally exercise and log rejected database operations.

For a manual rerun, use the full command above with `QUARKUS_DATASOURCE_JDBC_URL`,
`QUARKUS_DATASOURCE_USERNAME` and `QUARKUS_DATASOURCE_PASSWORD` pointing to a **disposable**
PostgreSQL database, plus ephemeral `JWT_PUBLIC_KEY` / `JWT_PRIVATE_KEY` and `PUBLIC_BASE_URL`.
In IntelliJ, the architecture, comment, CurrentUser and community resource tests can run without
infrastructure; mail integration tests require the disposable datasource. No broader backend
verification remains pending from this refactor.

### Robustness fixes — 2026-09-13

Targeted backend verification ran HttpRestExceptionMapperTest,
UnexpectedExceptionRestExceptionMapperTest, ParentConstraintTranslationTest,
HttpRobustnessIntegrationTest and ParentDeletionIntegrationTest. The first run
found a missing JSON Content-Type in the logout test request; after correcting
that fixture, all these classes passed in the full run. The coordinated deletion
test covers vote/comment/remix missing-parent failures and rollback of an earlier
write in the same transaction. Unit tests reject translation of unrelated constraints.

Full backend `test`: 145 tests, 144 passed, one ArchitectureTest failure. The
existing BuildDbAdapter candidate projection depends on BuildRanking.Candidate,
which the persistence ranking rule prohibits. That pre-existing conflict is not
changed by the robustness fixes. Acceptance, HTTP robustness, missing-account,
deletion-race, repository and migration tests passed. Packaged `verify` and fresh
coverage analysis were not run; this is not a clean full-verification claim.

Reproduce from the repository root against the disposable test database (never
point these tests at the normal application database):

```powershell
mvn -f backend/pom.xml "-Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/ringlab_robustness_test" "-Dquarkus.datasource.username=ringlab" "-Dquarkus.datasource.password=ringlab" "-Dquarkus.datasource.devservices.enabled=false" test
```

The named test classes can also be run directly in IntelliJ with the same test
datasource configuration. For a targeted Maven run add
`-Dtest=HttpRestExceptionMapperTest,UnexpectedExceptionRestExceptionMapperTest,ParentConstraintTranslationTest,HttpRobustnessIntegrationTest,ParentDeletionIntegrationTest`.

Frontend targeted API/auth/editor tests passed after correcting a jsdom router
harness incompatibility. Full `npm test` passed 77 tests; `npm run build` passed.
Run both from `frontend/`. Tests cover A-to-B edit load failure/forbidden/delay,
401 expiration, retained tokens on network/429/503/500 errors, successful retry,
and Retry-After preservation/display.

The existing local stack was reused and refreshed. Frontend and proxied racers
API returned 200 at http://localhost:5173. Live malformed path UUID, query UUID,
overflowing page and malformed JSON returned sanitized JSON 400; wrong media
type returned sanitized JSON 415. No normal application data was reset.

The 2026-09-13 backend `verify` run failed during Surefire: four assertions failed
across `AcceptanceTest`, `GadgetCatalogIntegrationTest`, and
`GameDataRepositoryIntegrationTest`. Packaged tests did not run; their report on
disk was dated September 12. Catalog counts and Gadget Plate expectations have
since been updated, but those edits have not been rerun.

`ArchitectureTest` passed its JUnit test, evaluating four dependency rules.
The initial frontend `npm test` failed one stale artwork-path assertion. After
correction, `npm run test:coverage` passed all 62 tests and `npm run build` passed.
The subsequent explicit coverage reporter configuration has not yet been rerun.

A fresh backend measurement was blocked when Docker could not bind
`127.0.0.1:55439`: Windows reported "An attempt was made to access a socket in a
way forbidden by its access permissions." No port retry was attempted. Previous
JaCoCo execution data and XML were archived outside the repository to prevent
accumulated sessions from contaminating the next measurement. CI has not run on
GitHub. No clean backend percentage or passing full verification is claimed.

## Remaining verification work

Complete a fresh backend `verify` using a disposable database and temporary JWT
keys as documented in README. Re-run frontend coverage/build with the final
configuration. Add interaction coverage for details voting/comment mutations,
Copy Setup success/failure, editor submission and authentication; current page
coverage leaves several event handlers unexecuted. Review clean backend branch
gaps before adding more tests. Keep coverage reporting informational until a
repeatable successful baseline is established.
