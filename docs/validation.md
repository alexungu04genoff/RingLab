# Verification — 2026-09-09

| Check | Result |
|---|---|
| Java 21 compilation and Quarkus production package | Passed |
| JUnit domain tests | 2 passed |
| Quarkus HTTP acceptance tests against PostgreSQL | 10 passed |
| Same HTTP contract against the packaged production application | 10 passed |
| Frontend Vitest tests | 5 passed |
| TypeScript check and Vite production build | Passed |

Backend command: `mvn verify`, with the external-database properties documented in README and explicit production JWT key paths. Frontend commands: `npm test` and `npm run build`.

The machine initially had Java 17 and no Maven or Docker on PATH. Verification used workspace-local Java 21, Maven 3.9.11, and an isolated PostgreSQL 17.6 server on localhost:55432. Downloaded tools, temporary keys, database files, logs and build outputs are ignored by source control. No system-wide Java or PostgreSQL installation was changed. The temporary database was stopped after verification.

Both Flyway migrations ran against real PostgreSQL; later application starts validated and reused the persisted schema. The packaged application ran with the production profile and explicit RSA key files. Tests created their own accounts via registration; production seed migrations contain no users or builds.

The Docker Compose startup path was not executed because Docker is unavailable here. No browser end-to-end or visual QA was performed; frontend tests cover selection order and HTTP/session handling, while backend tests exercise the complete API acceptance contract. A harmless Quarkus integration-test launcher warning about its logging-category argument appeared on Windows; tests and application startup succeeded.
