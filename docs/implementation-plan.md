# MVP implementation plan

The initial workspace was empty. Build a local Java 21 / Quarkus modular monolith and a React / TypeScript Vite client.

1. Flyway schema and separate exact game-data seeds.
2. Auth, game-data, build, vote and comment modules. Domain records are framework-independent; application services enforce rules; persistence ports isolate ORM adapters; REST uses validated DTOs.
3. Explore, details, create/edit, registration/login and My Builds pages with responsive CSS and local artwork placeholders.
4. PostgreSQL integration tests for auth, ownership, references, votes and comments; frontend tests and production builds.
5. Document actual boundaries, startup, security and intentional simplifications.

Tables: users, racers, machines, gadgets, builds, build_gadgets (ordered), votes, comments. UUID identifiers; foreign keys, unique account identifiers and votes; check vote values.

Endpoints: POST auth/register, auth/login, auth/logout; GET auth/me; GET racers, machines, gadgets; GET/POST builds; GET/PUT/DELETE builds/{id}; PUT/DELETE builds/{id}/vote; GET/POST builds/{id}/comments; DELETE comments/{id}. All under /api. GET builds supports title search, racerId, machineId, authorId, newest/score sorting, page and size.

No game-stat invention, gadget capacity rules, machine parts, seeded users, or speculative infrastructure.
