# Architecture

## One modular monolith

RingLab has one Quarkus process and one PostgreSQL database. Features are packages within the same deployment, not network services. The React client calls REST. This keeps local startup, transactions, and thesis explanation small while keeping feature responsibilities visible.

Packages are architecture-first under `dev.ringlab`: `domain`, `application`, `port`, and `adapter`. Feature-specific domain code is grouped by `auth`, `gamedata`, `build`, `vote`, `comment`, and `news`; application code currently has `auth`, `build`, `vote`, `comment`, and `news` services. Outbound contracts are centralized for immediate visibility. The application error type is `application.AppException`; its HTTP adapter is `adapter.in.rest.ErrorRestExceptionMapper`. The JWT current-user helper lives in `adapter.in.rest.auth`. There are no generic base repositories or services, event bus, or framework for future games.

```text
dev.ringlab/
  domain/{auth,build,comment,gamedata,news,vote}/
  application/{auth,build,comment,news,vote}/
  port/out/
    {User,Build,Comment,GameData,GameNews,Vote}Repository.java
  adapter/in/rest/{auth,build,comment,gamedata,news,vote}/
    request/ and response/
  adapter/out/db/{auth,build,comment,gamedata,vote}/
  adapter/out/steam/
```

## Boundaries

- **domain:** immutable Java records (`User`, `Racer`, `Machine`, `MachinePart`, `Gadget`, `GameVersion`, `Build`, `Vote`, `Comment`) and the `RacingType` and `MachinePartType` enums, using only the JDK. `Racer` and `Machine` carry `RacingType` and image path; `Gadget` carries its description, nullable future slot cost, and image path. `Build` snapshots its ordered gadget list. `Vote` permits only −1 and +1. Domain code imports no Quarkus, REST, Hibernate, or JPA types.
- **application:** use-case services that depend on domain types and outbound repository contracts. Services validate build references, enforce author ownership, normalize accounts, and orchestrate mutations. CDI and transaction annotations are pragmatic application-layer dependencies. AuthService uses Quarkus's bcrypt utility directly because a second hashing abstraction would not serve a current implementation need.
- **port/out:** the flat set of outbound infrastructure contracts: `UserRepository`, `BuildRepository`, `CommentRepository`, `GameDataRepository`, `GameNewsRepository`, and `VoteRepository`. Centralizing this small set makes every application-to-infrastructure boundary visible in one package. These interfaces depend only on domain types and JDK types.
- **adapter/in/rest:** route/role annotations and conversion into service calls. Feature-specific transport records live in `request/` and `response/` subpackages beside their REST resource: for example, `build/request/BuildRequest` and `build/response/BuildResponse`. Entry points end in `RestResource`, including `AuthRestResource`, `BuildRestResource`, `CommentRestResource`, `CommentDeletionRestResource`, `GameDataRestResource`, `NewsRestResource`, and `VoteRestResource`. REST does not return JPA entities or password hashes. CurrentUser extracts a UUID from a verified JWT. Global error mapping remains directly under `adapter.in.rest` because it is shared rather than feature-specific.
- **adapter/out/db:** JPA entities named `*DbEntity`, persistence implementations named `*DbAdapter`, and MapStruct interfaces named `*DbMapper`. For example, `BuildDbAdapter` implements `BuildRepository` and uses `BuildDbMapper` with `BuildDbEntity`. Panache repositories remain where concise and EntityManager queries where more explicit. Only adapters know table names and PostgreSQL upsert syntax.

The outbound repositories are real boundaries between application behavior and infrastructure. No input ports are currently used because REST resources call application services directly. Read-only game-data routes use `GameDataRepository` directly because they have no additional use-case rules.

PostgreSQL adapters implement persistence ports; `SteamNewsAdapter` implements the outbound REST port `GameNewsRepository`. This demonstrates the same application boundary with two different infrastructure types. Steam HTTP details and JSON records stay in `adapter/out/steam`; the application only requests five latest domain news items.

```text
React -> RingLab REST -> GameNewsService -> GameNewsRepository
                                       <- SteamNewsAdapter -> Steam Web API
```

`GET /api/news` returns an array of `{id, title, url, publishedAt}` response DTOs. The typed Quarkus REST Client uses the public Steam News v2 endpoint, with configurable `STEAM_BASE_URL` and `STEAM_APP_ID` (default 2486820), 2-second connection and 3-second read timeouts. Network, HTTP, and malformed-response failures become a safe 503 `News unavailable` response through the existing application error mapper; logs omit external bodies. No retries, caching, persistence, or synchronization are used. Explore loads news independently and shows a secondary panel with titles, dates, and original links, stacking below builds on smaller screens. It omits article contents entirely, so HTML/BBCode is never rendered; empty and unavailable news leave build browsing usable.

`vote` and `comment` call BuildService to verify that their target build exists. Build responses use auth and game-data queries to assemble public author/loadout details and VoteService for scores. These are in-process calls. The build response currently reuses the game-data response DTO; this deliberate coupling keeps the same public shape without a parallel mapper hierarchy.

## Mapping and flow

MapStruct generates entity/domain mappings for users, builds, comments, and the five game-data entity types. It removes repeated field copying and keeps ORM records out of the domain. Persistence mappers use strict unmapped-target checking, so adding a target property requires an explicit mapping decision. Vote persistence writes its small validated record directly with an upsert, so it has no mapper. REST mappings are explicit where they are small or require assembling multiple module results.

`GameDataDbAdapter` and `GameDataDbMapper` remain combined for racers, source machines, machine parts, gadgets, and game versions, with strongly typed list/find methods in `GameDataRepository`. `findMachine` resolves part source metadata. The REST layer uses explicit `RacerResponse`, `MachineResponse`, `MachinePartResponse`, and `GadgetResponse` DTOs. `GET /api/machine-parts` returns each part's ID, type, source-machine ID/name, and source racing type. Build requests use `frontPartId`, `rearPartId`, and `tirePartId`; responses include the corresponding part DTOs. The dynamic build-filter query uses JPA Criteria.

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

Edit/delete flows load the existing record and compare the authenticated actor to the stored author before any write. The client cannot set author IDs. Comment deletion uses the comment author, including when the build belongs to somebody else. Service transactions enclose each mutation; database foreign keys and unique/check constraints remain the final consistency boundary.

## Relational design

`users`, `racers`, `machines`, `machine_parts`, `gadgets`, `builds`, `votes`, and `comments` use UUID primary keys. `build_gadgets` uses `(build_id, position)` as its primary key: it is an ordered collection entry, not an independently addressable entity. Foreign keys enforce valid references. Build deletion cascades its collection, votes, and comments. User deletion is not implemented.

`Machine` is catalog/source-machine metadata. The framework-independent `MachinePart` record represents a FRONT, REAR, or TIRE component originating from a source machine; `MachinePartType` is an enum persisted as a string. `Build` composes exactly one FRONT + one REAR + one TIRE, alongside one racer. Required part IDs and foreign keys preserve references; BuildService enforces the slot types and rejects unknown IDs with 400 errors. Parts can come from different machines. Gadgets remain an independent ordered configuration mapped through `build_gadgets`. No part stats, individual names, artwork, slot budgets, or compatibility restrictions are invented.

Explore's compact “Uses parts from” filter retains the `machineId` query parameter as a source-machine ID. It matches front OR rear OR tire source using the same predicate for results and counts, composing with racer, literal search, author, all sorts, and pagination.

Votes have a unique `(user_id, build_id)` constraint and a −1/+1 check. PostgreSQL `INSERT … ON CONFLICT … DO UPDATE` makes repeated/concurrent votes update the same row atomically. Scores are always queried as `SUM(value)`; no client-owned or cached score exists.

Comment listings are paginated in PostgreSQL in deterministic `createdAt`, then `id` order. The REST response includes `items`, `total`, `page`, and `size`, allowing the client to navigate page boundaries without inferring them from the number of returned comments.

Browse queries filter in PostgreSQL and paginate before response assembly. `BuildDbAdapter` uses JPA Criteria with one shared predicate helper for item and count queries. Search uses `locate(lower(title), lowercasedSearch)`, so user input remains a literal substring rather than SQL or `LIKE` wildcard syntax. Score sorting uses a correlated vote-sum subquery; all sorts retain creation time then ID as tie-breakers. The maximum page size is 50. Collection/detail assembly uses straightforward bounded lookups, so query count grows with page size; batching would be a measurable future optimization rather than a custom query framework now. There is no optimistic-lock version field: simultaneous edits by the same author use last-write-wins semantics.

Flyway V1 defines schema; V2 defines the supplied game dataset, and V3 assigns stable local artwork paths to the six supplied racers. Hibernate runs schema validation. V6 expands the catalog to 53 racers, 27 source machines and 81 parts, retaining existing IDs and adding official local artwork paths. Gadget image paths, descriptions and slot costs remain null. No fake application users enter migrations.

V6 makes racer/machine racing types nullable when authoritative type data is unavailable. The domain and persistence still use the five-value `RacingType` enum, persisted as strings. REST preserves known string values and returns null for unverified types; the frontend explicitly accepts null and displays Unknown. This prevents catalog completeness from requiring invented game statistics. [The catalog ledger](game-data-sources.md) records released entries, provenance, artwork gaps, skins and future exclusions. The named launch-machine inventory is still incomplete; the 27-machine RingLab count is not the game's total. Each new source machine gets one FRONT, REAR and TIRE under RingLab's existing composition model, without asserting additional in-game compatibility rules.

V4 creates three explicitly identified parts per seeded machine, backfills old builds to the three parts from their former machine, makes all three columns required, then removes `builds.machine_id`. The transactional migration fails if any old build cannot be mapped. Build IDs, timestamps, ordered gadgets, votes, and comments are preserved; V1–V3 remain unchanged.

Best rated (`sort=rated`) orders by the Wilson lower bound with z = 1.96 (approximately 95% confidence), then raw score descending, creation time descending, and UUID ascending. `BuildDbAdapter` builds the calculation as a correlated Criteria aggregate over votes, so PostgreSQL ranks all matching builds before pagination using the same filters. Zero-vote builds receive zero. Sample size matters: 40 upvotes and 1 downvote rank above 3 upvotes and no downvotes because the larger sample provides stronger evidence. Wilson is internal to ordering; the visible community score remains upvotes minus downvotes. No ranking values are cached or exposed in responses.

## Frontend

`GameVersion` is persistent game-data catalog metadata (`id`, plain version string, release date).
V5 seeds the four supplied official versions and adds a nullable `Build.gameVersionId` foreign key.
There is no default or backfill: existing builds remain versionless and editing may add, change,
or clear a version. BuildService rejects unknown non-null IDs with 400. The catalog endpoint
`GET /api/game-versions` lists release dates newest first; build responses contain a small nested
`gameVersion` DTO or null. Explore's optional `gameVersionId` predicate is shared by PostgreSQL
item/count queries before pagination and composes with existing filters and all three sorts.
Wilson ranking and visible raw scores are unchanged. Steam news remains an independent external
REST adapter; automatic patch extraction and synchronization are intentionally not implemented.

The editor offers an optional Game version / Patch selector; details and cards display selected
versions compactly. Explore offers a Patch filter, and Game Collection lists versions and release
dates. The demo seeder resolves catalog IDs through REST and assigns a deterministic version mix
only to its named demo builds, preserving their existing parts, gadget order, votes, and comments.

The editor selects Front, Rear, and Tires independently; preview and details show each source name. Cards show the stock-machine name when all sources match, otherwise “Mixed machine”. Game Collection explains that stock machines provide all three components. The demo seeder retains its users, builds, comments, and vote distributions, with three existing examples using mixed sources.

React Router owns page navigation. A small context holds current authentication; forms and page queries own local state. `api.ts` centralizes the bearer header, JSON handling and errors. `useLoad` aborts stale requests on route/filter changes. Build selection helpers preserve and reorder gadget IDs without game-rule calculations. React's normal text escaping is used for user content.

Routes cover Explore, My Builds, details, create/edit, register/login, and the requested public catalog browsing. Controls have labels, focus states, pending/error states and responsive layouts. Artwork uses only local `/assets/` paths, with a placeholder on failure. No global-state library or UI framework is used.

## Verification and limits

`AcceptanceTest` uses Quarkus's test runner, HTTP requests, actual JWT registration/login, Flyway, and PostgreSQL. `PackagedApiIT` repeats it against the production artifact. `DomainTest` checks immutable collection and vote/ownership behavior. Frontend tests check ordering and API errors/session expiration. The test run's external-database option requires a disposable database.

JWT logout is client-side token disposal; a copied token lasts until its one-hour expiry. There is no refresh/revocation system. Curated/custom artwork, gadget rules and calculated stats remain outside the implemented scope.
