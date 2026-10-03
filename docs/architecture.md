# Architecture

## One modular monolith

RingLab has one Quarkus process and one PostgreSQL database. Features are packages within the same deployment, not network services. The React client calls REST. This keeps local startup, transactions, and thesis explanation small while keeping feature responsibilities visible.

Packages are architecture-first under `dev.ringlab`: `domain`, `application`, `port`, and `adapter`. Feature packages group domain concepts and application implementations. Both input and output contracts are centralized for immediate visibility. Semantic application exceptions derive from `application.AppException`; `adapter.in.rest.ErrorRestExceptionMapper` alone maps them to HTTP statuses. The JWT current-user helper lives in `adapter.in.rest.auth`. There are no generic base repositories or services, event bus, or framework for future games.

```text
dev.ringlab/
  domain/{auth,build,collection,comment,gamedata,news,vote}/
  application/{auth,build,collection,comment,community,gamedata,news,validation,vote}/
  port/in/                         # capabilities RingLab provides
    *UseCase.java                   # feature contracts, including import and dev-only capabilities
    CommunitySnapshot.java          # detached boundary result
  port/out/
    {User,Build,SavedBuild,Collection,Comment,GameData,GameDataImport,GadgetRule,BaseStats,GameNews,Vote}Repository.java
  adapter/in/catalog/               # explicit CSV import command
  adapter/in/rest/{auth,build,collection,comment,community,gamedata,news,ratelimit,vote}/
    request/ and response/
  adapter/out/db/{auth,build,comment,gamedata,vote}/
  adapter/out/steam/
  adapter/out/{google,mail}/
```

## Boundaries

Game-data maintenance has a separate explicit command: `GameDataImportMain` is
the composition root, wiring `adapter/in/catalog/GameDataImportCommand` and its
concrete CSV reader to `ImportGameDataUseCase`, implemented by
`GameDataImportService`. Its validator/planner receive typed detached
`domain/gamedata/importing` records, never paths. `GameDataImportRepository` is a
flat output port; `GameDataImportDbAdapter` uses JDBC to read a consistent snapshot
or apply a safe plan atomically. Its preparation callback runs application policy
inside the transaction before any write; the DB adapter never depends on input
ports. The command bypasses Quarkus startup entirely, so offline validation needs
no database and no mode invokes Flyway, HTTP or startup observers. Normal REST
queries retain `GameDataRepository`/`BaseStatsRepository` and PostgreSQL. The
canonical files, conservative update policy, approval token and operation commands
are documented in [game-data/README.md](../game-data/README.md).

Rule facts use the same importer and transaction through `GameDataRuleSet` rows.
V34 creates the versioned passive/scenario tables and their ordered source tables,
bootstrapping existing 1.4.1 facts so upgrades preserve results. Normal runtime
loads `GadgetRuleSnapshot` through `GadgetRuleRepository`/`GadgetRuleDbAdapter`;
`ReviewedGadgetRules` owns the current application version selection. Passive,
scenario, community and recommendation callers pass detached facts into pure
calculators. `RecommendationCatalog` includes the same passive/scenario snapshot. JDBC
mapping is shared inside the outbound adapter by `GameDataRuleStorage`; calculators
never reach that storage or CSV. Reviewed-version permissions, the closed passive
additive policy and separate scenario interactions remain Java policy. Source URLs and numeric facts
are relational data; importing a new snapshot does not grant runtime permission.

`ReviewedGadgetRules` synchronizes the first successful load and shares that immutable
snapshot for the application instance. Missing snapshots and repository failures are
not cached. A successful cold load executes six SELECTs: version, ruleset metadata,
passive facts, scenario facts, passive sources and scenario sources. Later snapshot
calls execute no SQL; callers still perform their other catalog/build/stat reads.
Passive previews, nonempty build pages, scenario previews, recommendation catalog
loads and both rule metadata endpoints each request the snapshot once. Community
response projection requests it per entry (at most three); conditional 304 responses
skip that projection. These callers share the same application-scoped rule cache.
Published rule metadata, facts and ordered sources cannot be changed by the importer,
and the snapshot contains no live persistence objects. Application restart refreshes
the cache; changing the reviewed version requires Java policy changes and restart.
Importing a future version neither invalidates existing facts nor enables that version.

Private bookmarks follow the same boundaries: `SavedBuildRestResource` obtains the
actor through `CurrentUser`, calls `SavedBuildUseCase` implemented by `SavedBuildService`, and assembles normal live
build responses with a separate `savedAt`. `SavedBuildRepository` is a flat outbound
port; `SavedBuildDbAdapter` owns PostgreSQL upsert, database pagination, literal
catalog search, and uniqueness/cascade handling. It does not change public ranking
or snapshot models. See [Saved Builds](saved-builds.md) for the contract.

The frontend `SavedBuildsProvider` batches visible status IDs and shares confirmed
state across cards/details. Its inner provider and consumers are keyed by user and
session generation, aborting obsolete requests on replacement. Saved data is not
stored in public snapshots or browser persistence. Comparison remains separate
ephemeral state and survives navigation within a session.

- **domain:** immutable Java records (`User`, `Racer`, `Machine`, `MachinePart`, `Gadget`, `GameVersion`, `Build`, `Vote`, `Comment`), the `RacingType` and `MachinePartType` enums, and the pure `GadgetPlate` placement validator, using only the JDK. `Machine` owns its racing type; `MachineComposition` defines required slots; `Build` snapshots its ordered gadget list. Domain code imports no Quarkus, REST, Hibernate, or JPA types.
- **application:** implements input ports and coordinates domain behavior and output ports. Services validate build references, enforce author ownership, normalize accounts, and orchestrate mutations. Expected failures use semantic application exceptions; this layer stores no HTTP status codes. CDI and transaction annotations are pragmatic dependencies. AuthService uses Quarkus's bcrypt utility directly because a second hashing abstraction would not serve a current implementation need.
- **port/in:** capabilities provided by RingLab to inbound adapters. Contracts use domain/JDK types and boundary-owned commands, queries and results, including `BuildUseCase.Draft`, `Filter`, `Query`, `Page` and `CommunitySnapshot`. They never import application implementations, transport DTOs, JAX-RS, Quarkus or persistence. `BuildUseCase.Filter` is translated explicitly into `BuildRepository.Filter`; output contracts remain independent of input contracts.
- **port/out:** flat contracts for capabilities required from infrastructure: repositories, identity verification and mail senders. Both port packages depend only on JDK/domain/port types.
- **adapter/in/rest:** route/role annotations and conversion into service calls. Feature-specific transport records live in `request/` and `response/` subpackages beside their REST resource: for example, `build/request/BuildRequest` and `build/response/BuildResponse`. Entry points end in `RestResource`, including `AuthRestResource`, `BuildRestResource`, `CommentRestResource`, `CommentDeletionRestResource`, `GameDataRestResource`, `NewsRestResource`, and `VoteRestResource`. REST does not return JPA entities or password hashes. CurrentUser parses the verified JWT subject as a UUID, verifies that the RingLab user still exists, and treats a missing account as unavailable authentication. The `ratelimit` package owns application-level HTTP throttling and does not leak it into business services. Global error mapping remains directly under `adapter.in.rest` because it is shared rather than feature-specific, and this adapter owns the HTTP status assigned to each semantic application exception.
- **adapter/out/db:** JPA entities named `*DbEntity`, persistence implementations named `*DbAdapter`, and MapStruct interfaces named `*DbMapper`. For example, `BuildDbAdapter` implements `BuildRepository` and uses `BuildDbMapper` with `BuildDbEntity`. Panache repositories remain where concise and EntityManager queries where more explicit. Only adapters know table names and PostgreSQL upsert syntax.

`adapter/in` owns protocol-specific entry mechanisms, including REST/JWT/HTTP and the explicit CSV command. It calls
input ports for application work, including simple catalog reads and response enrichment.
`adapter/out` implements infrastructure contracts for PostgreSQL, Steam, Google and mail.
The runtime path is `adapter/in → port/in → application/domain → port/out → adapter/out`.
Java implementation dependencies point inward: services implement input ports and outbound
adapters implement output ports. See the [focused input-port diagram](architecture/inbound-ports.mmd)
and the [atlas](architecture-atlas.md#2-layers-and-dependency-direction).

Ports exist at these boundaries. `BuildDraftValidator`, `ProfanityPolicy`, compatibility
rules, solvers, calculators and loaders deliberately remain concrete. For example,
`BuildRecommendationService` still calls `CollectionService` and `RecommendationCatalogLoader`
directly; `port/in` is not an internal service bus. DTO construction stays in REST.

The architecture guard rejects every inbound dependency on application implementation
packages or `port/out`. Its only application exceptions are the seven exact semantic error
types (`AppException`, `AlreadyExistsException`, `AuthenticationException`,
`ExternalServiceUnavailableException`, `ForbiddenException`, `NotFoundException`,
`ValidationException`), used to translate or signal existing transport failures. Rate limiting
and adapter-local formatters need no service exemption. Outbound adapters may not depend on
`port/in`. Existing domain/port purity, application separation, ranking and calculator guards
remain. Negative fixtures reject inbound calls to `BuildService` and `BuildRepository`;
positive cases accept `BuildUseCase`, service/DB-to-repository calls and internal helper calls.

### Input port bindings and consumers

| Input port | Application implementation | Direct inbound consumers |
| --- | --- | --- |
| `AuthUseCase` | `AuthService` | `AuthRestResource`, `CurrentUser`, `CommentRestResource`, `BuildResponseAssembler` |
| `AccountRegistrationUseCase` | `AccountRegistrationService` | `AuthRestResource` |
| `ExternalAuthUseCase` | `ExternalAuthService` | `AuthRestResource` |
| `PasswordResetUseCase` | `PasswordResetService` | `PasswordResetRestResource` |
| `DemoAccountUseCase` | `DemoAccountBootstrapService` (non-prod) | `DevDemoAccountRestResource` (non-prod) |
| `BuildUseCase` | `BuildService` | `BuildRestResource`, `BuildResponseAssembler`, `PassiveStatsRestResource`, `BuildRequest` (command conversion) |
| `BuildRecommendationUseCase` | `BuildRecommendationService` | `BuildRecommendationRestResource` |
| `SavedBuildUseCase` | `SavedBuildService` | `SavedBuildRestResource` |
| `CollectionUseCase` | `CollectionService` | `CollectionRestResource` |
| `CommentUseCase` | `CommentService` | `CommentRestResource`, `CommentDeletionRestResource` |
| `VoteUseCase` | `VoteService` | `VoteRestResource`, `SavedBuildRestResource`, `BuildResponseAssembler`, `VoteResponse` (result conversion) |
| `CommunityUseCase` | `CommunitySnapshotCache` | `CommunityRestResource`, `BuildRestResource`, `TopBuildsResponse` |
| `GameDataQueryUseCase` | `GameDataQueryService` | `GameDataRestResource`, `BuildResponseAssembler` |
| `BaseStatsUseCase` | `BaseStatsService` | `BaseStatsRestResource` |
| `PassiveStatsUseCase` | `PassiveStatsService` | `PassiveStatsRestResource`, `BuildRestResource`, `SavedBuildRestResource` |
| `ScenarioStatsUseCase` | `ScenarioStatsService` | `ScenarioStatsRestResource` |
| `GameNewsUseCase` | `GameNewsService` | `NewsRestResource` |

### Inbound audit and preserved sequencing

The pre-edit audit at `f408299` counted **45 annotated operations in 16 resources**:
44 production operations in 15 resources, plus one development operation. Every resource
and helper below was inspected before changing dependencies. The table records the former
calls; the bindings above describe the replacement. Domain enums/records still cross the
boundary for translation. Actual rule retrieval now belongs to the relevant input queries.

| Resource/helper | Former application calls / output shortcuts | Domain or transport work retained |
| --- | --- | --- |
| `AuthRestResource` | `AuthService`, `ExternalAuthService`, `EmailVerificationService`; direct `EmailVerificationSender` | Request validation, JWT signing, `UserResponse`/messages; delivery coordination moved to application |
| `PasswordResetRestResource` | `PasswordResetService` | Generic confirmation and safe delivery-failure warning |
| `DevDemoAccountRestResource` | `DemoAccountBootstrapService` | Non-prod/loopback gate, account command conversion, JWT signing |
| `BuildRestResource` | `BuildService`, `PassiveStatsService`, `CommunitySnapshotCache`; constructed outbound filter | Sort/query conversion, `CurrentUser`, `BuildResponseAssembler`, optional-enrichment error handling |
| `BuildRecommendationRestResource` | `BuildRecommendationService` | Request/domain conversion and response conversion |
| `SavedBuildRestResource` | `SavedBuildService`, `PassiveStatsService`; direct `VoteRepository.summaries` | Actor, normal build assembler, private cache headers |
| `CollectionRestResource` | `CollectionService` | `CollectionCategory` conversion, actor, response |
| `CommentRestResource` | `CommentService`, `AuthService` | Author response mapping and actor on create |
| `CommentDeletionRestResource` | `CommentService` | Actor and deletion route |
| `VoteRestResource` | `VoteService` | Actor, request and result conversion |
| `CommunityRestResource` | `CommunitySnapshotCache` | Query rejection, ETag, `CommunityPublicUrls`, JSON/Discord formatting |
| `GameDataRestResource` | Direct `GameDataRepository` | Separate catalog DTOs, source-machine enrichment and presentation sorting |
| `BaseStatsRestResource` | `BaseStatsService` | Domain stats to catalog/build transport responses |
| `PassiveStatsRestResource` | `PassiveStatsService`, `BuildService`; direct `GameDataRepository` and `PassiveGadgetRules` metadata | Preview DTOs; rule metadata now obtained through `PassiveStatsUseCase.rules` |
| `ScenarioStatsRestResource` | `ScenarioStatsService`; direct `ScenarioGadgetRules` metadata | Domain context/enum and control DTO conversion; metadata now through `ScenarioStatsUseCase.rules` |
| `NewsRestResource` | `GameNewsService` | News response conversion |
| `CurrentUser` | Direct `UserRepository` | JWT subject UUID parsing and missing-claim version 0; account/version validity moved to `AuthUseCase.validateSession` |
| `BuildResponseAssembler` | `BuildService`, `AuthService`, `VoteService`; direct `GameDataRepository` | Public author/loadout/remix/vote DTO assembly |
| `TopBuildsResponse` | Application `CommunitySnapshot`, whose entry calculated passive stats | Port-owned detached snapshot plus `CommunityUseCase.passiveStats`; URL and JSON construction stay in adapter |
| `BuildRequest` / `VoteResponse` | Nested `BuildService.Draft` / `VoteService.Result` | Conversion now uses input-port records |
| Exception mappers / rate limiter / other DTOs | Seven semantic errors only; no use-case or repository calls | Existing safe HTTP mapping, policy/IP/identity/buckets, enum/value translation and formatting |

`AccountRegistrationService` supplies the missing application orchestration: its registration
and resend methods suspend any caller transaction, invoke the existing transactional services,
then deliver mail after commit. Delivery failure still returns a safe 503 with the account/token
or replacement token committed. Verification keeps its existing transaction. Password-reset
issuance still sends inside its transaction; delivery failure rolls back replacement and the
REST response stays generic. Google login/link transactions and JWT creation stay unchanged.
Real CDI/PostgreSQL regression tests assert both transaction statuses and persisted outcomes.

Community snapshot retrieval and passive projection are separate methods of `CommunityUseCase`.
The cache TTL, synchronized refresh and selection transaction are unchanged. A 304 response
or browse exclusion needs only `get()`; rendering calculates passive facts from the existing
detached entry without new repository reads. `CommunitySnapshot` is a boundary result with no
service or calculator dependency.

Usernames, build titles and descriptions, and comment text pass through the local application-layer
`ProfanityPolicy`, backed by ModernMT's `com.modernmt.text:profanity-filter:1.0.1` English dictionary.
The dependency is distributed under the Apache License 2.0; RingLab does not maintain or ship its
own explicit profanity/slur dictionary. This remains an in-process safeguard rather than a network
moderation service. Application services remain authoritative at every protected write boundary;
the dictionary filter is a safeguard, not a comprehensive moderation system.
Build title and description validation failures include an optional field identifier in the REST
error response. The shared create/edit/remix editor displays these errors beside the corresponding
input; failures without a field remain at form level.

## Scenario Preview

Scenario Preview is an optional, read-only layer after passive stats. The JDK-only
`ScenarioContext`, `ScenarioEffectRule`, `ScenarioGadgetRules`, `ScenarioStatsCalculator`
and `ScenarioStatsResult` live in `domain/gamedata`. The calculator consumes the
existing `PassiveStatsResult`; it never executes a second copy of passive arithmetic.
Its reviewed 1.4.1 scope and every conditional-effect audit entry are documented in
[Scenario Preview evidence](scenario-preview-evidence.md). Unknown is distinct from
inactive; unsupported numeric interactions retain a known subtotal but no exact total.

`ScenarioStatsService` resolves catalog selections through `GameDataRepository` and
`BaseStatsService`/`BaseStatsRepository`, loading gadget metadata once rather than per
selected gadget. It reuses `PassiveStatsService.resolved`. There are no persistence,
ownership or recommendation mutations and no additional outbound port.

`POST /api/stats/scenario-build` accepts version/racer/front/rear/nullable-tire UUIDs,
up to six distinct gadget UUIDs and a structured `scenario`. Current nullable inputs
are `lap` (1–3), `vehicleForm` (`NORMAL`, `WATER`, `FLIGHT`), `ringsHeld` (0–999),
`landingBoostActive` (boolean), and `distanceToFinish` (0–50,000 metres). Limits are
request bounds, not effective game caps. Fractional counts are rejected before
integer conversion. Client-supplied stats/types are ignored; the server owns facts.
The public POST consumes the existing client-IP general-read rate bucket. REST maps
DTOs and validation only. The response contains `passive` (including base, deltas,
coverage and effects), scenario `adjustments`, `knownSubtotal`, nullable exact `total`,
coverage, effect statuses, explanation, version and ruleset.

`GET /api/stats/scenario-rules` supplies relevant input metadata, never browser-side
formulas. The shared React `ScenarioPreview` defaults closed/passive-only in the
editor, details, and Compare; Explore remains passive. Compare owns one context and
submits it for both selections. Per-side errors never silently substitute passive
values for scenario totals. Context lives only in the mounted preview; resetting
unmounts it. Serialized calculation keys and AbortControllers invalidate results on
selection/patch/context changes and unmount, including the render before effect
cleanup. Maps and titles are not inputs, and no scenario fields enter saving/remixing.

Recommendations optimize base stats plus reviewed always-active passive stat
adjustments. Race-state and triggered effects are intentionally excluded and remain
available in Scenario Preview. `RecommendationStatsEvaluator` calls only the passive
calculator. ArchitectureTest prohibits every recommendation-domain class from
depending on Scenario types; adapters remain prohibited from executing the scenario
calculator. `ScenarioStatsService` stays independent from recommendations.
Passive rules, interaction permissions, ownership and schema are unchanged.
`ScenarioStatsResult.assumptions()` identifies the exact Quick Starter + Sea Dog
modeling assumption after interaction checks; CALCULATED coverage is not a VERIFIED label.

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
`TRUST_CLOUDFLARE_CLIENT_IP=true`, the peer is loopback or exactly matches the optional
`TRUSTED_PROXY_ADDRESS`, and `CF-Connecting-IP` contains one valid IP
literal. The loopback case supports the documented local Cloudflare Quick Tunnel -> Vite -> Quarkus
path, where Vite passes the edge-provided header through and the backend remains local-only.
The production Docker case trusts only Caddy's fixed address and its sanitized header,
as described below; other proxy layouts require their own explicit trust boundary.

Development fixtures are excluded from production builds. In development, fixture tooling
connects directly to Quarkus on loopback; Vite's development and preview proxies allow only
public API routes and reject fixture paths so public tunnel traffic cannot inherit local access.

This state is per Quarkus process and resets on restart. Horizontal deployment would require a
shared limiter such as Redis or rate limiting at a trusted edge, neither of which is part of the
current single-instance design. Cloudflare/network protection is complementary; the REST filter is
not presented as volumetric DDoS prevention.

Application services own input validity as well as orchestration. AuthService validates private input records with the existing Jakarta Validator, preserving the same username/email/password constraints as REST without depending on REST DTOs. Login preserves legacy credential acceptance, including whitespace passwords, and never trims passwords. BuildService delegates draft text, references, composition, patch and gadget validation to BuildDraftValidator; it retains query validation, ownership and persistence orchestration. CommentService explicitly validates text, null inputs and pagination bounds. REST Bean Validation remains an early transport check; no HTTP types flow inward.

Outbound method documentation specifies literal search, any-part source-machine matching, comment/version ordering, vote absence/bulk-summary conventions and atomic replacement. UserDbAdapter translates only PostgreSQL uniqueness violations for the Flyway username/email constraints; unrelated failures reach the safe unexpected-error boundary.

PostgreSQL adapters implement persistence ports; `SteamNewsAdapter` implements the outbound REST port `GameNewsRepository`. This demonstrates the same application boundary with two different infrastructure types. Steam HTTP details and JSON records stay in `adapter/out/steam`; the application only requests five latest domain news items.

```text
React -> RingLab REST -> GameNewsService -> GameNewsRepository
                                       <- SteamNewsAdapter -> Steam Web API
```

`GET /api/news` returns an array of `{id, title, url, publishedAt}` response DTOs. The typed Quarkus REST Client uses the public Steam News v2 endpoint, with configurable `STEAM_BASE_URL` and `STEAM_APP_ID` (default 2486820), 2-second connection and 3-second read timeouts. Network, HTTP, and malformed-response failures become a semantic external-service-unavailable error, which the REST mapper exposes as the safe 503 `News unavailable` response; logs omit external bodies. No retries, caching, persistence, or synchronization are used. Explore loads news independently and shows a secondary panel with titles, dates, and original links, stacking below builds on smaller screens. It omits article contents entirely, so HTML/BBCode is never rendered; empty and unavailable news leave build browsing usable.

`vote` and `comment` call BuildService to verify that their target build exists. BuildResponseAssembler in the REST adapter uses auth and game-data queries to assemble public author/loadout details. BuildRestResource retains routes, transport validation, actor lookup and service calls. Build detail responses obtain their vote summary through VoteService; list responses reuse page vote summaries supplied by BuildService after ranking. These are in-process calls. The build response currently reuses the game-data response DTO; this deliberate coupling keeps the same public shape without a parallel mapper hierarchy.

## Mapping and flow

`BaseStatsService` resolves explicitly selected versions through `GameDataRepository` and reads
historical contributions through the flat `BaseStatsRepository` port. `BaseStatsDbAdapter` uses
two fixed parameterized SQL queries against Flyway V16's normalized tables; no generic query
framework, runtime external source, or duplicated machine totals are introduced. Decimal values
use `BigDecimal` in the JDK-only `BaseStats` domain record. Its sum operation is the canonical
arithmetic for four-component builds, BOOST three-component builds, and stock machines.
`BaseStatsBreakdown.calculate` composes character/machine/total values from supplied maps;
both preview and community selection use it without depending on another application service.
BaseStatsService retains version/reference checks and names stock assembly in `stockMachineStats`. An unknown field
propagates to only that field's total. Missing rows are fully unknown, never zero.

`GET /api/stats/catalog?gameVersionId=…` returns version-specific racer and part maps (absent rows
have no entry) plus derived stock-machine totals. `GET /api/stats/build` accepts `gameVersionId`,
`racerId`, `frontPartId`, `rearPartId`, and optional `tirePartId`; omitted racer/front/rear components are unknown,
while an omitted tire contributes nothing. Unknown
catalog IDs are rejected, and a missing version returns five explicit null fields without assuming
a version. These read-only routes neither mutate builds nor impose a stats-availability rule.
Existing build response contracts remain compatible.

Game Collection displays the newest known patch snapshot. The four currently known patches begin
with identical explicitly stored base stats; they are not resolved through a runtime fallback, so
future version migrations can diverge safely. Editor preview, details and comparison
request backend totals for their own selected version. React renders values, partial/unavailable
labels and dashes; it never adds contributions. Requests are aborted/remounted when selections
change to prevent stale totals. Stats errors remain separate from publishing validity. The original
1.3.1 audit supplied no historical numerical evidence; V22's copied rows are an explicit product
policy rather than a claim of independently verified historical differences.
Base stats exclude gadget-modified/effective stats; gadget effects, versioned gadget costs, Extreme
Gear correction and new compatibility rules remain separate work.

Local registration creates a user with a nullable `emailVerifiedAt`, persists a SHA-256 digest of a
256-bit URL-safe verification secret, and asks the outbound Quarkus Mailer adapter to deliver the
public `/verify-email?token=...` link. The plaintext secret is never persisted. Verification locks
the token row, checks its 24-hour expiry, marks the user verified, and deletes the token in one
transaction. Resend replaces the previous digest and is rate limited per client IP; its REST
response is intentionally identical for unknown, verified, and unverified addresses. Delivery
failure does not roll back the already-created account, so resend remains recoverable. Flyway V15
backfills existing users as verified. Google-created users set `emailVerifiedAt` at creation because
the Google identity verifier already requires a verified email claim; Google login sends no RingLab
verification mail.

`AuthService` owns registration, password login and current-account lookup.
`EmailVerificationService` owns token generation, hashing, expiry, replacement and
consumption. Registration calls its package-private issuance method inside the
registration transaction; verification and resend each retain their own REQUIRED
transaction boundary. `AuthRestResource` calls `AccountRegistrationUseCase`, whose
`AccountRegistrationService` implementation sends mail after the transactional call returns,
so SMTP delivery is still outside the database transaction. The application beans keep
request data in local variables rather than shared fields.

`PasswordResetService` owns separate recovery tokens through `PasswordResetRepository`
and `PasswordResetSender`. V29 stores one SHA-256 digest per account and adds
`users.auth_version`. Issuance and consumption serialize on the user row; consumption
rechecks the digest after locking and atomically changes the bcrypt hash, increments
the session version and deletes the token. Unlike verification, reset delivery is
inside the transaction so failed delivery rolls back token replacement. The REST
response stays generic even on delivery failure to avoid disclosing eligibility.
Only verified local-password accounts qualify. Registration and reset share the
small `PasswordPolicy` validator. Every JWT issuer includes the current version;
`CurrentUser` rejects mismatches. Legacy JWTs without a claim mean version zero,
preserving them until the account resets its password. See [Password recovery](password-recovery.md)
for cooldown, rate limits, failure tradeoffs and the local demonstration.

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
resolves returning users by provider and subject. On a first Google sign-in, an exact
normalized email match links to an existing RingLab account only when its email was
already verified and the Google-verified address ends in `@gmail.com`. Google is
authoritative for Gmail ownership; a verified third-party email claim alone does not
establish current ownership. Workspace/custom domains, lookalike domains and other
providers retain the explicit authenticated linking requirement. Unverified local
accounts are never linked by sign-in. Gmail aliases are not collapsed. Existing IDs, passwords and profile
fields are preserved. An unverified Gmail collision returns a 409 explaining that the
user must resend/open the verification link before retrying Google. See
[Google's ownership guidance](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token).

`ExternalAuthService` owns verification, account resolution, linking policy and the
transaction. `ExternalAccountRegistration` owns passwordless account creation and
username selection inside that transaction. First-time users
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
Authenticated local users can link a verified Google identity with the same email through
`ExternalAuthService.link`. Adding passwords and Google API authorization are outside scope.

`GOOGLE_CLIENT_ID` and frontend `VITE_GOOGLE_CLIENT_ID` must identify the same Web
client. Missing configuration disables Google sign-in without disabling local auth.
The frontend uses GIS's official button and JavaScript callback, then submits JSON
through the existing API layer; this is not Google's form/redirect login flow.
`useAuthForm` owns local/Google submissions and registration. Auth requests are
anonymous, so an invalid credential cannot expire an existing bearer session. It
ignores responses after navigation or a newer session. `AuthPage` owns the form markup.
The endpoint accepts JSON, and cross-origin requests remain governed by the existing
CORS allowlist. Successful login explicitly accepts a RingLab token in the browser;
there is no authentication cookie established by a cross-site form submission.

Build editor loading lives in `useBuildDraft`: it loads an owned edit or public remix,
resets on source/account changes, and ignores abandoned loads. New drafts default to
the latest known patch; edits/remixes retain their stored patch, including an absent
legacy patch that must be explicitly selected before saving. `BuildEditor` owns field
interactions and saves. Failed source loads do not expose a previous draft for submission.

Explore uses `SearchableFilter` for searchable catalog choices and keyboard interaction.
`exploreFilters.ts` contains URL parsing, preference precedence and active-filter labels;
the page coordinates catalog/build loading and rendering. An unavailable saved patch is
ignored without repeatedly replacing the URL. Removing the sort chip clears its saved
public preference so the default sort is restored.

MapStruct generates entity/domain mappings for users, builds, comments, and the five game-data entity types. It removes repeated field copying and keeps ORM records out of the domain. Persistence mappers use strict unmapped-target checking, so adding a target property requires an explicit mapping decision. Vote persistence writes its small validated record directly with an upsert, so it has no mapper. REST mappings are explicit where they are small or require assembling multiple module results.

`GameDataDbAdapter` and `GameDataDbMapper` remain combined for racers, source machines, machine parts, gadgets, and game versions, with strongly typed list/find methods in `GameDataRepository`. `findMachine` resolves part source metadata. The REST layer uses explicit DTOs. Machine responses expose `racingType`; machine-part responses expose the source ID/name, artwork and racing type. Artwork is not duplicated into `machine_parts`. Build requests keep `frontPartId`, `rearPartId`, and nullable `tirePartId`; responses keep the same shape and return `tirePart: null` for BOOST machines.

Create-build flow:

```text
React form → POST /api/builds with bearer JWT
  → Quarkus verifies signature, issuer, expiry, and role
  → BuildRequest Bean Validation
  → BuildService.create(actor UUID, Draft)
  → BuildDraftValidator validates the draft through GameDataRepository and domain rules
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

`Machine` is catalog/source-machine metadata with a `RacingType`. `MachinePartType` remains FRONT, REAR, or TIRE. `BuildDraftValidator` delegates source-machine compatibility to `MachineCompatibility` and required slots to `MachineComposition`: BOOST uses FRONT/REAR and prohibits a tire; other types require all three slots. Mixed source machines are allowed only when their known racing types match, independently of the racer's type. V27 replaces the earlier family classification with this racing-type policy. Gadgets remain an independent ordered configuration validated by `GadgetPlate`.

Explore's compact “Uses parts from” filter retains the `machineId` query parameter as a source-machine ID. It matches front OR rear OR nullable tire source, so Board builds remain discoverable.

Votes have a unique `(user_id, build_id)` constraint and a −1/+1 check. PostgreSQL `INSERT … ON CONFLICT … DO UPDATE` makes repeated/concurrent votes update the same row atomically. A single aggregate query per requested build counts upvotes and downvotes; `VoteSummary` derives score as upvotes minus downvotes. No client-owned or cached counters exist. Build and vote responses expose `score`, `upvotes`, and `downvotes`; vote responses also retain `myVote`.

Comment listings are paginated in PostgreSQL in deterministic `createdAt`, then `id` order. The REST response includes `items`, `total`, `page`, and `size`, allowing the client to navigate page boundaries without inferring them from the number of returned comments.

Browse queries filter candidates in PostgreSQL through `BuildDbAdapter` and its JPA Criteria predicate helper. Search uses `locate(lower(title), lowercasedSearch)`, so user input remains a literal substring rather than SQL or `LIKE` wildcard syntax. The adapter projects only `BuildRanking.Candidate(id, createdAt, gameVersionId)` for every match, without loading full build entities or ordered gadget collections. `VoteDbAdapter` supplies raw upvote/downvote summaries for all candidate IDs when SCORE or BEST_RATED needs them; NEWEST defers its summary query until after pagination. `BuildService` applies the selected canonical domain ranking, paginates globally, bulk-loads only the selected page through `BuildRepository.findAll`, and reconstructs the final list in ranked ID order because persistence result order is unspecified. The maximum page size remains 50 at the REST boundary. Persistence therefore owns filtering and fact retrieval but no Wilson, sign-bucket, sorting, tie-break, or pagination policy. Collection/detail assembly uses straightforward bounded lookups, so response-enrichment query count grows with page size; batching remains a separately measurable future optimization. There is no optimistic-lock version field: simultaneous edits by the same author use last-write-wins semantics.

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

V4 creates three explicitly identified parts per seeded machine, backfills old builds, and removes `builds.machine_id`. V25 adds `machines.family`, initially marks pre-existing rows `STANDARD`, and makes `tire_part_id` nullable. V26 corrects the 12 verified Extreme Gear machines to `BOARD`, preserves their FRONT/REAR IDs, clears obsolete tire references on affected builds, and removes their generated TIRE rows and dependent stats. Standard machine parts and references are unchanged. Build IDs, timestamps, ordered gadgets, votes, and comments are preserved.

Build ranking is a domain/application policy. The REST adapter translates the public `newest`, `score`, and `rated` query values into typed `BuildSort` values. `BuildRanking` defines every ordering: newest uses creation time descending then UUID ascending; score uses raw score descending followed by those ties; best rated uses the full-precision Wilson lower bound descending, catalog release date descending (known dates before unspecified legacy versions), fewer downvotes when Wilson is zero, creation time descending, then UUID ascending. A newer-patch downvote-only build can therefore outrank an older-patch unrated build when both have Wilson zero. `WilsonScore` is the single pure-Java canonical implementation using z = 1.96 (approximately 95% confidence), including the zero-vote case. Sample size therefore matters: 40 upvotes and 1 downvote rank above 3 upvotes and no downvotes. Persistence projects only build ID, creation time, and version ID; application services load catalog release dates in one lookup, without per-build queries. The adapters do not interpret ranking modes. Ranking happens before pagination in `BuildService`. Wilson remains internal to ordering; the visible community score is still upvotes minus downvotes, and no ranking values are cached or exposed.

## Community selection responsibilities

`CommunitySelectionService.select` owns the REQUIRES_NEW transaction, global
ranking, 50-candidate hydration batches and early termination after three eligible
builds. It creates one package-private `CommunitySnapshotAssembler` per selection.
That object preloads the catalog, delegates eligibility to `CommunityEligibility`,
and assembles snapshot entries with per-selection author and per-version stats maps.
It is not a CDI bean or shared cache. All lookups remain inside the selection
transaction, and `CommunitySnapshotCache` publishes the immutable snapshot only
after the transaction returns successfully. Cache lifetime and synchronization
are unchanged.

Explore passes the displayed snapshot's IDs as up to three `excludeId` query parameters
to build browsing. Exclusion happens before ranking, counts and pagination, so a cache refresh
cannot silently change which displayed winners are omitted. The older `excludeTop=true`
option remains available for clients that want the server's current winners excluded instead.

The write validator, showcase eligibility and incomplete stats preview deliberately
remain separate policies. They reuse `MachineCompatibility`, `MachineComposition`,
`GadgetPlate` and `BaseStats.sum` only where the same rule applies.

See [the code walkthrough](code-walkthrough.md) for IDE entry points and behavior tests.

## Frontend

The frontend source is organized by responsibility. `src/app` owns the app shell
and footer; `src/main.tsx` mounts the existing router and providers. `src/pages`
contains route composition. Domain-aware UI and state live in `src/features`
(auth, builds, collection, comments, maps, news, recommendations, saved-builds,
and stats), including editor draft state and browsing filters. Tests stay beside
the responsibility they exercise; route-level interaction tests remain in pages.

`src/shared` contains API contracts (`types.ts`), the API client, loading hooks,
generic formatting, and reusable UI. Shared code does not import features or
pages. Features may reuse other feature responsibilities without runtime cycles;
pages compose them and the app mounts them. `ScenarioBadge` remains in stats.
`CollectionArtwork` supplies catalog fallbacks and ownership to the generic
`shared/ui/Artwork`; the latter does not read collection state or call the API.

Plain CSS lives in `src/styles`. `index.css` explicitly imports the original
stylesheet's sections in their existing cascade order, including late readability,
responsive, stats, and collection overrides. Some adjacent override sections span
related responsibilities to preserve that order without changing selectors.
Recommendation CSS retains its earlier import through the recommendation dialog.
The full file map and source tree are in [Frontend organization](frontend-organization.md).

`GameVersion` is persistent game-data catalog metadata (`id`, plain version string, release date).
V5 seeds the four supplied official versions and adds a nullable `Build.gameVersionId` foreign key.
Legacy stored builds may remain versionless, but creating or editing requires a selected
known version. BuildDraftValidator rejects a missing version with the `gameVersionId` field
identifier and rejects unknown IDs with a validation exception, mapped to 400 by REST. The catalog endpoint
`GET /api/game-versions` lists release dates newest first; build responses contain a small nested
`gameVersion` DTO or null. Explore's optional `gameVersionId` predicate filters candidates in PostgreSQL
before application ranking/pagination and composes with existing filters and all three sorts.
Wilson ranking and visible raw scores are unchanged. Steam news remains an independent external
REST adapter; automatic patch extraction and synchronization are intentionally not implemented.

The editor requires a Game version / Patch selection; details and cards display selected
versions compactly. Explore offers a Patch filter, and Game Collection lists versions and release
dates. The demo seeder resolves and validates catalog IDs through REST before writing, assigning
a deterministic version mix to new demo builds. Existing build fields, votes, and comments are preserved.

The editor starts without a machine type and offers SPEED, ACCELERATION, HANDLING, POWER and BOOST. Part options and complete stock shortcuts are filtered by the source machine's racing type; switching type clears incompatible parts and the stock shortcut. BOOST omits the tire control and preview row. Details, cards, sharing, stats, remix/edit, and comparison handle a null tire without inventing a placeholder. The backend remains authoritative.

React Router owns page navigation. A small context holds current authentication; forms and page queries own local state. `api.ts` centralizes the bearer header, JSON handling and errors. `useLoad` aborts stale requests on route/filter changes. Build selection helpers preserve and reorder gadget IDs and mirror the small Gadget Plate placement rule for editor feedback. React's normal text escaping is used for user content.

Session bootstrap clears credentials on 401 only; other failures retain the token and expose a session-check retry. Requests capture a session generation so old responses cannot clear or replace a newly accepted session. API 429 errors retain a valid Retry-After delay and include it in their displayed message. The editor resets its draft on edit-route changes and permits submission only when the loaded build ID and author match the current route and actor. Save completion updates UI and navigates only while its editor context remains current; leaving the editor does not cancel an already-submitted server mutation.

At the REST boundary, CurrentUser also verifies that the JWT subject still has a RingLab account; missing accounts receive 401 without changing public author lookup semantics. HttpRestExceptionMapper preserves sanitized framework HTTP errors (including malformed-input 400 and unsupported-media 415); unexpected exceptions retain the generic 500 fallback. Persistence writes translate only SQLSTATE 23503 for `votes_build_id_fkey`, `comments_build_id_fkey`, and `builds_remixed_from_build_id_fkey` into missing-build application errors (404). Explicit flushes keep those failures inside the translation boundary; the enclosing transaction rolls back, and unrelated constraint failures remain unexpected.

Routes cover Explore, My Builds, details, create/edit, register/login, and the requested public catalog browsing. Controls have labels, focus states, pending/error states and responsive layouts. Artwork uses only local `/assets/` paths, with a placeholder on failure. No global-state library or UI framework is used.

Compare Builds is a URL-driven frontend view for exactly two existing builds. It composes the current detail and paginated browse APIs to compare semantic racer, patch, Front/Rear/Tire, ordered gadget, Gadget Plate, and vote fields rather than free-form text; it stores no comparison state on the backend.

## Verification and limits

Production packaging is defined separately in `compose.production.yaml`: Caddy
serves the compiled React SPA and proxies unchanged `/api/*` paths to the Java 21
Quarkus container; PostgreSQL 17 persists in a production-only volume on an internal
network. Only Caddy publishes 80/443. Images are built on CI after both test jobs
pass, not on the VPS. Production releases require a separate workflow_dispatch
action selecting a full commit SHA; the workflow resolves both images to immutable
digests and uses the protected production environment. Pushes never deploy.
Runtime secrets stay on the VPS. This configuration is prepared, not yet deployed.

The rate-limit adapter can additionally trust one explicitly configured proxy IP.
Production pins Caddy's Docker address and makes it replace the visitor-IP header
after checking Cloudflare's source ranges. Quarkus uses the original socket peer;
arbitrary Docker peers are not trusted. Local loopback behavior is unchanged.
See [production deployment](production.md) for configuration, trust boundaries,
secret mounts, later deployment commands, backup and rollback limitations.

`AcceptanceTest` uses Quarkus's test runner, HTTP requests, actual JWT registration/login, Flyway, and PostgreSQL. `PackagedApiIT` repeats it against the production artifact. `DomainTest` checks immutable collection and vote/ownership behavior. Frontend tests check ordering and API errors/session expiration. The test run's external-database option requires a disposable database.

`RepositoryContractIntegrationTest` checks actual persisted deletion effects (including surviving remix contents), literal/blank search, any-slot machine matching, comment/version tie-breaks and vote replacement/absence conventions. Deletion postconditions belong to BuildRepository's contract; PostgreSQL retains responsibility for implementing them atomically with its existing constraints.

JWT logout is client-side token disposal; a copied token lasts until its one-hour expiry. There is no refresh/revocation system. Curated/custom artwork, additional gadget compatibility rules, patch-aware costs and gadget-modified stats remain outside the implemented scope; current-cost Gadget Plate capacity validation and nullable versioned base-stat calculation are implemented.
# Community showcase snapshot

`application/community` selects up to three eligible builds with the existing domain Best Rated
comparator and caches one immutable snapshot per process. Read-only eligibility reuses machine
compatibility and Gadget Plate rules; controlled demo prefixes are excluded only here.
`adapter/in/rest/community` composes existing public build/stats DTOs, constructs trusted public
URLs, implements conditional GET and formats Discord payloads without posting them.
`TopCommunityBuilds` loads this snapshot independently above Explore filters and supplies its stats
to the shared BuildCard. Explore excludes those displayed IDs from the lower grid before
counting and pagination; My Builds remains complete.
See [the integration guide](community-top-builds.md) for contracts, policy, TTL and scaling limits.

## Browse comparison and page stats

`BuildComparisonProvider` owns at most two public build selections above the routes.
It has no server persistence or authentication dependency. Cards use independent
title links and comparison buttons; an inline tray checks availability before opening
the existing comparison route. Route changes preserve selection; a reload clears it.

`GET /api/builds?includeStats=true` optionally adds `statsByBuildId` for the selected
page. `BaseStatsService.buildPage` uses the saved references and request-local maps,
reading each distinct patch's racer and part maps once. It uses the same domain
`BaseStatsBreakdown.calculate` as the single-build endpoint. Pagination, ranking,
exclusions and response assembly precede enrichment. Failure omits the map and adds
a safe `statsError` while preserving the normal page. Default callers receive the
original four fields. Explore/My Builds consume this map; Top 3 keeps snapshot stats.
See [browsing improvements](browsing-improvements.md) for UI behavior and validation.

## Author-selected map preferences

`RaceMap` is a catalog concept with an explicit course category and separate optional
content pack. `GameDataRepository` exposes the ordered catalog; `Build` owns an immutable
set of recommended map IDs. V30/V31 add the catalog and association table, with zero
associations representing an unspecified preference (All maps). `BuildDraftValidator`
validates IDs; omitted update fields preserve selections. Persistence uses membership
and emptiness predicates before ranking/counting/pagination, shared with private bookmarks.
Response assembly loads the map catalog once per page, and collection hydration is batched.
The global community snapshot includes preferences under its existing cache lifetime.
Frontend selection, dialogs, comparisons and sharing use shared map helpers. See
[Recommended Maps](recommended-maps.md) for contracts, isolated startup and the optional demo.

## Passive gadget statistics

The base-stat contract stays unchanged. `PassiveGadgetRules` holds typed reviewed data for
an explicit patch; `PassiveStatsCalculator` evaluates loadout conditions and arithmetic in
the domain. `PassiveStatsService` resolves racer and machine types separately, validates
draft gadget IDs and the existing plate, and reuses batched base contributions for lists.
Community snapshots calculate with already resolved catalog metadata. REST adds passive
results without replacing base fields. See [passive-gadget-sources.md](passive-gadget-sources.md)
for evidence and partial coverage. The versioned Java additive policy admits only
23 reviewed numerical effect identities; unknown additions remain unsupported.
Signed vectors are added even when they overlap. Calculation notes identify the
community-backed policy separately from the immutable imported fact ruleset.
Maps never affect these calculations.

## Build recommendations

`domain/build/recommendation` owns immutable request/catalog/result types, strict
lexicographic stat comparison, exact `BalancedStage` thresholds, and a bounded deterministic solver. It reuses
`PassiveStatsCalculator`, `GadgetPlate` and the now-pure
`domain/gamedata/MachineCompatibility`; the existing application compatibility
facade translates validation failures without changing existing build behavior.
`BuildRecommendationSolver` orchestrates validation, optional current-stat comparison,
mode selection and result explanations. Package-private domain collaborators
own candidate validation/pools (`RecommendationCandidates`), passive calculation
(`RecommendationStatsEvaluator`), ordering and
incumbent tracking (`RecommendationCandidateOrder`), budget checks
(`RecommendationSearchBudget`), shared gadget traversal (`RecommendationGadgetSearch`),
and the separate Strict and Balanced algorithms (`StrictRecommendationSearch`,
`BalancedRecommendationSearch`). They consume detached values only; none is a
port, application service or persistence boundary. `BalancedConfiguration` validates
active/ignored settings; `BalancedStage` defines the exact signed sacrifice formula.
The request's `GadgetRecommendationScope` defaults to `KEEP_CURRENT`: the shared
gadget traversal evaluates the current ordered plate for each candidate type.
`OPTIMIZE_UNLOCKED` enables existing subset enumeration around gadget locks.
Both modes retain the Phase 2 passive policy and budgets. Scope is part of the
frontend configuration identity; Phase 1 collection snapshot checks remain intact.
Only complete base/passive totals compete; unknown passive values never become zero
or partial objective scores. Strict retains its component reduction because passive
adjustments are ID-independent within fixed racer/machine types. Balanced uses
sequential thresholds over surviving candidates without a current-build baseline. The evaluator
runs per gadget subset, not per component leaf. The REST request/response have no
scenario objective fields; unknown top-level request fields are rejected.
`RecommendationCatalogLoader` resolves catalog and versioned contributions once
through the existing outbound ports in a short transaction.
`BuildRecommendationService` then searches detached facts outside a transaction,
with two nonwaiting concurrency permits and server-owned work/time budgets.
`BuildRecommendationRestResource` authenticates and maps a read-only proposal,
runs on a worker thread and has a dedicated per-user REST rate policy.

Frontend lock/priority/draft-merge helpers live in `recommendation.ts`; the
`useBuildRecommendation` hook owns abortable requests and stale-context checks.
Collection revisions advance only when a successful authoritative read changes
normalized exclusion sets within the current account/session. Focus and Apply share
an in-flight read. Refresh returns data, revision and account/session identity together,
so Apply detects changes without waiting for a React render. Failed reads block Apply
without advancing revision; pending mutations and all other stale-result guards remain.
`useEditorRecommendationLocks` owns lock and popup lifetimes at every viewport width.
`useRecommendationConfiguration` owns the captured starting setup, mode, priorities,
loss settings and calculation eligibility. The dialog keeps modal focus and execution
actions, composing configuration controls, reference selections and result comparisons.
`RecommendationSelectionComparison` presents shared Added/Changed/Kept selections;
`RecommendationExplanation` owns outcome copy, priority summaries and stage diagnostics.
The editor hosts ephemeral locks and one native modal with configuration,
activity and result states. Explicit Apply changes only permitted unsaved draft
fields. Resizing preserves locks and requests; responsive controls and a scrolling
modal body keep actions available. Apply exposes specific stale/load blockers and
ignores canceled refresh completions, including errors. No optimizer policy is copied
into React or persistence. See [Auto-builder v1](auto-builder.md) for the exact
objective, separability proof, data limits, endpoint and demonstration.

Balanced is sequential constrained lexicographic optimization using per-priority
sacrifice thresholds over the surviving candidate set. A streaming pass proves each
active maximum and applies `b - abs(b) * loss / 100` with exact decimals. The next
pass counts survivors; the final pass compares active values, then shared convenience
ties. Ignored stats never rank candidates. Safe suffix minima/maxima collapse wholly
surviving subtrees or prune wholly failing ones; other branches recurse. No full
candidate population is stored. BEST_FOUND withholds all stage claims; ESTABLISHED
includes proven maxima, thresholds and counts. Empty/partial drafts are supported.
The captured current selections are for locks, gadget scope, convenience and display
only. Configuration changes invalidate pending requests and Apply proposals. No
preferences or locks are persisted. See [Balanced mode](balanced-auto-builder.md)
for the bound proof, independent oracle and exact comparison order.

## Frontend page responsibilities

`BuildEditor` keeps route/authentication context, draft updates, save validation and
error handling. Its feature-scoped catalog hook wraps the existing independent
`useLoad` calls. Essentials, machine setup, gadget selection and preview components
live in `features/builds/editor`; gadget search and reorder drag state stay with their
sections. The existing map picker and stats/scenario components are reused.
`ExploreFiltersSection` presents controlled filters while Explore owns URL state,
preferences and requests. `BuildCommentsSection` owns comment composition and
presentation; BuildDetails retains concurrent reads, pagination state and the shared
mutation busy/error boundary used by voting, comments and build deletion.

## Personal collection availability

`CollectionExclusions` models explicit exclusions only: absent and newly added catalog items are owned by default. `CollectionService` validates typed catalog references through `GameDataRepository`; `CollectionRepository` persists private racer, source-machine, and gadget exclusions. V33 adds three foreign-key-backed tables with `(user_id, item_id)` primary keys. Idempotent PUT updates serialize on the authenticated user's row. No migration backfills owned rows.

The authenticated `/api/collection` adapter returns private, non-cacheable data. Recommendation orchestration loads the actor's exclusions separately from the full catalog. The existing Strict/Balanced solver filters candidate pools, rejects named unavailable locks, and retains full reference stats without seeding an unavailable incumbent. Ownership does not change scoring, manual build validation, public queries, top-three membership, or Discord snapshots.

`CollectionProvider` shares session-scoped frontend state, guards late account responses, and distinguishes loading/errors from all-owned data. Missing-item summaries deduplicate source machines while artwork alone becomes grayscale. Selections remain editable and publishable. Recommendation Apply reloads private availability and checks every proposed source, racer, and gadget; local collection changes invalidate the proposal. Cross-device changes are observed on focus and at Apply, rather than pushed live.
