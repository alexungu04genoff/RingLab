# Private Saved Builds

My Builds contains authored builds. Saved Builds contains private bookmarks of live
published builds, including the user's own builds. Compare remains a temporary
two-build selection. A bookmark neither copies nor modifies a build, its author,
votes, timestamps, Wilson ranking, or Top 3 eligibility. Remix creates independent
variations. No public bookmark counts or other users' saved lists are exposed.

## Schema and queries

V28 adds `saved_builds(user_id, build_id, saved_at)`. The composite primary key
prevents duplicate saves; both foreign keys use `ON DELETE CASCADE`. PostgreSQL
generates saved_at with `clock_timestamp()`. An upsert with `DO NOTHING` preserves
the original timestamp for repeat/concurrent saves. Removal followed by saving
creates a new timestamp. Only the source-build foreign-key failure translates to
not-found; unrelated constraint failures are not disguised.

Indexes cover `(user_id, saved_at DESC, build_id ASC)` and source `build_id` deletion.
List/count criteria use the existing case-insensitive literal substring search on
title, racer, machine-source names and gadget names. `%` and `_` stay literal.
Optional game-version matching is exact. Pagination occurs before one bounded
`findAll` hydration; hydration order is restored to bookmark chronology. A source
deleted between reads is omitted safely; a later reload reconciles the count.
Status is one IN query for a nonempty batch and zero queries for an empty batch.
Build response assembly retains existing bounded lookups; stats use shared
`buildPage` enrichment, with racer/part maps loaded per distinct patch.

## HTTP contract

All routes require the normal bearer session and derive the actor exclusively
from server-side `CurrentUser`. No userId selector is accepted. Successful private
responses use `Cache-Control: private, no-store`; list/status/save also vary on
Authorization. Public build browsing and public snapshots remain authentication
independent, with no `isSaved` or `savedAt` fields.

| Method and path | Result |
| --- | --- |
| `GET /api/saved-builds` | `{items:[{build:BuildResponse,savedAt}],total,page,size,statsByBuildId,statsError}` |
| `GET /api/saved-builds/status?buildId=UUID&buildId=UUID` | `{savedIds:[UUID]}`; absent and nonexistent IDs are unsaved; empty batch returns an empty set |
| `PUT /api/saved-builds/{buildId}` | 200 `{buildId,savedAt}`; repeat save preserves timestamp; missing build 404 |
| `DELETE /api/saved-builds/{buildId}` | 204, including absent bookmark or already-deleted source |

List accepts `search` (max 120 characters), `gameVersionId`, zero-based `page`
(0–100000, default 0), and `size` (1–50, default 12). Order is saved_at descending,
then build_id ascending. Top 3 builds are not excluded. Optional stats failure keeps
the list and returns `statsByBuildId:null` and a safe `statsError`; success returns
the per-build map and `statsError:null`. Status allows at most 50 **distinct** UUIDs.
Malformed IDs and invalid bounds return 400; unauthenticated requests return 401.
Read throttling uses the existing general allowance per authenticated actor;
mutations use the existing authenticated mutation bucket. The Vite allowlist adds
only the saved-builds family and continues to reject fixture tooling.

## Frontend and session behavior

The protected `/saved-builds` page has a bookmark navigation icon, private-list
explanation, saved dates, search, patch filtering, pagination and comparison tray.
Card/detail bookmark controls are compact and separate from title navigation,
comparison, voting and owner actions. Known states use aria-pressed; the saved state
also has a check mark. Errors are retryable and unknown failures are never shown as
confirmed unsaved. Signed-out Save links use the relevant internal build return
path; login does not trigger saving. Login rejects protocol-relative, backslash,
whitespace/control-character and external return paths.

The provider batches distinct visible IDs, disables conflicting pending actions,
and shares confirmed success across duplicate cards. User/session replacement
remounts private consumers, aborts requests and ignores obsolete completions.
An accepted server mutation may still finish after logout; the client does not
claim rollback. The database provides persistence across sessions/devices.

Removal reloads the list/count and moves an invalid last page backward. Returning
focus refreshes live source edits/deletions. Search, patch and page remain in the
URL and safe details/compare origins. Comparison selection survives a round trip.

## Verification

Verification on 2026-09-26 used disposable `ringlab_saved_test_20260926` for automated
tests and `ringlab_saved_browser_20260926` for the browser walkthrough. Both used
synthetic accounts, temporary JWT keys and mocked/test SMTP. No production or
ordinary development data was used for mutating checks.

Results:

- Baselines: 15 targeted backend tests and 19 frontend tests passed.
- Batch A: API/privacy/rate-limit checks passed. The new persistence fixture first
  needed its column names corrected; the corrected concurrency/cascade test passed.
- Focused service and optional-stats failure tests passed. Status query counting,
  equal-timestamp ID ordering, catalog/patch filtering and canonical stats equality
  also passed in the full run.
- Full frontend: **221 tests, 29 files passed**. Coverage: 90.59% statements/lines,
  91.23% branches, 85.26% functions; existing gates unchanged.
- Full Maven verify: **253 unit/integration tests + 18 packaged API tests passed**.
  JaCoCo: 96.93% instructions, 96.97% lines, 86.04% branches; existing gates unchanged.
  An initial architecture-check failure was corrected with an explicit allowance
  for `CriteriaQuery.orderBy` in `SavedBuildDbAdapter` only. Public-ranking policy
  dependencies, comparators and local sorting remain prohibited in persistence.
- After the final responsive action grouping, 18 targeted bookmark/comparison
  frontend tests and the production build passed. The full coverage numbers above
  precede that markup-only grouping.
- `git diff --check` passed; Git emitted only existing LF/CRLF conversion notices.

Actual browser checks (separate from jsdom):

1. At `http://127.0.0.1:5174`, signed in as synthetic `bookmark_alice`, saved a
   `bookmark_author` setup from Top 3, found it in Saved Builds and refreshed.
2. Logged out, signed in as `bookmark_bob`, confirmed an empty private list, then
   returned to Alice and confirmed her bookmark remained.
3. Selected the saved build and a second setup, opened Compare from Saved Builds,
   returned via its back link and confirmed both selections remained.
4. Used Tab between card actions/title and Enter to remove the bookmark; the private
   list became empty. Both source builds retained their author, title, zero votes
   and unedited timestamps (PostgreSQL stores timestamps at microsecond precision).
5. Clicked Save while signed out, logged in, returned to the correct build detail
   with confirmed `aria-pressed=false`, then explicitly saved from details.
6. Checked 1440px desktop, 390px mobile and 320px reflow. No horizontal overflow on
   Saved Builds; actions wrap together. Native zoom shortcuts had no effect in the
   available in-app browser, so actual high-zoom behavior remains a manual check.
   Physical touchscreen and screen-reader testing were not performed.
7. Verified the frontend API proxy responds and fixture tooling returns 404.

Screenshots are ignored local artifacts: `backend/target/saved-builds-desktop.png`
and `backend/target/saved-builds-mobile.png`. Local review runs on port 5174 with the
isolated backend on 8082. The ordinary backend was stopped before adding V28 to avoid
hot-reload migration of the ordinary development database; it was not restarted.
The isolated stack remains running for review, with mocked mail and no real Steam
news calls. News therefore intentionally displays its unavailable state.

Commands (PowerShell, substitute a **disposable** database for any rerun):

```powershell
cd E:\RingLab\frontend
npm run test:coverage
npm run build
cd E:\RingLab
$env:DB_URL='jdbc:postgresql://localhost:5432/ringlab_saved_test_20260926'
$env:DB_USER='ringlab'
$env:DB_PASSWORD='ringlab'
$env:PUBLIC_BASE_URL='http://localhost:5174'
mvn -f backend/pom.xml '-Dmaven.repo.local=C:/Users/Alex/.m2/repository' '-Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/ringlab_saved_test_20260926' '-Dquarkus.datasource.username=ringlab' '-Dquarkus.datasource.password=ringlab' '-Dquarkus.datasource.devservices.enabled=false' verify
git diff --check
```

Coverage reports: `frontend/coverage/index.html` and
`backend/target/site/jacoco/index.html`. Logs: `frontend/saved-coverage.log` and
`backend/target/saved-full-verify.log`.

Changed source groups: V28 migration; bookmark port/service/entity/adapter/resource;
shared build-filter reuse and rate policy; frontend provider, icon, page, card/detail
actions, router, safe origins/login return and responsive styles; Vite proxy; focused
service/persistence/REST/frontend tests and shared packaged API contract; architecture
guard; README, architecture, schema and walkthrough documentation. The pre-existing
comparison alignment edit in styles.css was preserved. Production release uses the
repository's reviewed manual deployment workflow after successful main CI and image
publication.
