# Architecture

## One modular monolith

RingLab has one Quarkus process and one PostgreSQL database. Features are packages within the same deployment, not network services. The React client calls REST. This keeps local startup, transactions, and thesis explanation small while keeping feature responsibilities visible.

Packages are architecture-first under `dev.ringlab`: `domain`, `application`, `port`, and `adapter`. Feature-specific domain and application code remains grouped by `auth`, `gamedata`, `build`, `vote`, and `comment`; outbound contracts are centralized for immediate visibility. The application error type is `application.AppException`; its HTTP adapter is `adapter.in.rest.ErrorRestExceptionMapper`. The JWT current-user helper lives in `adapter.in.rest.auth`. There are no generic base repositories or services, event bus, or framework for future games.

```text
dev.ringlab/
  domain/{auth,build,comment,gamedata,vote}/
  application/{auth,build,comment,gamedata,vote}/
  port/out/
    {User,Build,Comment,GameData,Vote}Repository.java
  adapter/in/rest/{auth,build,comment,gamedata,vote}/
    request/ and response/
  adapter/out/db/{auth,build,comment,gamedata,vote}/
```

## Boundaries

- **domain:** immutable Java records (`User`, `Racer`, `Machine`, `Gadget`, `Build`, `Vote`, `Comment`) and the `RacingType` enum, using only the JDK. `Racer` and `Machine` carry `RacingType` and image path; `Gadget` carries its description, nullable future slot cost, and image path. `Build` snapshots its ordered gadget list. `Vote` permits only −1 and +1. Domain code imports no Quarkus, REST, Hibernate, or JPA types.
- **application:** use-case services that depend on domain types and outbound repository contracts. Services validate build references, enforce author ownership, normalize accounts, and orchestrate mutations. CDI and transaction annotations are pragmatic application-layer dependencies. AuthService uses Quarkus's bcrypt utility directly because a second hashing abstraction would not serve a current implementation need.
- **port/out:** the flat set of outbound infrastructure contracts: `UserRepository`, `BuildRepository`, `CommentRepository`, `GameDataRepository`, and `VoteRepository`. Centralizing this small set makes every application-to-infrastructure boundary visible in one package. These interfaces depend only on domain types and JDK types.
- **adapter/in/rest:** route/role annotations and conversion into service calls. Feature-specific transport records live in `request/` and `response/` subpackages beside their REST resource: for example, `build/request/BuildRequest` and `build/response/BuildResponse`. Entry points end in `RestResource`, including `AuthRestResource`, `BuildRestResource`, `CommentRestResource`, `CommentDeletionRestResource`, `GameDataRestResource`, and `VoteRestResource`. REST does not return JPA entities or password hashes. CurrentUser extracts a UUID from a verified JWT. Global error mapping remains directly under `adapter.in.rest` because it is shared rather than feature-specific.
- **adapter/out/db:** JPA entities named `*DbEntity`, persistence implementations named `*DbAdapter`, and MapStruct interfaces named `*DbMapper`. For example, `BuildDbAdapter` implements `BuildRepository` and uses `BuildDbMapper` with `BuildDbEntity`. Panache repositories remain where concise and EntityManager queries where more explicit. Only adapters know table names and PostgreSQL upsert syntax.

The outbound repositories are real boundaries between application behavior and storage. No input ports are currently used because REST resources call application services directly. Read-only game-data routes use `GameDataRepository` directly because they have no additional use-case rules.

`vote` and `comment` call BuildService to verify that their target build exists. Build responses use auth and game-data queries to assemble public author/loadout details and VoteService for scores. These are in-process calls. The build response currently reuses the game-data response DTO; this deliberate coupling keeps the same public shape without a parallel mapper hierarchy.

## Mapping and flow

MapStruct generates entity/domain mappings for users, builds, comments, and the three game-data entity types. It removes repeated field copying and keeps ORM records out of the domain. Persistence mappers use strict unmapped-target checking, so adding a target property requires an explicit mapping decision. Vote persistence writes its small validated record directly with an upsert, so it has no mapper. REST mappings are explicit where they are small or require assembling multiple module results.

`GameDataDbAdapter` and `GameDataDbMapper` remain combined for racers, machines, and gadgets, but the domain and port are strongly typed: `listRacers`/`findRacer`, `listMachines`/`findMachine`, and `listGadgets`/`findGadget`. Each existing Db entity maps to its corresponding domain record. The REST layer uses explicit `RacerResponse`, `MachineResponse`, and `GadgetResponse` DTOs, including when build responses assemble a loadout. The dynamic build-filter query uses JPA Criteria. JPQL entity references follow the renamed Java entities; table names and migrations are unchanged.

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

`users`, `racers`, `machines`, `gadgets`, `builds`, `votes`, and `comments` use UUID primary keys. `build_gadgets` uses `(build_id, position)` as its primary key: it is an ordered collection entry, not an independently addressable entity. Foreign keys enforce valid references. Build deletion cascades its collection, votes, and comments. User deletion is not implemented.

Racers and machines are independent foreign keys. `build_gadgets` is mapped as an ordered element collection of gadget UUIDs. No guessed slot budget, unique-gadget constraint, compatibility rules, or machine-part abstractions are present.

Votes have a unique `(user_id, build_id)` constraint and a −1/+1 check. PostgreSQL `INSERT … ON CONFLICT … DO UPDATE` makes repeated/concurrent votes update the same row atomically. Scores are always queried as `SUM(value)`; no client-owned or cached score exists.

Browse queries filter in PostgreSQL and paginate before response assembly. `BuildDbAdapter` uses JPA Criteria with one shared predicate helper for item and count queries. Search uses `locate(lower(title), lowercasedSearch)`, so user input remains a literal substring rather than SQL or `LIKE` wildcard syntax. Score sorting uses a correlated vote-sum subquery; all sorts retain creation time then ID as tie-breakers. The maximum page size is 50. Collection/detail assembly uses straightforward bounded lookups, so query count grows with page size; batching would be a measurable future optimization rather than a custom query framework now. There is no optimistic-lock version field: simultaneous edits by the same author use last-write-wins semantics.

Flyway V1 defines schema; V2 defines the supplied game dataset only. Hibernate runs schema validation. Seed descriptions, slot costs and image paths remain null. No fake application users enter migrations.

## Frontend

React Router owns page navigation. A small context holds current authentication; forms and page queries own local state. `api.ts` centralizes the bearer header, JSON handling and errors. `useLoad` aborts stale requests on route/filter changes. Build selection helpers preserve and reorder gadget IDs without game-rule calculations. React's normal text escaping is used for user content.

Routes cover Explore, My Builds, details, create/edit, register/login, and the requested public catalog browsing. Controls have labels, focus states, pending/error states and responsive layouts. Artwork uses only local `/assets/` paths, with a placeholder on failure. No global-state library or UI framework is used.

## Verification and limits

`AcceptanceTest` uses Quarkus's test runner, HTTP requests, actual JWT registration/login, Flyway, and PostgreSQL. `PackagedApiIT` repeats it against the production artifact. `DomainTest` checks immutable collection and vote/ownership behavior. Frontend tests check ordering and API errors/session expiration. The test run's external-database option requires a disposable database.

JWT logout is client-side token disposal; a copied token lasts until its one-hour expiry. There is no refresh/revocation system. Artwork, machine-part customization, gadget rules, advanced ranking and calculated stats remain outside the implemented scope.
