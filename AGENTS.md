# RingLab

RingLab is the player-facing game-build community application and the main bachelor's thesis implementation. Favor correctness, understandable code and an interface the author can confidently demonstrate and explain.

Read only the documentation relevant to the task. [README](README.md) covers setup and checks; [architecture](docs/architecture.md) describes implemented boundaries. Update the relevant documentation when behavior or architecture changes; the architecture atlas is a reference, not required reading for every edit.

## Architecture and implementation

- Java 21, Quarkus, PostgreSQL and Flyway; React, TypeScript, Vite, React Router and normal CSS. Keep the modular monolith; do not propose microservices without a demonstrated need.
- Dependency flow: inbound adapter → inbound port → application/domain → outbound port → outbound adapter. Keep `dev.ringlab.domain`, `application`, flat `port/in` and `port/out`, and `adapter/in` and `adapter/out` visible. Ports represent meaningful boundaries, not every internal helper.
- Domain code depends on the JDK only. Application code orchestrates use cases without depending on adapters. REST resources handle transport/auth context, and DB adapters handle persistence; neither owns core business rules.
- Keep explicit domain and transport types (`Racer`, `Machine`, `MachinePart`, `Gadget`, etc.), immutable records where appropriate, and existing responsibility-based names. Do not collapse different concepts into nullable generic models. Keep API calls centralized in the frontend API layer.
- Flyway owns the schema; preserve migration history and relational constraints. MapStruct persistence mappers retain `ReportingPolicy.ERROR`. Use Lombok narrowly for obvious constructor/logging boilerplate, never to hide behavior or model JPA entities with `@Data`.
- Preserve authentication/session checks, backend ownership authorization, safe REST errors and non-sensitive Quarkus/JBoss diagnostics. Derive the actor from authenticated context, never client-supplied ownership IDs.
- Preserve literal title search, filtering, pagination (maximum 50), current ranking and chronology contracts; consult the relevant browsing/publication docs before changing them. Votes remain one per user/build; comments remain flat.

## Domain and privacy

- Preserve machine composition: parts share a known source-machine racing type; BOOST requires FRONT + REAR and no TIRE, other types require all three. Racer type is independent of machine compatibility. Preserve ordered gadgets and the two-row, three-slot Gadget Plate constraints.
- Keep collection ownership separate from build legality: unowned items do not prevent saving/publishing. Recommendation ownership constraints still apply. See [recommendations](docs/auto-builder.md).
- Enforce PRIVATE/PUBLIC access server-side across lists, details, persisted calculations, comments, votes, bookmarks, comparison, remix provenance and community APIs. Preserve existing private builds and non-disclosure behavior. [Build visibility](docs/build-visibility.md) is the detailed contract.
- Unknown data never silently becomes zero. Keep base stats, reviewed passive effects and scenario effects distinct. Unsupported effects retain uncertainty; a data row or source label does not grant calculation permission for a game version.
- Preserve Strict lexicographic recommendations and [Balanced sequential survivor thresholds](docs/balanced-auto-builder.md), including ownership/locks, passive-only objectives and honest proof/budget status. Do not substitute weighted scoring or scenario effects.

## Game data

- Preserve canonical files, stable identities, source ledgers, provenance and uncertainty classifications. Do not invent stats, overwrite disputed facts or reinterpret historical placeholders as verified patch data.
- For catalog, versioned stats/rules, importer PLAN/APPLY or parity work, use the repository's `ringlab-game-data` skill. [Game-data maintenance](game-data/README.md) defines validation, reviewed plans, approval tokens and protected published facts.
- No automatic CSV import during application startup. A push or deployment does not authorize data corrections, publication activation or Registry cutover.
- Future Registry integration replaces the maintenance source; stat and recommendation calculations remain inside RingLab. Registry is not implemented by these instructions.
- Use reviewed local frontend assets. Link external articles; do not scrape or add copyrighted media without user-provided content or explicit approval.

## Main application UI

- UI quality is a major priority: coherent design, consistent chips/colors/typography, clear loading/error/empty/success feedback, accessible controls and usable desktop/tablet/mobile layouts. Avoid clipping, awkward wrapping and horizontal overflow.
- Preserve React escaping and explicit API-aligned frontend types. Do not sacrifice user experience merely to reduce implementation effort.
- For UI changes, run appropriate existing frontend tests plus real browser/visual checks when available. If browser checks are unavailable, report the gap. Preserve frontend coverage requirements; the future Registry UI exception does not apply here.
- Consult [frontend organization](docs/frontend-organization.md) when changing frontend structure.

## Verification

- Behavior changes need meaningful tests. Use focused pure JUnit 5 tests for domain/application logic; retain architecture, REST/security, persistence, transaction, migration and packaged API tests for their real boundaries.
- Follow nearby Java tests: currently JUnit assertions and readable in-memory doubles. Use an installed `java-unit-tests` skill when available and applicable. Mockito/AssertJ should follow established local usage; do not add dependencies or rewrite tests solely for stylistic consistency.
- Run checks proportionate to the change, expanding for affected boundaries and releases. Do not weaken tests, skip failures or lower existing coverage gates to pass CI. Keep test databases disposable and separate from development/production data.
- Focused backend example, from the root: `mvn -f backend/pom.xml "-Dtest=BuildServiceTest" test` (substitute affected classes). Full backend/release verification: `mvn -f backend/pom.xml verify`, with the isolated database and key setup in [README tests](README.md#tests) and [packaged API tests](README.md#production-builds-and-packaged-api-tests).
- From `frontend`: `npm test -- <affected-test-path>`; broader checks are `npm run test:coverage` and `npm run build`. Gates live in `backend/pom.xml` and `frontend/vite.config.ts`; reports are `backend/target/site/jacoco/index.html` and `frontend/coverage/index.html`.
- For instruction-only changes, validate instruction/skill discovery, syntax, links and diff; application suites and infrastructure startup are unnecessary. Report checks run and any relevant remaining manual verification.

## Delivery and deployment

- Commit and push verified work autonomously, using a `codex/` branch for isolated feature work. When promoting to local development, fast-forward a clean `main` checkout; never rewrite history without an explicit request.
- After major application changes, refresh the documented local PostgreSQL/Quarkus/Vite stack. Reuse healthy services, preserve volumes/data/keys/tunnels, use persistent background processes (hidden on Windows), and verify frontend and API responses through the proxy before reporting `http://localhost:5173` available.
- Production releases must be explicitly in task scope and follow [production operations](docs/production.md): protected workflow, successful release checks, immutable image digest/commit verification and post-deploy checks. For risky production migrations: back up, validate, deploy, verify.
- **Stage/staging means real production at https://ringlabgarage.com**, not a preview port or development tunnel. An explicit stage release request authorizes that protected production workflow, subject to its controls.
- Do not deploy every commit or automatically activate game-data corrections/Registry cutover. Instruction-only changes need no deployment.
