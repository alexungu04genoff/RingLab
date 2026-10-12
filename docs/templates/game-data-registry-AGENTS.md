# Game Data Registry

This is a ready-to-use template for the future Registry repository's root `AGENTS.md`. While stored under this filename in RingLab, it is reference material only and does not govern RingLab. Creating the Registry repository or service requires its own task.

## Purpose and technology

- Internal data-management application for a handful of trusted moderators, supplying versioned, reviewed game data to RingLab through a published API. Supporting infrastructure, not the primary thesis implementation.
- Java 21 / Quarkus backend, Registry-owned MongoDB, simple browser admin UI, independent deployment and credentials. Use separate moderator accounts, no Google authentication and no public registration.
- Prefer straightforward existing approaches. RingLab continues to own stat and recommendation calculations; the Registry manages reviewed source data.

## Admin UI

- Functionality over visual perfection: login, browsing/search, spreadsheet-like editing, saving drafts, validation feedback, diff review, publication and moderator account management.
- Basic readability and accessibility matter. Desktop-first with basic smaller-width usability is sufficient. Do not copy RingLab's elaborate design system or overinvest in animation, artwork and responsive polish.
- No automated React component/UI suite, Playwright/Cypress E2E suite, screenshot regression suite or frontend coverage targets. Still run frontend type-check/build checks and a small manual functional smoke test of important admin actions before release.
- This UI-testing exception applies only to Registry, never the main RingLab frontend.

## Backend quality and security

- Enforce authentication/session security, moderator roles and draft ownership/permissions server-side. Preserve provenance, uncertainty and explicit unknown values; unknown never means zero.
- Thoroughly test optimistic concurrency, validation, diffs, publication eligibility and immutability, idempotency, concurrent publication, checksums/schema contracts, audit history, real database transactions, failure and recovery.
- Do not sacrifice publication integrity to simplify the UI. Use meaningful unit and API/integration tests for the relevant boundaries.

## Publication contract

- Keep game version (for example `1.4.1`) separate from Registry revision (for example `19`, `20`, `21`).
- Draft edits never change published data. Publication creates an immutable revision with a complete audit history.
- Authorized PUBLISHER/ADMIN moderators may self-publish initially; do not invent a mandatory second reviewer for ordinary publication.
- Future RingLab integration will activate ordinary safe publications automatically only after validation. This is a future integration requirement, not permission to perform a cutover now.
- Corrections to protected published facts require explicit reviewed authorization. Never silently introduce unreviewed numerical/mechanical changes into RingLab or mutate an old published revision.

## Release

- Keep deployment and credentials independent from RingLab. Verify backend/API integrity, frontend build and manual admin smoke checks before a scoped release.
- Back up persistent data before risky migrations; validate, deploy and verify. A code push alone does not authorize production release, protected data corrections or Registry cutover.
