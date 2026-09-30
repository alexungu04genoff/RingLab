# Scenario Preview v1 verification

Verified locally on 2026-09-30. See [evidence and supported scope](scenario-preview-evidence.md)
and [architecture](architecture.md). No production access, existing database writes,
community refresh, migration changes, commit, push or deployment occurred.

## Automated checks

- Focused pure backend tests: 33 passed (`ScenarioStatsCalculatorTest`,
  `PassiveStatsServiceTest`, `RateLimitTest`, `ArchitectureTest`).
- Full relevant Maven verification used an isolated backend copy and a fresh native
  PostgreSQL cluster on port 55439, with test HTTP on 8095. The initial run executed
  355 tests: 353 passed; two failed because the isolated copy lacked the existing
  gadget artwork and optimizer JSON fixture. After copying those unchanged inputs,
  only the two affected classes were rerun: all four tests passed. The same
  `verify` continuation passed all 18 packaged API tests and existing coverage checks.
  Thus all 355 unit/integration tests passed across the initial run and bounded retry;
  the initial full command itself was not successful.
- Frontend coverage: 37 files, 347 tests passed. Statements/lines 93.86%, branches
  91.66%, functions 85.86%. ScenarioPreview itself: 100% statements/lines/functions,
  89.28% branches.
- Frontend production build: TypeScript and Vite passed (87 modules).
- `git diff --check`: passed; Git reported only existing line-ending conversion notices.

The full backend run includes passive calculation, Strict/Balanced recommendations
and ownership regressions. No production code in those calculators was changed.
Evolution zero-event/max-stack numerical tests were intentionally not fabricated:
the cap/reset evidence was insufficient. Unsupported-effect tests cover that boundary.

Ignored local artifacts are under `.tools/scenario-verification/`:
`backend-focused.log`, `maven-verify.log`, `maven-verify-followup.log`, and
`backend/target/site/jacoco/index.html`. Frontend logs are
`.tools/scenario-frontend-final-coverage.log` and `.tools/scenario-frontend-build.log`.

## Browser demonstration

An isolated demo app used Vite 5177, backend 8093 and its own
`ringlab_scenario_preview` database. Normal development services on 5173/8080
kept their existing processes. No saved development build was opened or edited.

The demo loadout was Amy Rose, Speedster Lightning Front/Rear/Tires,
Quick Starter, patch 1.4.1. Stat order below is Speed, Acceleration, Handling,
Power, Boost:

1. Passive: **65, 30, 59, 52, 34**; preview initially closed.
2. Preview opened with unspecified lap: known subtotal, condition unknown.
3. Lap 2: **65, 30, 59, 52, 34**, zero scenario adjustment.
4. Lap 1: **85, 50, 79, 72, 54**, verified +20 to each stat.
5. Reset: returns to Passive only and discards the context.
6. Public build responses checked after all interactions: saved selections, description,
   metadata and update time unchanged (accounting for PostgreSQL microsecond storage
   precision in the immediate creation response).

Compare used one shared Lap/Vehicle form control pair for Quick Starter and Sea Dog
demo builds. Lap 1 + Water applied +20 to each build. Changing to Normal kept
Quick Starter active and made Sea Dog inactive under the same context.

The editor preview followed an unsaved racer change to AiAi: passive
**65, 31, 56, 53, 35**, Lap 1 scenario **85, 51, 76, 73, 55**. The original racer
was restored, preview reset and the editor left without saving.

Details, Editor and Compare were visually inspected at desktop and actual measured
360, 390 and 412 CSS-pixel widths. Desktop Compare and Editor were 1440 CSS pixels;
the initial Details desktop capture was 1200. Mobile document widths were respectively
347, 377 and 399 pixels after the vertical scrollbar: no horizontal overflow.
Controls and result text wrapped without overlap. No browser console errors appeared.

Screenshots in `.tools/scenario-verification/`:

- `scenario-desktop.png`, `scenario-360.png`, `scenario-390.png`, `scenario-412.png`
- `scenario-compare-desktop.png`, `scenario-compare-360.png`
- `scenario-editor-desktop.png`, `scenario-editor-360.png`

These are browser previews of raw stat arithmetic, not in-game performance tests.
No effective-cap, lap-time or Evolution accumulation claim follows from them.
