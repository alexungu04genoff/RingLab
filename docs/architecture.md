# Architecture

## One modular monolith

RingLab has one Quarkus process and one PostgreSQL database. Features are packages within the same deployment, not network services. The React client calls REST. This keeps local startup, transactions, and thesis explanation small while keeping feature responsibilities visible.

Packages are architecture-first under `dev.ringlab`: `domain`, `application`, `port`, and `adapter`. Feature-specific domain code is grouped by `auth`, `gamedata`, `build`, `vote`, `comment`, and `news`; application code currently has `auth`, `build`, `vote`, `comment`, and `news` services. Outbound contracts are centralized for immediate visibility. Semantic application exceptions derive from `application.AppException`; `adapter.in.rest.ErrorRestExceptionMapper` alone maps them to HTTP statuses. The JWT current-user helper lives in `adapter.in.rest.auth`. There are no generic base repositories or services, event bus, or framework for future games.

```text
dev.ringlab/
  domain/{auth,build,comment,gamedata,news,vote}/
  application/{auth,build,comment,news,validation,vote}/
  port/out/
    {User,Build,Comment,GameData,GameNews,Vote}Repository.java
  adapter/in/rest/{auth,build,comment,gamedata,news,ratelimit,vote}/
    request/ and response/
  adapter/out/db/{auth,build,comment,gamedata,vote}/
  adapter/out/steam/
```

## Boundaries

- **domain:** immutable Java records (`User`, `Racer`, `Machine`, `MachinePart`, `Gadget`, `GameVersion`, `Build`, `Vote`, `Comment`), the `RacingType` and `MachinePartType` enums, and the pure `GadgetPlate` placement validator, using only the JDK. `Racer` and `Machine` carry `RacingType` and image path; `Gadget` carries its description, nullable latest verified slot cost, and image path. `Build` snapshots its ordered gadget list. `Vote` permits only −1 and +1. Domain code imports no Quarkus, REST, Hibernate, or JPA types.
- **application:** use-case services that depend on domain types and outbound repository contracts. Services validate build references, enforce author ownership, normalize accounts, and orchestrate mutations. Expected failures use semantic application exceptions for validation, authentication, existing resources, forbidden operations, missing resources, and unavailable external services; this layer stores no HTTP status codes. CDI and transaction annotations are pragmatic application-layer dependencies. AuthService uses Quarkus's bcrypt utility directly because a second hashing abstraction would not serve a current implementation need.
- **port/out:** the flat set of outbound infrastructure contracts: `UserRepository`, `BuildRepository`, `CommentRepository`, `GameDataRepository`, `GameNewsRepository`, and `VoteRepository`. Centralizing this small set makes every application-to-infrastructure boundary visible in one package. These interfaces depend only on domain types and JDK types.
- **adapter/in/rest:** route/role annotations and conversion into service calls. Feature-specific transport records live in `request/` and `response/` subpackages beside their REST resource: for example, `build/request/BuildRequest` and `build/response/BuildResponse`. Entry points end in `RestResource`, including `AuthRestResource`, `BuildRestResource`, `CommentRestResource`, `CommentDeletionRestResource`, `GameDataRestResource`, `NewsRestResource`, and `VoteRestResource`. REST does not return JPA entities or password hashes. CurrentUser parses the verified JWT subject as a UUID, verifies that the RingLab user still exists, and treats a missing account as unavailable authentication. The `ratelimit` package owns application-level HTTP throttling and does not leak it into business services. Global error mapping remains directly under `adapter.in.rest` because it is shared rather than feature-specific, and this adapter owns the HTTP status assigned to each semantic application exception.
- **adapter/out/db:** JPA entities named `*DbEntity`, persistence implementations named `*DbAdapter`, and MapStruct interfaces named `*DbMapper`. For example, `BuildDbAdapter` implements `BuildRepository` and uses `BuildDbMapper` with `BuildDbEntity`. Panache repositories remain where concise and EntityManager queries where more explicit. Only adapters know table names and PostgreSQL upsert syntax.

The outbound repositories are real boundaries between application behavior and infrastructure. No input ports are currently used because REST resources call application services directly. Read-only game-data routes use `GameDataRepository` directly because they have no additional use-case rules.

Usernames, build titles and descriptions, and comment text pass through a basic local profanity
policy before application services persist them. The policy loads a small classpath word list,
applies Unicode normalization and case folding, and recognizes simple punctuation- or
space-separated spellings with word boundaries. Application services are authoritative; this
intentionally limited check is not a comprehensive moderation system.

## HTTP rate limiting

`RateLimitFilter` is an inbound REST filter. It selects one of a small fixed set of endpoint
policies and consumes from an in-process token bucket before invoking a resource. Public reads,
build browsing, news, login, Google login and registration use client-IP identities. Build
creation, comment creation, vote mutation and other authenticated mutations use the verified JWT
subject (the RingLab user UUID); requests without a verified user safely fall back to an IP key.
The initial policies are respectively 120/minute, 60/minute, 30/minute, 10/minute, 10/minute,
5/hour, 10/minute, 10/minute, 60/minute and 30/minute. Configuration uses one
`capacity/ISO-8601-duration` property per policy.

The limiter stores buckets in a synchronized, access-ordered in-memory map. Token refill is based
on elapsed time, so a full bucket permits a reasonable burst rather than imposing fixed spacing.
Idle buckets expire after two hours, cleanup runs at most once per minute, and a hard 10,000-bucket
cap evicts the least recently accessed identity when necessary. A rejection returns HTTP 429,
the existing safe `{message}` JSON shape, and a whole-second `Retry-After` value. Normal validation,
authorization and application errors remain unchanged for requests that the limiter admits.

Client IP resolution ignores `X-Forwarded-For`. It uses the immediate peer unless
`TRUST_CLOUDFLARE_CLIENT_IP=true`, the peer is loopback, and `CF-Connecting-IP` contains one valid IP
literal. This narrowly supports the documented local Cloudflare Quick Tunnel -> Vite -> Quarkus
path, where Vite passes the edge-provided header through. It assumes the backend remains local-only;
other proxy layouts require their own explicit trust boundary.

This state is per Quarkus process and resets on restart. Horizontal deployment would require a
shared limiter such as Redis or rate limiting at a trusted edge, neither of which is part of the
current single-instance design. Cloudflare/network protection is complementary; the REST filter is
not presented as volumetric DDoS prevention.

Application services own input validity as well as orchestration. AuthService validates private input records with the existing Jakarta Validator, preserving the same username/email/password constraints as REST without depending on REST DTOs. Login preserves legacy credential acceptance, including whitespace passwords, and never trims passwords. BuildService and CommentService explicitly validate text, null inputs and pagination bounds. REST Bean Validation remains an early transport check; no HTTP types flow inward.

Outbound method documentation specifies literal search, any-part source-machine matching, comment/version ordering, vote absence/bulk-summary conventions and atomic replacement. UserDbAdapter translates only PostgreSQL uniqueness violations for the Flyway username/email constraints; unrelated failures reach the safe unexpected-error boundary.

PostgreSQL adapters implement persistence ports; `SteamNewsAdapter` implements the outbound REST port `GameNewsRepository`. This demonstrates the same application boundary with two different infrastructure types. Steam HTTP details and JSON records stay in `adapter/out/steam`; the application only requests five latest domain news items.

```text
React -> RingLab REST -> GameNewsService -> GameNewsRepository
                                       <- SteamNewsAdapter -> Steam Web API
```

`GET /api/news` returns an array of `{id, title, url, publishedAt}` response DTOs. The typed Quarkus REST Client uses the public Steam News v2 endpoint, with configurable `STEAM_BASE_URL` and `STEAM_APP_ID` (default 2486820), 2-second connection and 3-second read timeouts. Network, HTTP, and malformed-response failures become a semantic external-service-unavailable error, which the REST mapper exposes as the safe 503 `News unavailable` response; logs omit external bodies. No retries, caching, persistence, or synchronization are used. Explore loads news independently and shows a secondary panel with titles, dates, and original links, stacking below builds on smaller screens. It omits article contents entirely, so HTML/BBCode is never rendered; empty and unavailable news leave build browsing usable.

`vote` and `comment` call BuildService to verify that their target build exists. Build responses use auth and game-data queries to assemble public author/loadout details. Build detail responses obtain their vote summary through VoteService; list responses reuse page vote summaries supplied by BuildService after ranking. These are in-process calls. The build response currently reuses the game-data response DTO; this deliberate coupling keeps the same public shape without a parallel mapper hierarchy.

## Mapping and flow

Google sign-in uses the same RingLab session boundary as local login:

```text
Google Identity Services -> Google ID token -> POST /api/auth/google
  -> ExternalAuthService -> ExternalIdentityVerifier
                         <- GoogleIdentityVerificationAdapter (Google Java verifier)
  -> UserRepository + ExternalIdentityRepository -> RingLab JWT -> existing sessionStorage flow
```

The Google adapter verifies signature, issuer, configured audience, expiry and
required identity claims, then supplies `VerifiedExternalIdentity` to application
logic. Google token details remain outside the account model. The application
resolves returning users by provider and subject; email is used only for initial
account creation and conflict detection, never implicit linking. First-time users
receive an ASCII username derived from the email local-part, capped at 21 characters
to leave room for an underscore and eight random hexadecimal characters on collision.
Short or non-ASCII local-parts receive a `user_` prefix. Both account and identity
inserts share one transaction; uniqueness races roll back and return a conflict.

Flyway V14 makes `users.password_hash` nullable and creates `external_identities`
with a composite primary key `(provider, provider_subject)`, a user foreign key with
`ON DELETE CASCADE`, and creation time. Provider is a string so additional providers
do not need a schema enum migration. External-only users have no usable password;
local login still performs the dummy bcrypt check and rejects a null hash. The
ordinary `SessionResponse` and JWT issuer, role, lifetime and ownership semantics
are shared. RingLab never receives Google's password and stores no Google tokens.
Linking accounts, adding passwords and Google API authorization are outside scope.

`GOOGLE_CLIENT_ID` and frontend `VITE_GOOGLE_CLIENT_ID` must identify the same Web
client. Missing configuration disables Google sign-in without disabling local auth.
The frontend uses GIS's official button and JavaScript callback, then submits JSON
through the existing API layer; this is not Google's form/redirect login flow.
The endpoint accepts JSON, and cross-origin requests remain governed by the existing
CORS allowlist. Successful login explicitly accepts a RingLab token in the browser;
there is no authentication cookie established by a cross-site form submission.

MapStruct generates entity/domain mappings for users, builds, comments, and the five game-data entity types. It removes repeated field copying and keeps ORM records out of the domain. Persistence mappers use strict unmapped-target checking, so adding a target property requires an explicit mapping decision. Vote persistence writes its small validated record directly with an upsert, so it has no mapper. REST mappings are explicit where they are small or require assembling multiple module results.

`GameDataDbAdapter` and `GameDataDbMapper` remain combined for racers, source machines, machine parts, gadgets, and game versions, with strongly typed list/find methods in `GameDataRepository`. `findMachine` resolves part source metadata. The REST layer uses explicit `RacerResponse`, `MachineResponse`, `MachinePartResponse`, and `GadgetResponse` DTOs. `GET /api/machine-parts` returns each part's ID, type, source-machine ID/name, nullable source-machine image path, and source racing type. The image path comes from `Machine`; artwork is not duplicated into `machine_parts`. Build requests use `frontPartId`, `rearPartId`, and `tirePartId`; responses include the corresponding part DTOs. The dynamic build-filter query uses JPA Criteria.

Create-build flow:

```text
React form → POST /api/builds with bearer JWT
  → Quarkus verifies signature, issuer, expiry, and role
  → BuildRequest Bean Validation
  → BuildService.create(actor UUID, Draft)
  → GameDataRepository validates IDs independently
  → Build domain record → BuildRepository → MapStruct → JPA
  → transaction commit in PostgreSQL
  → response DTO with public author, loadout, timestamps, score
```

A build may hold one nullable `remixedFromBuildId`: a lightweight parent reference recording
which existing configuration it was derived from. Remix creation copies configuration into a new,
independent build and validates it through the normal create path. The self-referencing foreign key
uses `ON DELETE SET NULL`, so deleting the source never deletes its remixes.
BuildService resolves optional provenance without requiring the source to still exist: a concurrently deleted source produces `remixedFrom: null` during REST assembly. The requested build itself still uses the normal required lookup and 404 behavior.

Edit/delete flows load the existing record and compare the authenticated actor to the stored author before any write. The client cannot set author IDs. Comment deletion uses the comment author, including when the build belongs to somebody else. Service transactions enclose each mutation; database foreign keys and unique/check constraints remain the final consistency boundary.

## Relational design

`users`, `racers`, `machines`, `machine_parts`, `gadgets`, `builds`, `votes`, and `comments` use UUID primary keys. `build_gadgets` uses `(build_id, position)` as its primary key: it is an ordered collection entry, not an independently addressable entity. Foreign keys enforce valid references. Build deletion cascades its collection, votes, and comments. User deletion is not implemented.

`Machine` is catalog/source-machine metadata. The framework-independent `MachinePart` record represents a FRONT, REAR, or TIRE component originating from a source machine; `MachinePartType` is an enum persisted as a string. `Build` composes exactly one FRONT + one REAR + one TIRE, alongside one racer. Required part IDs and foreign keys preserve references; BuildService enforces the slot types and rejects unknown IDs with validation exceptions, mapped to 400 by REST. Parts can come from different machines. Gadgets remain an independent ordered configuration mapped through `build_gadgets`; their stored order is presentation-only. BuildService rejects duplicate or unknown gadgets and unknown/out-of-range current costs, then uses `GadgetPlate` to determine whether some order-independent assignment fits two rows of three slots. No row assignment is stored or exposed, and gadget costs are not patch-aware.

Explore's compact “Uses parts from” filter retains the `machineId` query parameter as a source-machine ID. It matches front OR rear OR tire source when retrieving candidates, composing with racer, literal search and author. BuildService ranks and paginates those candidates and derives the total from their count.

Votes have a unique `(user_id, build_id)` constraint and a −1/+1 check. PostgreSQL `INSERT … ON CONFLICT … DO UPDATE` makes repeated/concurrent votes update the same row atomically. A single aggregate query per requested build counts upvotes and downvotes; `VoteSummary` derives score as upvotes minus downvotes. No client-owned or cached counters exist. Build and vote responses expose `score`, `upvotes`, and `downvotes`; vote responses also retain `myVote`.

Comment listings are paginated in PostgreSQL in deterministic `createdAt`, then `id` order. The REST response includes `items`, `total`, `page`, and `size`, allowing the client to navigate page boundaries without inferring them from the number of returned comments.

Browse queries filter candidates in PostgreSQL through `BuildDbAdapter` and its JPA Criteria predicate helper. Search uses `locate(lower(title), lowercasedSearch)`, so user input remains a literal substring rather than SQL or `LIKE` wildcard syntax. The adapter projects only `BuildRanking.Candidate(id, createdAt)` for every match, without loading full build entities or ordered gadget collections. `VoteDbAdapter` supplies raw upvote/downvote summaries for all candidate IDs when SCORE or BEST_RATED needs them; NEWEST defers its summary query until after pagination. `BuildService` applies the selected canonical domain ranking, paginates globally, bulk-loads only the selected page through `BuildRepository.findAll`, and reconstructs the final list in ranked ID order because persistence result order is unspecified. The maximum page size remains 50 at the REST boundary. Persistence therefore owns filtering and fact retrieval but no Wilson, sign-bucket, sorting, tie-break, or pagination policy. Collection/detail assembly uses straightforward bounded lookups, so response-enrichment query count grows with page size; batching remains a separately measurable future optimization. There is no optimistic-lock version field: simultaneous edits by the same author use last-write-wins semantics.

Flyway V1 defines schema; V2 defines the supplied game dataset, and V3 assigns stable local artwork paths to the six supplied racers. Hibernate runs schema validation. V6 expands the catalog to 53 racers, 27 source machines and 81 parts, retaining existing IDs and adding official local artwork paths. V10 fills the known racer and machine types from the user-approved Sonic Wiki catalog and expands the released machine inventory to 63 source machines and 189 parts. No fake application users enter migrations.

V7 expands gadgets to 71 rows (67 substantiated retail identities plus four retained seed identities
requiring review), preserving all existing IDs and ordered selections. Only supported descriptions,
latest verified costs and official local images are populated; unknown fields remain null.
Game Collection displays optional effects and costs without inventing defaults. `Gadget.slotCost`
is current catalog metadata, not a version-aware rule: historical changes and evidence conflicts
live in the source ledger. Gadget Plate capacity validation uses only that current cost; mode and
patch-aware validation are not implemented. The complete
retail roster and most individual costs remain evidence gaps; the catalog is not a complete rule set.

V8 fills the remaining current machine and gadget image paths with local Sonic Wiki artwork after
the project owner explicitly approved that community source. It changes presentation artwork only;
all release-status, name, effect, cost and rule evidence remains governed by the first-party ledger.

V9 adopts the Sonic Wiki as the sole local presentation-artwork source for racers, machines and
gadgets, including Blinky's previously-null path. Stable `/assets/...` paths remain unchanged for
existing records. This visual-source decision does not alter the first-party evidence policy for
catalog facts or add game rules.

V10 uses the project owner's explicit Sonic Wiki source policy for catalog facts. It fills the
stored racer types, adds 36 released stock machines that the earlier first-party-only audit could
not name, and creates one FRONT, REAR and TIRE row for each. It also fills descriptions and latest
plate costs for the stored gadgets. V11 then removes unreleased catalog entries and their associated
Festival gadget; V12 adds Wiki artwork for every remaining released machine. Character and part
statistics are deliberately not added in these migrations;
the implemented and proposed relational shapes are documented in `docs/game-data-schema.md`.

V6 makes racer/machine racing types nullable when authoritative data is unavailable. The domain and persistence still use the five-value `RacingType` enum, persisted as strings. V10 fills the types supported by the approved Wiki audit. [The catalog ledger](game-data-sources.md) records released entries, provenance, artwork gaps, skins and future exclusions. Each new source machine gets one FRONT, REAR and TIRE under RingLab's existing composition model, without asserting additional in-game compatibility rules.

V4 creates three explicitly identified parts per seeded machine, backfills old builds to the three parts from their former machine, makes all three columns required, then removes `builds.machine_id`. The transactional migration fails if any old build cannot be mapped. Build IDs, timestamps, ordered gadgets, votes, and comments are preserved; V1–V3 remain unchanged.

Build ranking is a domain/application policy. The REST adapter translates the public `newest`, `score`, and `rated` query values into typed `BuildSort` values. `BuildRanking` defines every ordering: newest uses creation time descending then UUID ascending; score uses raw score descending followed by those ties; best rated first groups builds by raw-score sign (positive, zero, negative), then uses Wilson lower bound, raw score, creation time, and UUID. `WilsonScore` is the single pure-Java canonical implementation using z = 1.96 (approximately 95% confidence), including the zero-vote case. Sample size therefore matters: 40 upvotes and 1 downvote rank above 3 upvotes and no downvotes. Persistence adapters expose builds and raw vote facts only; they do not know Wilson or interpret ranking modes. Ranking happens before pagination in `BuildService`. A future file-based, MongoDB, or other persistence adapter can therefore implement the ports without knowing any ranking rule. Wilson remains internal to ordering; the visible community score is still upvotes minus downvotes, and no ranking values are cached or exposed.

## Frontend

`GameVersion` is persistent game-data catalog metadata (`id`, plain version string, release date).
V5 seeds the four supplied official versions and adds a nullable `Build.gameVersionId` foreign key.
There is no default or backfill: existing builds remain versionless and editing may add, change,
or clear a version. BuildService rejects unknown non-null IDs with a validation exception, mapped
to 400 by REST. The catalog endpoint
`GET /api/game-versions` lists release dates newest first; build responses contain a small nested
`gameVersion` DTO or null. Explore's optional `gameVersionId` predicate filters candidates in PostgreSQL
before application ranking/pagination and composes with existing filters and all three sorts.
Wilson ranking and visible raw scores are unchanged. Steam news remains an independent external
REST adapter; automatic patch extraction and synchronization are intentionally not implemented.

The editor offers an optional Game version / Patch selector; details and cards display selected
versions compactly. Explore offers a Patch filter, and Game Collection lists versions and release
dates. The demo seeder resolves and validates catalog IDs through REST before writing, assigning
a deterministic version mix to new demo builds. Existing build fields, votes, and comments are preserved.

The editor selects Front, Rear, and Tires independently with native selects and visual source-machine previews. A shortcut derives complete stock setups from the loaded part catalog, while each slot remains independently editable. Client-side gadget search filters the available catalog without altering the ordered selected IDs. The editor mirrors the two-row Gadget Plate check for immediate feedback and disables publishing or saving invalid combinations; the backend remains authoritative. Details identify stock or mixed setups, show source-machine artwork for each slot, and safely summarize valid or legacy-invalid Gadget Plates without exposing a row assignment. Current catalog costs are used without interpreting the selected game version. Cards show the stock-machine name when all sources match, otherwise “Mixed machine”. Game Collection explains that stock machines provide all three components. The local demo plan retains 17 legacy examples and adds 43 fan-oriented builds, with recurring author preferences, stock/mixed sources and uneven engagement. Its 60/30/10 main-cast/other-Sonic/guest distribution is a fictional presentation choice, not player statistics. Fixed-seed planning is separate from REST execution and can be previewed offline; timestamps remain server-generated. Reruns reuse author/title identities and add missing interactions without resetting existing votes or build edits.

React Router owns page navigation. A small context holds current authentication; forms and page queries own local state. `api.ts` centralizes the bearer header, JSON handling and errors. `useLoad` aborts stale requests on route/filter changes. Build selection helpers preserve and reorder gadget IDs and mirror the small Gadget Plate placement rule for editor feedback. React's normal text escaping is used for user content.

Session bootstrap clears credentials on 401 only; other failures retain the token and expose a session-check retry. API 429 errors retain a valid Retry-After delay and include it in their displayed message. The editor resets its draft on edit-route changes and permits submission only when the loaded build ID and author match the current route and actor.

At the REST boundary, CurrentUser also verifies that the JWT subject still has a RingLab account; missing accounts receive 401 without changing public author lookup semantics. HttpRestExceptionMapper preserves sanitized framework HTTP errors (including malformed-input 400 and unsupported-media 415); unexpected exceptions retain the generic 500 fallback. Persistence writes translate only SQLSTATE 23503 for `votes_build_id_fkey`, `comments_build_id_fkey`, and `builds_remixed_from_build_id_fkey` into missing-build application errors (404). Explicit flushes keep those failures inside the translation boundary; the enclosing transaction rolls back, and unrelated constraint failures remain unexpected.

Routes cover Explore, My Builds, details, create/edit, register/login, and the requested public catalog browsing. Controls have labels, focus states, pending/error states and responsive layouts. Artwork uses only local `/assets/` paths, with a placeholder on failure. No global-state library or UI framework is used.

Compare Builds is a URL-driven frontend view for exactly two existing builds. It composes the current detail and paginated browse APIs to compare semantic racer, patch, Front/Rear/Tire, ordered gadget, Gadget Plate, and vote fields rather than free-form text; it stores no comparison state on the backend.

## Verification and limits

`AcceptanceTest` uses Quarkus's test runner, HTTP requests, actual JWT registration/login, Flyway, and PostgreSQL. `PackagedApiIT` repeats it against the production artifact. `DomainTest` checks immutable collection and vote/ownership behavior. Frontend tests check ordering and API errors/session expiration. The test run's external-database option requires a disposable database.

`RepositoryContractIntegrationTest` checks actual persisted deletion effects (including surviving remix contents), literal/blank search, any-slot machine matching, comment/version tie-breaks and vote replacement/absence conventions. Deletion postconditions belong to BuildRepository's contract; PostgreSQL retains responsibility for implementing them atomically with its existing constraints.

JWT logout is client-side token disposal; a copied token lasts until its one-hour expiry. There is no refresh/revocation system. Curated/custom artwork, additional gadget compatibility rules, patch-aware costs and calculated stats remain outside the implemented scope; current-cost Gadget Plate capacity validation is implemented.
