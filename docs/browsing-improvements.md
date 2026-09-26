# Browsing improvements — 2026-09-26

Work starts from the clean current checkout. No commits, pushes, deployments,
production writes, schema changes, or new dependencies are authorized here.

## Ordered batches

1. Shared two-build comparison selection and inline tray; independent card links.
2. Shared type badges, compact base-stat explanation, provenance review, snapshot age.
3. Explicit stock-machine part-stat disclosure using the loaded catalog.
4. Optional page-level stats enrichment and ordinary-card consumption; measurements.

Each batch receives focused tests before the next begins. Final checks include
frontend coverage/build, isolated-database backend verify, architecture and packaged
API tests, diff hygiene, and actual responsive browser inspection.

## Baseline

- Frontend: 23 files / 173 tests passed; statement/line 89.45%, branch 89.22%,
  function 85.34%; existing gates passed.
- Backend baseline uses disposable `ringlab_browse_baseline_20260926` on the existing
  loopback PostgreSQL container. Normal development data is not used for tests.
- Browser baseline: local Vite app; screenshot under
  `backend/target/browse-evidence/baseline-desktop.png` (untracked evidence).
- Measurements and final verification are recorded below as completed.

## Progress

- Batch 1 complete: shared provider above routes, inline tray, semantic article/title
  links, ID-based max-two selection, availability checks with distinct 404/network
  messages, abort on selection/navigation changes. No session storage (selection
  lasts for the app lifetime, including route changes; reload clears it).
  Focused frontend tests: 37 passed. Browser: keyboard toggle and two-item tray checked.
- Backend baseline: 30 relevant unit/REST/integration tests passed in the isolated DB.
- Batch 2 complete except demo labeling, blocked as explained below. Shared accessible
  badges cover cards, details, comparison and collection. Compact native disclosures
  explain the existing bars. Snapshot age uses its real timestamp with one 30-second
  timer and an exact-time disclosure. Focused tests: 47 passed (one jsdom-native-keyboard
  assertion corrected to activation; Enter was separately verified in the real browser).
- Batch 3 complete: inline disclosure with explicit trigger/close, Escape within the
  active card, conditional focus return, unmounted hidden content, ordered slots,
  board handling, actual patch and canonical machine total. Focused tests: 11 passed.
  Browser: Enter/open, real part totals, Escape/trigger focus checked.
- Batch 4 implemented: optional page enrichment and ordinary-card consumption. Focused
  backend tests: 17 passed; isolated ranking/measurement run: 9 passed; frontend stats,
  snapshot and stale-response tests passed. Final verification is complete below.

## Files by batch

Shared files have changes from more than one batch; no Git commits were created.

1. Comparison: `frontend/src/BuildComparison.tsx` and its tests, `main.tsx`,
   `components.tsx`, `pages/Explore.tsx`, `pages/CompareBuilds.tsx`, `styles.css`.
   Tests cover shared Top 3/ordinary selection, ID deduplication versus equal titles,
   keyboard toggling, two-item limit feedback, clearing, filter/page/snapshot/route
   persistence, links and browse origin, distinct 404/network failures, retry and
   cancellation. The browser checked keyboard selection, visible limit feedback,
   actual comparison URL and return navigation. Reload persistence is intentionally absent.
2. Clarity: `RacingTypeBadge.tsx`, `CardStats.tsx`, `SnapshotTime.tsx`,
   `CardClarity.test.tsx`, `TopCommunityBuilds.tsx` and its tests, `BaseStats.tsx`,
   `components.tsx`, `pages/BuildDetails.tsx`, `pages/CompareBuilds.tsx`,
   `pages/GameData.tsx`, `styles.css`. Tests cover the exact reused icons, full accessible
   names, null types, decimal/zero/unknown presentation, native disclosure activation,
   no heuristic demo labels, timestamp boundaries/skew/invalid values, cached timestamps
   and timer cleanup. Native Enter activation and focus visibility were checked in the
   browser. Demo labeling is blocked by provenance, not silently approximated.
3. Machine disclosure: `StockMachineCard.tsx` and its tests, `pages/GameData.tsx`
   and its tests, `styles.css`. Tests cover Enter/Space/click, explicit close/Escape,
   conditional focus return, no hidden controls, pointer-leave persistence, slot order,
   Boost without tires, canonical machine-only totals, patch/partial/unavailable/error/
   loading states, and no catalog reload on open. Browser checks cover keyboard and
   click activation, focus return, scrolling, and phone/narrow viewport bounds. Collection
   tabs now wrap; narrow machine artwork/copy stack to eliminate observed overflow.
4. Page stats: `backend/.../application/gamedata/BaseStatsService.java`,
   `adapter/in/rest/build/BuildRestResource.java`, `build/response/BuildPageResponse.java`;
   tests in `BaseStatsServiceTest`, `BuildRestResourceTest`, `BuildRankingIntegrationTest`,
   `ApiContract` (also inherited by packaged tests), and `performance/BuildPageStatsBenchmark`.
   Frontend: `types.ts`, `CardStats.tsx`, `components.tsx`, `pages/Explore.tsx`,
   `PageCardStats.test.tsx`, `TopCommunityBuilds.test.tsx`. Tests cover canonical equality,
   mixed patches, partial/missing values, zero, boards, versionless/empty pages, call
   bounds, independent page lifetimes, default JSON shape, safe optional failure,
   ranking/count/exclusion/page stability, no per-card requests and stale-query rejection.
   Browser checks confirmed rendered ordinary stats, 12 cards, and no Top 3/lower-grid
   ID overlap. Request/query measurements are from the isolated API benchmark below.

API/UI documentation: `README.md`, `docs/architecture.md`, and this progress/report file.

## Additive API contract

`GET /api/builds` accepts optional `includeStats` (boolean, default `false`).
Without it, `items`, `total`, `page`, and `size` are unchanged and no stats-map reads
are added. With it, successful responses add `statsByBuildId`, an object keyed by
returned build UUID. Each value has exactly the `/api/stats/build` shape: five
nullable numeric totals plus `character` and `machine` objects with five nullable
fields each. Empty pages return `{}`. Versionless/missing-stat builds return explicit
unknowns; they never borrow another patch. Partial fields and numeric precision are
preserved. There are no extra reference lookups to compute these values.

If optional enrichment fails, the response remains a usable HTTP 200 build page:
`statsByBuildId` is omitted and `statsError` contains a safe message. This does not
hide errors in the primary listing itself. Explore/My Builds show a page-level retry
and never fall back to individual card requests. Missing enrichment, pending data,
unavailable values and failure have distinct UI states. `useLoad` continues to bind
data to the complete request path and abort obsolete requests. The original preview,
single-build and Top 3 contracts are unchanged.

## Measurement conditions

`BuildPageStatsBenchmark` runs only when explicitly named. It refuses any database
whose name does not begin `ringlab_browse_`, creates its own 36 builds, and removes
only its own fixture rows in `finally`. The same deterministic fixture is used before
and after. Each scenario has 12 cards: one patch; two patches alternating; or four
current-patch, four empty-patch and four versionless builds. One warm-up and three
measured runs; reported times are medians of sequential local HTTP acquisition,
including JSON parsing. They are not browser rendering or concurrent-request timings.
Hibernate counts prepared SQL statements, and its native-query execution counters
measure racer/part stat-map reads (each repository map read executes that query once).
The list's pre-existing build/catalog/author queries are reported separately.

| Before | Stats HTTP | Total HTTP | Racer / part map queries | List SQL | Total SQL | Median ms |
|---|---:|---:|---:|---:|---:|---:|
| One patch | 12 | 13 | 12 / 12 | 11 | 95 | 512.599 |
| Two patches | 12 | 13 | 12 / 12 | 12 | 96 | 409.845 |
| Missing / versionless | 8 | 9 | 8 / 8 | 12 | 68 | 321.249 |

| After | Stats HTTP | Total HTTP | Racer / part map queries | List SQL including stats | Total SQL | Median ms |
|---|---:|---:|---:|---:|---:|---:|
| One patch | 0 | 1 | 1 / 1 | 13 | 13 | 239.864 |
| Two patches | 0 | 1 | 2 / 2 | 16 | 16 | 199.706 |
| Missing / versionless | 0 | 1 | 2 / 2 | 16 | 16 | 178.134 |

Existing listing SQL remains 11 / 12 / 12 respectively; enrichment adds 2 / 4 / 4.
The missing-stat patch is a real catalog patch with empty maps, so it still requires
two reads; versionless builds require none. Before, each stats request also validated
its references; those repeated lookups disappear from ordinary card acquisition.
Unit tests additionally count port method calls directly, including empty pages and
cross-patch isolation. These measurements show lower sequential API acquisition times
for this fixture. CPU load, JIT and HTTP scheduling can affect the timings; they do not
establish production or browser-render latency improvements.

Logs and screenshots are untracked under `backend/target/browse-*`.

## Demo provenance finding

`backend/scripts/SeedDemoData.ps1` stores generated UUIDs and fingerprints in a local,
untracked `.ringlab-demo-state.json`. The development bootstrap validates a supplied
username/email pair but does not persist a demo marker. Public registration does not
reserve these names. The manifest is environment-specific and is not an API/runtime
source of truth. Therefore neither titles nor `ringlab_demo_` usernames prove demo
provenance in a deployed database. Accurate runtime labels require trusted persisted
provenance and deliberate migration/backfill, outside this task's authorization.
No content is guessed to be demo content and no ranking/eligibility rule is changed.

## Final verification

- `npm run test:coverage -- --maxWorkers=4`: **27 files, 202 tests passed**.
  Coverage: statements/lines **90.09%**, branches **91.42%**, functions **86.47%**.
  All existing gates passed; worker count limits contention, not test coverage.
- `npm run build`: passed after the final responsive/style changes.
- Full Maven `verify` on fresh disposable `ringlab_browse_final_20260926`:
  **248 Surefire tests + 17 packaged API tests**, no failures/errors/skips.
  Includes both `ArchitectureTest` checks. JaCoCo gates passed with instruction
  **97.12%**, branch **86.51%**, line **97.01%**. Earlier execution data was archived
  before this run, so these gates are not based on accumulated baseline coverage.
- `git diff --check`: passed. No generated output is included in the source changes.
- Existing local stack was reused. `http://127.0.0.1:5173/api/builds?includeStats=true&size=1`
  returned HTTP 200 with one build and one stats entry through Vite's proxy. The working
  local UI is `http://127.0.0.1:5173/`. Existing development data and JWT keys were preserved.
- Packaged startup emitted a warning about ignored
  `quarkus.log.category.io.quarkus.level`; tests still passed. Logging configuration
  cleanup is outside this task.

## Browser evidence and limits

Actual in-app Chromium checks used the local development dataset, not production.
The isolated benchmark dataset is separate; its acquisition times are not screenshot
render times. No browser-render timing or network trace is claimed.

| View | Result | Screenshot under `backend/target/browse-evidence/` |
|---|---|---|
| Baseline | Before feature changes; 1200 CSS px at initial browser scale | `baseline-desktop.png` |
| Desktop 1440 CSS px | Hero preserved, card alignment, icons/stats/actions | `final-desktop-1440.png` |
| Tablet 768 CSS px | Reflow, actual comparison and preserved return selection | `tablet-explore-768.png`, `tablet-compare-768.png` |
| Phone 390 CSS px | Inline tray, visible two-build limit, long titles and focus | `mobile-tray-390.png` |
| Phone 390 CSS px | Scrollable part contributions and machine-only total | `mobile-machine-390.png` |
| Narrow 320 CSS px | Tray and machine card fit; no horizontal document overflow | `narrow-tray-320.png`, `narrow-machine-320.png` |
| Reflow 720 CSS px | Readable layout without horizontal overflow | `reflow-720.png` |

The tool supports explicit viewport dimensions but exposes no browser zoom control.
Zoom-in shortcuts did not change the measured device scale or CSS width. **Actual
200% desktop zoom remains a manual check**; the 720px capture is only a width/reflow
check, not a claim of 200% zoom verification. The temporary viewport override was reset.

The local catalog currently has 52 fully populated racers, and no network/404 failures
were induced in the running development app. Those UI states were verified with focused
React tests; missing/partial/versionless statistics were also exercised against the
isolated database. Physical touchscreen and screen-reader testing were not performed;
real browser click/keyboard behavior and accessible markup were checked.

## Review commands and remaining manual checks

From `frontend`: `npm run test:coverage -- --maxWorkers=4` and `npm run build`.
Focused files can also be run with `npm test -- src/BuildComparison.test.tsx
src/CardClarity.test.tsx src/StockMachineCard.test.tsx src/PageCardStats.test.tsx`.
The focused pure backend classes can be run directly from IntelliJ's gutter:
`BaseStatsServiceTest`, `BuildRestResourceTest`, and `BaseStatsBreakdownTest`.

The full verification command used from the repository root was:

```powershell
$env:DB_URL='jdbc:postgresql://localhost:5432/ringlab_browse_final_20260926'
$env:DB_USER='ringlab'
$env:DB_PASSWORD='ringlab'
$env:PUBLIC_BASE_URL='http://localhost:5173'
mvn -f backend/pom.xml '-Dmaven.repo.local=C:/Users/Alex/.m2/repository' '-Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/ringlab_browse_final_20260926' '-Dquarkus.datasource.username=ringlab' '-Dquarkus.datasource.password=ringlab' '-Dquarkus.datasource.devservices.enabled=false' verify
```

Use only an isolated local test database for a rerun. The database above was created
specifically for this task; it is not the normal `ringlab` development database.
Coverage HTML: `backend/target/site/jacoco/index.html` and `frontend/coverage/index.html`.
The benchmark is rerunnable on `ringlab_browse_baseline_20260926` with the same database
flags and `-Dtest=BuildPageStatsBenchmark -Dringlab.benchmark.includeStats=true test`.
Its false mode retains the old per-card acquisition path for comparison.

Remaining manual UI checks: use a normal desktop browser at actual 200% zoom, and
optionally test touch/screen-reader behavior. Accurate demo badges require a separate
provenance decision and authorized migration/backfill; no such work was performed.

## Suggested logical commits

1. `feat: add shared two-build comparison tray`
2. `feat: clarify build badges stats and snapshot freshness`
3. `feat: add accessible machine part stats disclosure`
4. `perf: batch base stats for paginated build cards`

Shared files require selective staging if these are split into four commits.
No commit, push, or deployment has been performed.
