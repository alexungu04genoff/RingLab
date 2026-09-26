# Backend readability refactor

Historical record of the 2026-09-22 refactor, not verification of later changes.
The subsequent review fixes replace the fixed registration identity with a unique per-test
identity; the reused-database failure below records the original test as it existed then.

Starting revision: `70020d64a061001c127344fe54a5e32f40435dc3`; working tree was clean.
Only the root AGENTS.md applies. No commits, pushes, deployment, schema, tooling,
configuration or frontend changes are part of this task.

## Baseline

Full Maven `verify` passed against the disposable database
`ringlab_refactor_verify_20260922`: Surefire 225, Failsafe 15, no failures/errors/skips.
Architecture checks and existing JaCoCo gates passed. Previous execution data was
archived before the run. Log: `backend/target/refactor-baseline.log` (untracked).
The existing per-user Docker installation and Maven cache require elevated tool
access; neither needed installation or project configuration changes.

## Ordered batches

1. Build draft validation and readable browsing steps. Keep transactions and ownership
   in BuildService; isolate write validation. Callers: service construction in unit tests.
   Risk: validation precedence and page hydration. Tests: BuildServiceTest,
   CommentServiceTest, VoteServiceTest, BuildRestResourceTest.
2. Build REST response assembly. Keep routes/security in the resource and enrichment
   in the REST adapter. Risk: null provenance and vote-query reuse. Tests:
   BuildRestResourceTest plus assembler tests and existing API contracts.
3. Pure stats breakdown and selection-local community assembly. Keep version/reference
   checks in BaseStatsService and selection transactions in CommunitySelectionService.
   Risk: null propagation, batch limits, lookup lifetime. Tests: BaseStatsServiceTest,
   CommunitySelectionTest, CommunitySnapshotCacheTest, CommunityExportTest and domain tests.
4. Private methods for external username selection and Steam item conversion. No new
   security abstractions. Risk: error handling/order. Tests: ExternalAuthServiceTest,
   SteamNewsAdapterTest.
5. Walkthrough/documentation, fresh full verification, diff review and local stack check.

## Progress

- Inventory: reviewed domain, application, ports, REST (including rate limiting),
  persistence and external adapters; inspected existing test coverage and guards.
- Batch 1 complete: draft validation extracted; browsing steps named; use-case tests
  retain all previous assertions and add validation-precedence coverage. Targeted
  BuildServiceTest, CommentServiceTest, VoteServiceTest and BuildRestResourceTest:
  50 passed, no failures/errors/skips. Diff reviewed and whitespace check passed.
- Batch 2 complete: BuildResponseAssembler owns public enrichment; resource routes,
  validation and role annotations are unchanged. Three REST/assembler unit tests
  passed with no failures/errors/skips; diff reviewed and whitespace check passed.
- Batch 3 complete: BaseStatsBreakdown owns pure composition; the selection-local
  CommunitySnapshotAssembler owns catalog enrichment and author/version lookup reuse.
  Stats/community/cache/export/architecture checks: 26 passed, no failures/errors/skips.
  Characterizations cover validation precedence, decimals/unknowns, missing hydration,
  versionless builds, early termination, per-selection cache refresh and remix assembly.
  Diff reviewed and whitespace check passed.
- Batch 4 complete: ExternalAuthService names first-account creation and username
  selection; SteamNewsAdapter names item conversion inside its existing catch boundary.
  Fourteen targeted tests passed, no failures/errors/skips. Diff and whitespace checks passed.
- Walkthrough and affected architecture notes complete; all file links resolve.
- Fresh final verification and local stack checks passed. No implementation or
  verification work remains; the uncommitted working tree is ready for review.
- Preserve the implemented racing-type compatibility and patch-date ranking rules;
  older documentation still refers to machine families and missing coverage gates.

## Existing verification issue

The first final `verify` reused the baseline disposable database. Surefire ran 236
tests: 235 passed, one failed, zero errors/skips. Failsafe and the coverage gate
were not reached. The unchanged
`DemoAccountBootstrapIntegrationTest.normalRegistrationStillReturnsVerificationMessageRatherThanSession`
registers a fixed username without cleanup. The baseline's account persisted
(database timestamp confirms it predates the refactor), so the second registration
returned the correct HTTP 409 instead of the expected HTTP 200. This is a test
isolation defect, not changed registration behavior. No assertion was weakened.

Final verification therefore uses a new empty disposable database,
`ringlab_refactor_final_20260922`, preserving the baseline database and all normal
development data. The failed run is retained in
`backend/target/refactor-final-reused-db.log`; fresh-run coverage starts from an
empty execution-data file. Fixing the fixed test identity/cleanup is separate work.

## Review ledger

| Batch | Production files | Test files |
| --- | --- | --- |
| Build use cases | `application/build/BuildService.java`, new `application/build/BuildDraftValidator.java` | `application/BuildServiceTest.java`, construction updates in `application/CommentServiceTest.java`, `application/VoteServiceTest.java` and `adapter/in/rest/build/BuildRestResourceTest.java` |
| REST assembly | `adapter/in/rest/build/BuildRestResource.java`, new `adapter/in/rest/build/BuildResponseAssembler.java` | `adapter/in/rest/build/BuildRestResourceTest.java`, new `adapter/in/rest/build/BuildResponseAssemblerTest.java` |
| Stats/community | `application/gamedata/BaseStatsService.java`, new `domain/gamedata/BaseStatsBreakdown.java`, `application/community/CommunitySelectionService.java`, new `application/community/CommunitySnapshotAssembler.java`, `application/community/CommunitySnapshot.java`, `adapter/in/rest/gamedata/response/BuildStatsResponse.java` | `application/BaseStatsServiceTest.java`, new `domain/gamedata/BaseStatsBreakdownTest.java`, `application/community/CommunitySelectionTest.java`, `adapter/in/rest/community/CommunityExportTest.java` |
| External flows | `application/auth/ExternalAuthService.java`, `adapter/out/steam/SteamNewsAdapter.java` | `application/ExternalAuthServiceTest.java`, `adapter/out/steam/SteamNewsAdapterTest.java` |

Production paths are relative to `backend/src/main/java/dev/ringlab`; test paths to
`backend/src/test/java/dev/ringlab`. Documentation changes are `docs/architecture.md`,
new `docs/code-walkthrough.md` and this progress/verification record.

Deliberately unchanged: CommentService, VoteService, AuthService, Google identity
verification, mail delivery, rate limiting, all persistence adapters/entities/mappers,
all outbound contracts, BuildRanking/WilsonScore, MachineCompatibility,
MachineComposition, GadgetPlate, BaseStats and CommunitySnapshotCache. Their
responsibilities are already focused; extraction would add navigation or risk.
Existing framework/integration assertions and architecture rules remain intact.

No duplicate business policy was merged: write validity, read eligibility and preview
validity intentionally differ. Canonical arithmetic remains `BaseStats.sum`, breakdown
composition now lives in `BaseStatsBreakdown`, and canonical ranking remains
`BuildRanking`/`WilsonScore`. No performance claim is made.

Checked risks: transaction/role/route annotations remain in place; draft validation
joins the caller's existing transaction; snapshot assembly runs inside select's
REQUIRES_NEW boundary; no mutable lookup maps enter singleton scope; selected-page
vote reuse, unordered bulk hydration, 50-build batches and early termination are
protected by tests. Persistence and exception translation are unchanged.

## Manual review

Open `BuildServiceTest`, `BuildRestResourceTest`, `CommunitySelectionTest`, and
`BaseStatsBreakdownTest` in IntelliJ alongside the classes linked in the walkthrough.
Use the gutter run/debug actions to inspect a use case; no extra infrastructure is
needed for these unit tests. An optional focused rerun from the repository root is:

```powershell
mvn -f backend/pom.xml '-Dtest=BuildServiceTest,BuildRestResourceTest,BuildResponseAssemblerTest,BaseStatsServiceTest,BaseStatsBreakdownTest,CommunitySelectionTest,CommunitySnapshotCacheTest,CommunityExportTest,ExternalAuthServiceTest,SteamNewsAdapterTest,ArchitectureTest' test
```

For a full rerun, use README's packaged-test environment setup and a **new empty
disposable database** until the fixed registration fixture is corrected. The final
run used this command (the database name records that run, not a reusable clean fixture):

```powershell
mvn -f backend/pom.xml '-Dmaven.repo.local=C:/Users/Alex/.m2/repository' '-Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/ringlab_refactor_final_20260922' '-Dquarkus.datasource.username=ringlab' '-Dquarkus.datasource.password=ringlab' '-Dquarkus.datasource.devservices.enabled=false' verify
```

JaCoCo's final HTML is `backend/target/site/jacoco/index.html`; XML is beside it.
Frontend files were not changed and frontend tests were intentionally not rerun.

## Final results — 2026-09-22

| Check | Baseline | Final fresh database |
| --- | --- | --- |
| Surefire | 225 passed | 236 passed |
| Failsafe packaged API | 15 passed | 15 passed |
| Failures / errors / skips | 0 / 0 / 0 | 0 / 0 / 0 |
| ArchitectureTest | 2 passed | 2 passed |
| Instruction coverage | 96.48% | 96.99% |
| Branch coverage | 84.55% | 86.32% |
| Line coverage | 96.63% | 96.81% |

Both full runs passed the unchanged Maven coverage gates: instructions 90%, branches
80%, lines 90%. Each measurement began without previous execution sessions. The
obsolete generated `BaseStatsService$BuildStats.class` was removed from target before
final verification; source records/callers use BaseStatsBreakdown. Final output:
`backend/target/refactor-final.log`. All targeted batches passed (50, 3, 26 and 14
tests respectively). `git diff --check` passed. No formatting/static-check or
architecture rules were disabled or relaxed.

The healthy existing PostgreSQL, Quarkus dev and Vite processes were reused. After
final verification, backend `/api/racers`, frontend `/`, and frontend-proxied
`/api/racers`, `/api/builds?size=1` and `/api/community/top-builds` returned HTTP 200.
Local URL: http://localhost:5173. No data reset, reseeding, key replacement or tunnel
changes were performed.

Remaining warnings: the existing deprecated/unchecked test APIs and packaged-run
`quarkus.log.category.io.quarkus.level` warning remain unchanged. The fixed-username
test isolation issue above needs a separate fix. Gadget effects/historical costs,
bounded response lookup overhead and last-write-wins edits remain existing limitations.
No verification blocker remains; broader manual testing is optional after diff review.

## Follow-up: authentication and Explore readability — 2026-09-26

This section describes the later working-tree changes; the full-suite results above
belong only to the historical refactor.

- `ExternalAccountRegistration` now owns passwordless user creation and username
  selection. `ExternalAuthService` retains the transaction and provider-link policy.
  A verified Google Gmail identity can link to an exact already-verified local email;
  unverified Gmail collisions explain the verification step. Existing accounts keep
  their IDs, passwords and owned content.
- `EmailVerificationService` now owns issuance, hashing, expiry, resend and token
  consumption. Registration still creates the user and token inside one transaction;
  verification/resend retain REQUIRED transactions and mail delivery remains outside.
- `useAuthForm` and `useBuildDraft` separate asynchronous work from page markup and
  ignore abandoned responses. Leaving a remix resets its draft and provenance.
- `SearchableFilter` owns the searchable control; `exploreFilters.ts` owns URL and
  preference rules. Stale patch preferences no longer repeatedly replace the URL,
  and removing the sort chip clears its saved preference.

Verification performed for the latest extraction:

```powershell
mvn -f backend/pom.xml '-Dmaven.repo.local=C:/Users/Alex/.m2/repository' '-Dtest=AuthServiceTest,AuthRestResourceTest,ExternalAuthServiceTest' test
# From frontend:
node node_modules/vitest/vitest.mjs run src/SearchableFilter.test.tsx src/pages/Explore.test.tsx src/pages/ExploreNavigation.test.tsx
node node_modules/typescript/bin/tsc --noEmit
```

Results: 33 backend tests passed, 25 frontend tests passed, and TypeScript checking
passed. The earlier focused auth/editor checks passed 16 backend and 29 frontend
tests; those were separate runs. Existing deprecated/unchecked Java test warnings
remain. All test sources compiled, but integration tests were not executed.

No full suite, packaged API suite, acceptance/persistence integration test, or
coverage analysis was run for these follow-up changes. Run `AcceptanceTest` and
`GoogleAuthIntegrationTest` manually against a disposable configured test database
to verify the real CDI, JWT, transaction and persistence boundaries:

```powershell
mvn -f backend/pom.xml '-Dtest=AcceptanceTest,GoogleAuthIntegrationTest' test
```

The existing local stack was reused; PostgreSQL was healthy and the frontend,
proxied racers/builds/community APIs, and extracted frontend modules returned HTTP
200. Local URL: http://localhost:5173. No source commit, push or deployment was made.
