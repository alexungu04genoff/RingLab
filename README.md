# RingLab

A small community build-sharing platform for a bachelor's thesis, using **Sonic Racing: CrossWorlds** as its concrete domain. Share a racer, one FRONT machine part, one REAR machine part, one TIRE machine part, and an ordered gadget combination; explore builds, vote, and comment.

## Stack and structure

- Backend: Java 21, Maven, Quarkus 3.27.2, REST/Jackson, Hibernate ORM/Panache, PostgreSQL, Flyway, MapStruct, Bean Validation, SmallRye JWT, bcrypt, JUnit 5.
- Content filtering: ModernMT `com.modernmt.text:profanity-filter:1.0.1` (Apache License 2.0), used locally through RingLab's `ProfanityPolicy`; no moderation network service or repository-owned word list is used.
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
  adapter/in/rest/{auth,build,comment,gamedata,news,ratelimit,vote}/
               REST entry points, current JWT identity and HTTP rate limiting
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

The first start migrates the schema and seeds 52 racers, 62 source machines, 186 machine parts, and 70 gadgets. V6 and V7 establish the release-audited identities; V9 and V12 use user-approved Sonic Wiki artwork consistently for every released catalog machine; V10 fills Wiki-backed racer/machine types, the missing released machine inventory, and current descriptions and costs. V11 removes unreleased catalog entries and their associated Festival gadget. See the [catalog source ledger](docs/game-data-sources.md) and [game-data schema](docs/game-data-schema.md) for sources, known limits, and proposed normalization. There are deliberately **no automatically seeded users or community builds**. Local username/password registration sends a verification link and does not sign the new account in. Verify the address, then log in normally. Google Sign-In accounts use Google's already-verified email identity and are ready immediately.

### Email verification and SMTP

The public `/resend-verification` page is linked from login, registration, and failed verification links. It remains available after a refresh or delivery failure and displays a generic success message without signing the user in.

Local-account verification links contain a URL-safe 256-bit random secret. RingLab stores only its SHA-256 digest; it is single-use, expires after 24 hours, and a resend replaces the old token. Existing accounts are backfilled as verified by Flyway V15, so deployment does not lock them out. Resending always returns the same success message and is limited to 5 attempts per hour per client IP.

Set `PUBLIC_BASE_URL` to the public frontend origin and `MAIL_FROM` to the sender address. Configure SMTP with `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, and `SMTP_TLS`; do not commit credentials. Production also needs the sender domain/DNS configuration required by the chosen SMTP provider. If delivery fails after account creation, the account remains unverified and the user can use resend verification.

Development connection overrides: `DB_URL`, `DB_USER`, `DB_PASSWORD`. `FRONTEND_ORIGIN` defaults to `http://localhost:5173,http://127.0.0.1:5173,https://ringlabgarage.com`. Setting it replaces this comma-separated CORS allowlist; include every frontend origin you intend to allow.

## API rate limiting

The Quarkus REST boundary applies an in-memory token bucket before API calls reach application
services. The initial full bucket allows a normal UI page to make a short burst. Defaults are:

- public reads: 120/minute per client IP
- `GET /api/builds`: 60/minute per client IP
- `GET /api/news`: 30/minute per client IP
- login and Google login: 10/minute per client IP, in separate buckets
- registration: 5/hour per client IP
- resend verification: 5/hour per client IP
- build creation: 10/minute per authenticated user
- comment creation: 10/minute per authenticated user
- vote changes/removal: 60/minute per authenticated user
- other authenticated mutations: 30/minute per authenticated user

An exhausted bucket returns HTTP 429 with `{"message":"Too many requests"}` and a whole-second
`Retry-After` header. Each policy can be adjusted without recompiling through the corresponding
`RATE_LIMIT_*` environment variable in `application.properties`; values use
`capacity/ISO-8601-duration`, for example `60/PT1M`. Idle buckets expire after two hours, and the
in-memory store is also capped at 10,000 identities. Those bounds are configurable through
`RATE_LIMIT_BUCKET_IDLE_TIMEOUT` and `RATE_LIMIT_MAX_BUCKETS`.

By default, the client identity is Quarkus's immediate socket peer; arbitrary `X-Forwarded-For`
values are never trusted. For the local Cloudflare Quick Tunnel path (`cloudflared` -> Vite on
`:5173` -> Quarkus on `:8080`), set `TRUST_CLOUDFLARE_CLIENT_IP=true`. RingLab then accepts the
single `CF-Connecting-IP` value only when Quarkus's immediate peer is loopback. Vite preserves that
incoming header while proxying `/api`. This assumes Quarkus remains local-only behind that Vite
process; a different proxy topology needs an explicit trusted-proxy design.

The buckets belong to one Quarkus process and reset on restart. Multiple application instances
would require shared state or edge rate limiting. Cloudflare/network filtering remains separate;
this feature limits ordinary application-level request flooding and is not volumetric DDoS
protection.

## Optional presentation dataset

With PostgreSQL and the Quarkus development server running, populate an explicitly local demo community from `backend/`:

```powershell
.\scripts\SeedDemoData.ps1
```

The script plans **100 demo members, 20 build authors, 60 builds, 804 votes and 96 comments** on a fresh database. It retains the original 17 examples and adds 43 fan-written entries with recurring author favorites, different machine combinations, uneven attention, and flat conversations including author replies. Demo usernames remain clearly identified; new titles and comments read as community contributions.

The intended racer mix is **60% main Sonic cast** (Sonic, Shadow, Tails, Knuckles, Amy), **30% other Sonic characters**, and **10% guests**. These are fictional editorial weights, not measured player popularity. Regular authors experiment around their favorites; occasional authors contribute one or two builds. Reader selection favors active members and character fans, but affinity changes attention rather than approval. Popular characters can have poorly received experiments, and guests can have well-received builds. Samples include 40/1, 3/0, 1/0, 20/40, tied scores, and unvoted builds.

The fixed-seed plan lives in `backend/scripts/CommunityDemoPlan.ps1`; the runner resolves all referenced catalog names through REST before writing anything. Creation order interleaves authors and characters. Timestamps remain server-generated: the script does not backdate activity or invent historical play sessions. This is manual local tooling, not a migration or an application startup step.

All demo accounts use the local-only password `RingLabDemo!2026`:

- `ringlab_demo_amy` / `ringlab_demo_amy@example.test`
- `ringlab_demo_tails` / `ringlab_demo_tails@example.test`
- `ringlab_demo_shadow` / `ringlab_demo_shadow@example.test`
- `ringlab_demo_sonic` / `ringlab_demo_sonic@example.test`
- `ringlab_demo_knuckles` / `ringlab_demo_knuckles@example.test`
- additional authors: `ringlab_demo_rouge`, `ringlab_demo_cream`, `ringlab_demo_blaze`, `ringlab_demo_silver`, `ringlab_demo_vector`
- new authors: `ringlab_demo_blueblur`, `ringlab_demo_ultimatefan`, `ringlab_demo_rosegrid`, `ringlab_demo_metalhead`, `ringlab_demo_chaotix`, `ringlab_demo_eggman`, `ringlab_demo_bigfan`, `ringlab_demo_phantom`, `ringlab_demo_megafan`, `ringlab_demo_crossover`
- audience accounts: `ringlab_demo_member_01` through `ringlab_demo_member_80`

Repeated runs reuse demo accounts and builds matched by author and title across all pages. Existing build fields and nonzero votes are preserved, and comments are added only when the same author/text pair is absent. Missing planned votes are added, including votes previously removed manually; changed votes are not reset. Renaming a seeded build makes it a different identity, so a rerun will create the original planned title again. Existing activity can therefore make actual counts differ from the plan. No data is deleted. If a demo account has a different password, the script stops rather than replacing it. Pass `-BaseUrl http://localhost:PORT` for another local API port; only loopback HTTP(S) URLs are accepted.

Preview and narrow verification from the repository root:

```powershell
# Returns the complete plan without network access or writes.
$plan = .\backend\scripts\SeedDemoData.ps1 -Preview
$plan.Builds | Group-Object Racer | Sort-Object Count -Descending

# Reads the running local catalog and validates every planned build; no writes.
.\backend\scripts\SeedDemoData.ps1 -ValidateOnly

# Standalone tests with an in-memory REST fake; no services or dependencies required.
.\backend\scripts\SeedDemoData.Tests.ps1
```

## Migrations and persistence

`GET /api/game-versions` lists the persistent patch catalog newest first. Build create/edit requests
accept optional `gameVersionId` (UUID or null); responses include `gameVersion` metadata or null.
Explore's Patch filter sends `gameVersionId` and filters in PostgreSQL before pagination.
V5 seeds 1.4.1 (2026-06-23), 1.3.1 (2026-03-18), 1.2.2 (2025-12-22), and 1.2.0 (2025-12-03).
Existing builds remain versionless; the optional demo seeder assigns a repeatable mix to demo builds.

Flyway is authoritative. `V1__initial_schema.sql` creates the initial tables and constraints; `V2__game_data.sql` inserts the supplied names and racing types with stable UUIDs; `V4__composable_machine_parts.sql` adds machine parts and migrates old single-machine builds to three matching source-machine parts. Hibernate validates the migrated schema; it never creates or drops it.

Add a new numbered migration when changing schema or seed data. Do not edit migrations after they have been applied to a database you intend to keep. Users and builds persist in PostgreSQL, independently of frontend refreshes or application restarts.

## Authentication

### Google sign-in setup

Local username/password authentication remains available. The optional official
Google Identity Services button exchanges a Google ID token at `POST /api/auth/google`
for the same RingLab JWT/session returned by local login. RingLab never receives
your Google password and never stores Google ID/access/refresh tokens.

1. In Google Cloud / Google Auth Platform, configure the consent/branding screen
   and create an OAuth client of type **Web application**. Configure test users if
   your consent configuration is in testing mode.
2. Add `http://localhost:5173` as an authorized JavaScript origin. If you use
   `http://127.0.0.1:5173`, add that origin separately. This popup/callback flow
   does not require a RingLab redirect URI or Google API scopes.
3. Set backend environment variable `GOOGLE_CLIENT_ID` to that Web client ID.
   Set `VITE_GOOGLE_CLIENT_ID` to the **same value** before starting/building Vite
   (for example, in your shell environment). No client secret is needed.
4. Restart the backend and frontend after setting configuration. Use the configured
   origin to open the login or registration page. Without configuration, local
   authentication remains available and Google sign-in is disabled.

The server uses Google's Java `GoogleIdTokenVerifier` with rotating public keys,
the configured audience, accepted Google issuers and expiration checks. It requires
a subject and verified email. It identifies returning users by `(GOOGLE, sub)`,
even if their Google email changes. New users receive a normalized email-derived
username, with a random suffix on collision, and no local password. An existing
email is a conflict: sign in through the existing account; automatic linking and
password creation are not implemented. Concurrent first-login uniqueness conflicts
roll back both inserts and may require retrying sign-in.

Google sign-in requires browser access to `https://accounts.google.com/gsi/client`
and backend access to Google's public certificate endpoint. Use HTTPS outside
localhost and register the actual deployment origins in Google Cloud.

Offline unit tests: `mvn -f backend/pom.xml "-Dtest=AuthServiceTest,ExternalAuthServiceTest,GoogleIdentityVerificationAdapterTest" test`.
Frontend interaction tests: from `frontend`, `npm test -- src/pages/AuthPage.test.tsx`.
Run `GoogleAuthIntegrationTest` manually in IntelliJ, or use
`mvn -f backend/pom.xml -Dtest=GoogleAuthIntegrationTest test` with Docker available.
That test substitutes a verifier and exercises real REST/JWT, migration constraints,
passwordless account persistence and cascade deletion without contacting Google.
The `google-test-*.pem` resources are deliberately public, disposable test fixtures,
never application JWT keys. A final real Google popup smoke test requires your client ID.

See [Google's verification guidance](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token).

Usernames and emails are normalized to lowercase and independently unique. Usernames contain 3–30 ASCII letters, numbers, or underscores. Passwords use bcrypt with its library default cost (10); registration accepts 8–72 characters and rejects passwords exceeding bcrypt's 72-byte UTF-8 limit. Hashes never appear in REST responses.

Application services enforce account and content validity even when called without REST. REST Bean Validation remains for early HTTP feedback. Passwords are never trimmed; login accepts whitespace passwords previously allowed at registration and retains its broader legacy length range. Only username/email uniqueness violations become duplicate-account errors.

Successful local login and Google sign-in return an RSA-signed JWT with the user's UUID as its subject and the `user` role. Local registration returns a message only; verify the email address before logging in. Tokens expire after one hour. Development uses the local ignored key pair generated during setup. Restarting development does not invalidate existing sessions while those keys remain in place.

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

PostgreSQL returns all filtered build candidates and raw vote counts. Domain `BuildRanking` and `WilsonScore` define ranking; `BuildService` ranks globally before pagination and derives the total from the candidate set. BEST_RATED groups positive, neutral and negative scores, then uses Wilson confidence, raw score, creation time descending and UUID ascending. This intentionally loads all matching candidates into memory. Comments remain paginated in PostgreSQL, with creation time ascending then UUID ascending.

Build deletion atomically removes its votes, comments and gadget relations through database cascades. Remixes survive with their parent reference cleared. If a parent disappears during response assembly, optional `remixedFrom` is null; a missing requested build still returns 404. These lifecycle and query guarantees are documented on the outbound ports and covered by `RepositoryContractIntegrationTest`.

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

Artwork lives in `frontend/public/assets/racers/`, `frontend/public/assets/machines/`, and `frontend/public/assets/gadgets/`. V6–V9 assign local paths; V9 uses the user-approved Sonic Wiki as the sole presentation-artwork source. The [source ledger](docs/game-data-sources.md) keeps that presentation choice distinct from first-party catalog evidence. The UI displays an initials placeholder when a local image is absent or fails. New racing types remain unknown until verified; no game statistics are inferred from artwork.

A build contains one racer, exactly one FRONT machine part, one REAR machine part, one TIRE machine part, and ordered gadgets. Each `MachinePart` originates from a source `Machine`; a machine supplies catalog metadata rather than being the build's single selected loadout. Parts may come from different source machines. Gadgets remain separate from machine parts, and their order is persisted with a position column; supported effects and latest verified costs are optional catalog metadata. BuildService rejects duplicate/unknown gadgets and unknown or invalid costs, and validates that the selection fits two rows of three slots using current catalog costs. The UI mirrors this Gadget Plate check for feedback and permits reordering; order is presentation-only and no placement is stored. Historical costs live in the ledger. Patch-aware validation, additional compatibility rules and calculated statistics are intentionally unimplemented.
