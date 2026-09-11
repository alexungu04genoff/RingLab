# RingLab

A small community build-sharing platform for a bachelor's thesis, using **Sonic Racing: CrossWorlds** as its concrete domain. Share a racer, one FRONT machine part, one REAR machine part, one TIRE machine part, and an ordered gadget combination; explore builds, vote, and comment.

## Stack and structure

- Backend: Java 21, Maven, Quarkus 3.27.2, REST/Jackson, Hibernate ORM/Panache, PostgreSQL, Flyway, MapStruct, Bean Validation, SmallRye JWT, bcrypt, JUnit 5.
- Frontend: React 19, TypeScript, Vite, React Router, plain CSS, Vitest.
- Local infrastructure: PostgreSQL 17 in Docker Compose. Applications run directly on your computer.

```text
backend/src/main/java/dev/ringlab/
  domain/{auth,build,comment,gamedata,news,vote}/
               framework-independent domain records
  application/{auth,build,comment,news,vote}/
               application services
  port/out/
               UserRepository, BuildRepository, CommentRepository,
               GameDataRepository, GameNewsRepository and VoteRepository contracts
  adapter/in/rest/{auth,build,comment,gamedata,news,vote}/
               REST entry points and current JWT identity
               feature-local request/ and response/ DTO packages
  adapter/out/db/{auth,build,comment,gamedata,vote}/
               DbEntity, DbMapper and DbAdapter persistence types
  adapter/out/steam/
  application/AppException.java
backend/src/main/resources/db/migration/
frontend/src/
  pages/       route-level screens
  api.ts       HTTP, bearer token and error handling
  auth.tsx     session context and protected routes
  components.tsx, useLoad.ts, buildForm.ts
docs/architecture.md
```

This is one modular monolith, one database, and a separate browser client. See [architecture](docs/architecture.md) and the [initial implementation plan](docs/implementation-plan.md).

## Prerequisites

Install Java **21**, Maven **3.9+**, Node.js **22.12+** (with npm), and Docker with Compose. Confirm with `java -version`, `mvn -version`, `node --version`, and `docker compose version`.

## Start locally

From the repository root:

```sh
docker compose up -d
```

PostgreSQL listens only on localhost:5432. The local database, username, and password are `ringlab`. These are development credentials. A named volume preserves builds and accounts across restarts. `docker compose down` stops the database without deleting that volume.

In one terminal:

```sh
cd backend
java scripts/GenerateJwtKeys.java .keys
mvn quarkus:dev
```

Generate the development key pair once. It is stored in the ignored `backend/.keys/` directory;
Quarkus uses it to sign local JWTs. Restarting development does not invalidate sessions while the
same local keys remain in place.

In a second terminal:

```sh
cd frontend
npm ci
npm run dev
```

Open **http://localhost:5173**. Vite forwards `/api` to the backend at localhost:8080. Swagger UI is available in development at **http://localhost:8080/q/swagger-ui**; the OpenAPI document is at `/q/openapi`.

The first start migrates the schema, seeds 6 racers, 10 source machines, 30 machine parts, and 20 gadgets, and assigns the racers stable local artwork paths. There are deliberately **no automatically seeded users or community builds**. Register through the UI, then create your first build. Registration signs you in immediately; login is also available separately. Use another browser/session to register a second account and try ownership restrictions.

Development connection overrides: `DB_URL`, `DB_USER`, `DB_PASSWORD`. `FRONTEND_ORIGIN` defaults to `http://localhost:5173`.

## Optional presentation dataset

With PostgreSQL and the Quarkus development server running, populate an explicitly local demo community from `backend/`:

```powershell
.\scripts\SeedDemoData.ps1
```

The script calls the existing REST API and resolves racers, source machines, machine parts, and gadgets by their supplied names. It creates five clearly identified build-owning accounts plus fifty-five voter-only demo audience accounts. This permits 280 uneven votes across 17 varied builds and 30 short comments while preserving the one-vote-per-user-per-build rule. The larger audience provides reference samples for future confidence-aware ranking demonstrations, including 40 up / 1 down, two 20 up / 40 down, 30 up / 5 down, and 3 up / 0 down. This is manual development tooling: it is not a Flyway migration, is not called during application startup, and cannot run automatically in production.

All five build-owner accounts and the voter-only demo accounts use the local-only password `RingLabDemo!2026`:

- `ringlab_demo_amy` / `ringlab_demo_amy@example.test`
- `ringlab_demo_tails` / `ringlab_demo_tails@example.test`
- `ringlab_demo_shadow` / `ringlab_demo_shadow@example.test`
- `ringlab_demo_sonic` / `ringlab_demo_sonic@example.test`
- `ringlab_demo_knuckles` / `ringlab_demo_knuckles@example.test`
- voter-only: `ringlab_demo_rouge`, `ringlab_demo_cream`, `ringlab_demo_blaze`, `ringlab_demo_silver`, `ringlab_demo_vector`
- additional voter-only accounts: `ringlab_demo_member_01` through `ringlab_demo_member_50`

Repeated runs are safe and do not delete data. The script logs into existing demo accounts, reuses builds with the same demo title and author, idempotently sets the intended votes, and adds a comment only when the same demo author and text are not already present. If an existing demo username uses a different password, the script stops clearly rather than modifying or deleting that account. Pass `-BaseUrl http://localhost:PORT` when the development API uses another port; the script refuses non-loopback URLs so it cannot target a remote production deployment.

## Migrations and persistence

Flyway is authoritative. `V1__initial_schema.sql` creates the initial tables and constraints; `V2__game_data.sql` inserts the supplied names and racing types with stable UUIDs; `V4__composable_machine_parts.sql` adds machine parts and migrates old single-machine builds to three matching source-machine parts. Hibernate validates the migrated schema; it never creates or drops it.

Add a new numbered migration when changing schema or seed data. Do not edit migrations after they have been applied to a database you intend to keep. Users and builds persist in PostgreSQL, independently of frontend refreshes or application restarts.

## Authentication

Usernames and emails are normalized to lowercase and independently unique. Usernames contain 3–30 ASCII letters, numbers, or underscores. Passwords use bcrypt with its library default cost (10); registration accepts 8–72 characters and rejects passwords exceeding bcrypt's 72-byte UTF-8 limit. Hashes never appear in REST responses.

Login and registration return an RSA-signed JWT with the user's UUID as its subject and the `user` role. Tokens expire after one hour. Development uses the local ignored key pair generated during setup. Restarting development does not invalidate existing sessions while those keys remain in place.

The client keeps the bearer token in **sessionStorage**, surviving refresh in the same tab. Every authenticated request includes `Authorization: Bearer …`. A 401 clears the client session. Backend role checks protect mutations; application services separately verify resource ownership. Hidden buttons are only a UI convenience.

`POST /api/auth/logout` returns 204 and the client discards its token. This is stateless logout: a copied token remains valid until expiration. There is no refresh token, revocation store, or session table. Browser storage is accessible to same-origin scripts, so keep the app free of untrusted script injection and use HTTPS for deployment. React renders descriptions and comments as plain text.

## Tests

The backend test suite has two complementary layers. Pure unit tests exercise domain and application-service behavior with small in-memory repository doubles, without starting Quarkus or PostgreSQL. Integration and acceptance tests retain the real framework boundaries: Quarkus REST, JWT handling, Hibernate, Flyway, and PostgreSQL.

Run the fast application-service unit tests from the repository root:

```sh
mvn -f backend/pom.xml test -Dtest=BuildServiceTest,VoteServiceTest,CommentServiceTest,AuthServiceTest
```

With Docker running:

```sh
cd backend
mvn test
```

Quarkus Dev Services starts an isolated PostgreSQL test container. The tests run the real Flyway migrations and make HTTP requests using actual registered accounts and JWTs. They cover accounts, ownership, references, ordered gadgets, votes, comments, filters, paging, and cascades. Pure domain and application tests cover business behavior without infrastructure.

You may instead point tests at a **dedicated disposable PostgreSQL database**. Tests insert users/builds and must not target a database containing important data. Quote Maven properties in PowerShell:

```sh
mvn test "-Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/ringlab_test" "-Dquarkus.datasource.username=ringlab" "-Dquarkus.datasource.password=ringlab" "-Dquarkus.datasource.devservices.enabled=false"
```

Frontend checks:

```sh
cd frontend
npm test
npm run build
```

Vitest tests ordered selection and the API client's authorization/error handling. These are not browser end-to-end tests.

Run the complete backend verification to generate JaCoCo HTML and XML coverage reports:

```sh
cd backend
mvn verify
```

Open `backend/target/site/jacoco/index.html` for the HTML report. The XML report is `backend/target/site/jacoco/jacoco.xml`. The Maven JaCoCo agent covers plain JUnit tests, while Quarkus's JaCoCo test extension records classes loaded by `@QuarkusTest`; both append to the same report data. Coverage is used to locate untested domain and application branches; the project does not enforce an artificial percentage threshold.

## Production builds and packaged API tests

```sh
cd backend
mvn package
```

The runnable distribution is the **entire** `backend/target/quarkus-app/` directory. Start it with `java -jar target/quarkus-app/quarkus-run.jar`. A packaged run requires `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_PUBLIC_KEY`, and `JWT_PRIVATE_KEY`. Generate your own keys once:

```sh
java scripts/GenerateJwtKeys.java .keys
```

Example PowerShell configuration, from `backend/`:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/ringlab'
$env:DB_USER = 'ringlab'
$env:DB_PASSWORD = 'ringlab'
$env:JWT_PRIVATE_KEY = (Resolve-Path .keys/private.pem).Path
$env:JWT_PUBLIC_KEY = (Resolve-Path .keys/public.pem).Path
java -jar target/quarkus-app/quarkus-run.jar
```

Keep private keys outside source control; `.keys/` is ignored. Restrict file access and use different credentials/keys outside local development. The generator refuses to overwrite existing keys.

`mvn verify` also runs `PackagedApiIT`, repeating the acceptance suite against the packaged application. Set the above production environment variables to a **disposable test database**, not the development database. The normal test phase uses Dev Services unless overridden; the packaged test phase uses the configured database and signing keys. Do not run another backend on the integration-test port (8081) at the same time.

`npm run build` produces `frontend/dist/`. Serve those static files with SPA fallback to `index.html` and proxy `/api` to Quarkus. `npm run preview` locally previews the built frontend and inherits the configured `/api` proxy to localhost:8080; keep the backend running. Vite's preview server is not a production hosting server. A hosted deployment is outside this local MVP.

## REST overview

All endpoints are under `/api`; request and response bodies are JSON. IDs are UUIDs.

| Method | Path | Access |
|---|---|---|
| POST | `/auth/register`, `/auth/login` | Public |
| GET / POST | `/auth/me` / `/auth/logout` | Signed in |
| GET | `/racers`, `/machines`, `/machine-parts`, `/gadgets` | Public |
| GET | `/news` | Public |
| GET | `/builds`, `/builds/{id}` | Public |
| POST | `/builds` | Signed in |
| PUT / DELETE | `/builds/{id}` | Build author |
| GET / PUT / DELETE | `/builds/{id}/vote` | Signed in |
| GET | `/builds/{id}/comments` | Public |
| POST | `/builds/{id}/comments` | Signed in |
| DELETE | `/comments/{id}` | Comment author |

`GET /api/machine-parts` returns FRONT, REAR, and TIRE components with their source-machine metadata. `Machine` remains source/catalog metadata; machine parts and gadgets are separate systems.

Build listing: `search` (literal case-insensitive title substring), `racerId`, `machineId`, `authorId`, `sort=newest|score|rated`, zero-based `page`, and `size` (1–50, default 12). `machineId` means “uses at least one part sourced from this stock machine.” Response: `{items,total,page,size}`. Ties use creation time then ID. Comments use zero-based `page` and `size` (default 20, max 50), oldest first. Creation currently returns 200 with the resource; deletions return 204 except votes, which return the updated score and current vote.

`GET /api/news` returns the latest Steam news items. Explore loads these independently from build browsing.

Build request:

```json
{
  "title": "My setup",
  "description": "Why I enjoy this combination",
  "racerId": "UUID from GET /api/racers",
  "frontPartId": "UUID from GET /api/machine-parts with type FRONT",
  "rearPartId": "UUID from GET /api/machine-parts with type REAR",
  "tirePartId": "UUID from GET /api/machine-parts with type TIRE",
  "gadgetIds": ["UUID from GET /api/gadgets"]
}
```

Vote body: `{"value":1}` or `{"value":-1}`. Comment body: `{"text":"Nice setup"}`. Business errors return `{message}`; validation errors include Quarkus's `violations` array.

## Assets and intentional scope

Temporary racer portraits for the private university demo live in `frontend/public/assets/racers/`. Their stable filenames and database paths let curated or custom replacements be swapped in later without application-code changes. Future machine or gadget artwork can use the same `/assets/...` database contract in a new migration. The UI displays an initials placeholder when a local image is absent or fails.

A build contains one racer, exactly one FRONT machine part, one REAR machine part, one TIRE machine part, and ordered gadgets. Each `MachinePart` originates from a source `Machine`; a machine supplies catalog metadata rather than being the build's single selected loadout. Parts may come from different source machines. Gadgets remain separate from machine parts, and their order is persisted with a position column; effects and slot costs remain null. There is no gadget capacity or compatibility validation and no calculated statistics. The API preserves the submitted gadget list without guessing combination rules; the UI uses ordinary multi-selection and permits reordering. No additional game systems or community features are included.
