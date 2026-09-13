# Verification

## Current baseline — 2026-09-13

Backend verification uses Java 21 and Maven:

```text
mvn -f backend/pom.xml verify
```

JaCoCo runs as part of `verify` and writes HTML and XML reports under
`backend/target/site/jacoco/`. A clean backend baseline is still pending.
The existing report included accumulated execution sessions, so its percentages
must not be treated as coverage from the current run. Before a fresh measurement,
archive the existing `backend/target/jacoco-quarkus.exec`; both agents must still
append within the same verification run. No coverage threshold is enforced.

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
Plate feedback. The frontend coverage baseline is recorded from the command run
for the current change; like the backend, it has no enforced threshold.

## Continuous integration

`.github/workflows/ci.yml` runs on pushes and pull requests. The backend job
uses Java 21 with the Maven cache, a disposable PostgreSQL 17 service and generated
temporary JWT keys, and executes `mvn -f backend/pom.xml verify`, which includes
architecture tests. Surefire uses Dev Services; the packaged application receives
the service database through production environment variables. The frontend job uses Node 22 with the npm
cache, executes `npm ci`, `npm run test:coverage`, and `npm run build`.

Generated build and coverage output remains untracked: `backend/target/`,
`frontend/coverage/`, and `frontend/dist/` are ignored.

## Current run status

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
