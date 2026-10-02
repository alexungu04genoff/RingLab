# Verification

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
