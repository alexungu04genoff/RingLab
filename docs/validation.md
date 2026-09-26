# Verification

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
and persistence does not own build-ranking/Wilson policy.

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
