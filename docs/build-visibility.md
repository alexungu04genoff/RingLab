# Build privacy and publication

`BuildVisibility` has exactly `PRIVATE` and `PUBLIC`. A private build is a complete,
persisted setup that only its owner can access through RingLab. A public build is
eligible for community discovery and engagement. Item ownership and calculation
coverage remain independent: unowned items never prevent saving or publishing,
ownership badges describe the viewer's collection, and unsupported effects retain
their existing coverage labels.

## Persistence and chronology

V37 adds constrained, non-null `builds.visibility` and nullable
`first_published_at`. Every existing build becomes PUBLIC with first publication
equal to its existing `created_at`; content, engagement and relationships survive.
Enum persistence uses strings. The visibility index supports public candidate
selection; `(author_id, visibility)` supports owner lists. There is no publication
time index because the current bounded application ranking sorts candidate facts
in memory, rather than ordering those rows in SQL.

New PUBLIC builds receive creation time as their first publication. New PRIVATE
builds have no publication timestamp. First publication sets it once; content
edits, privatization and subsequent republication preserve it. `created_at`
continues to mean creation and `updated_at` changes on edits. Application writes
use PostgreSQL's microsecond timestamp precision, including API responses.

Community Newest and ranking chronology tie-breaks use first publication, never
`updated_at`. My Builds uses creation time for chronology, including tie-breaks.
Republishing an old public build does not move it to the top of Newest.

## HTTP and compatibility

| Operation | Contract |
| --- | --- |
| `POST /api/builds` | Explicit visibility accepted; omission defaults to PUBLIC. |
| `PUT /api/builds/{id}` | Owner-only; content and visibility save atomically. Omitted visibility preserves current server visibility. |
| `GET /api/builds` | PUBLIC only, including `authorId` requests made by that author. |
| `GET /api/builds?mine=true` | Authenticated actor's PUBLIC and PRIVATE builds; supports `visibility=ALL\|PUBLIC\|PRIVATE` and existing filters, sorting and pagination. |
| `GET /api/builds/{id}` | Public or authenticated owner access. |
| `GET /api/stats/persisted/{id}` | Same access as details, checked before calculation. |
| `DELETE /api/builds/{id}` | Owner-only; existing destructive/cascade semantics retained. |

`mine=true` plus `authorId` is rejected with 400. A visibility filter on the public
list is rejected. Unknown, null, non-string or incorrectly cased visibility values
are rejected with 400, rather than defaulting. Responses add `visibility` and
`firstPublishedAt`.

The editor sends explicit visibility and its loaded `expectedUpdatedAt` on edits.
A stale timestamp returns 400 with field `expectedUpdatedAt` and a reload message,
before any content or publication changes. Older clients can omit that timestamp;
their edits serialize on the same row lock, with last committed explicit values
winning. Omitted visibility always preserves the value read under the lock. This
uses the existing timestamp without adding a generic versioning system.

`CurrentUser.optionalId()` uses the verified JWT subject and existing session
validation. Anonymous public reads stay available. Invalid bearer tokens fail
authentication; a caller-supplied user ID is never an access credential.

## One access policy

`BuildAccessPolicy` centralizes public, owner-readable and owner-mutation checks.
Missing, deleted and somebody else's private build return `404 Build not found`.
Authorization precedes response enrichment and stored-build calculations.
Private unauthorized mutations use the same not-found boundary. Public non-owner
edits retain the existing ownership error.

Repository candidate filters exclude private builds before ranking, vote summaries,
pagination, hydration, totals and page stats. My Builds has an explicit repository
owner scope populated only from authenticated identity. Bookmarks filter PUBLIC in
their list/count and batch-status queries before page construction. If publication
changes between candidate and hydration reads, inaccessible results are omitted;
a later reload reconciles the concurrent count/page change.

Comments and votes require a PUBLIC parent, including for its owner. Existing
engagement remains stored while private. Direct comment deletion checks the parent
before checking comment ownership; a hidden parent and missing comment both return
`404 Comment not found`. Saved Builds accepts only PUBLIC builds. Privatization
hides bookmarks from lists, totals, stats and status; republication restores them
with the original `savedAt`. Bookmark deletion remains idempotent: a hidden or
unknown parent returns 204 without exposing or removing its retained relationship.

Owners can compare and remix their private builds. Comparison hydrates each ID
through the protected detail route; an unavailable member fails its comparison
without returning its metadata. Remix creation checks and locks source access.
The owner may create a PUBLIC remix from a PRIVATE source. Private source
attribution is omitted for every viewer, even the owner. The internal relationship
survives; making the source public restores attribution. Remixes never inherit
source visibility, and deleting a source retains the existing null-provenance rule.

## Transactions and caches

Content edits, deletion, remix-source validation, votes, comments and bookmark
mutations share a pessimistic parent-row lock. A waiting community mutation reads
fresh publication state after the earlier transaction commits. Engagement committed
before privatization is retained; a vote that acquires the lock afterwards fails.

Publication transitions and deletion emit `BuildPublicationChanged` inside the
transaction. The synchronized community cache invalidates on CDI `AFTER_SUCCESS`,
including private source attribution in cached remixes. Rolled-back changes do not
invalidate it. The next Top Community, Discord formatter or `excludeTop` request
rebuilds its snapshot with public candidates and a fresh ETag. Republication restores
eligibility; ranking decides the winners. This cache is local to the single
application instance, consistent with the current deployment architecture.

Persisted build routes, their child resources, My Builds, saved routes and
stored-build stats return `Cache-Control: private, no-store` and vary on
Authorization, including public build details because access can be revoked.
Community snapshots retain `public, no-cache, must-revalidate` with revised ETags.
No new endpoint or rate-limit bucket is introduced for visibility mutations.

Making a build private removes it from RingLab's active public surfaces. RingLab
cannot recall content already viewed, copied or shared outside the service, including
Discord messages. An already open browser may retain previously rendered public
content until its next request; subsequent reads and mutations enforce current access.

## UI and fixtures

Build Essentials defaults new builds to Public and offers Private with owner-only
wording. Saving a new private build says “Save private build”; normal edits say
“Save changes”. Only publication transitions require confirmation. Private details
and cards retain owner workflows but hide active engagement and public sharing.
My Builds filters are URL-backed and use backend totals.

Session replacement remounts private consumers and comparison selection; details,
comparison and editor loads are invalidated on identity/session changes. Requests
from obsolete components are aborted or ignored. A stale public mutation receives
a safe unavailable message. Legacy responses without visibility default safely to
PRIVATE in the editor; historical public-only display fixtures remain readable.

Existing domain fixture constructors default to PUBLIC with creation-time
publication. Direct persistence fixtures explicitly supply both fields. Demo
planners still describe public setups; the local runner sends PUBLIC on creation,
preserves old content fingerprints, skips removed/private managed fixtures and
compares public API identities with only public database rows. Its explicit fixture
timestamp operation also updates publication time, restricted to unchanged public
fixtures whose publication date still equals creation. No demo data is seeded by
this feature's migration or normal application startup.

## Two-account local acceptance

Use owner A and another account B in separate browser sessions at the local/dev URL.

1. A creates a PRIVATE future build containing an item marked Not owned.
2. A finds it in My Builds → Private; verify All/Public/Private totals and pagination.
3. B and an anonymous browser open its copied URL and receive ordinary not found.
4. Search its exact unique title in public Explore and public author filtering: no result.
5. A compares it with another private/public build and remixes it. Confirm private
   source attribution is absent from a public remix.
6. A publishes it, accepting the community confirmation. Record `firstPublishedAt`.
7. B views, saves, votes, comments and remixes it.
8. Verify public discovery and ranking eligibility; Top 3 placement is not guaranteed.
9. A makes it private and reads the retention/external-copy confirmation.
10. B refreshes or tries another mutation: access fails safely. Public snapshot and
    Discord formatter output stop including it immediately if it was a winner.
11. B's Saved Builds and status omit it, with correct totals and full eligible pages.
12. A sees no active votes/comments/save controls; existing engagement is retained.
13. A republishes it.
14. B's bookmark with original saved time, vote and comment become available again.
15. Verify the original `firstPublishedAt` remains unchanged. Check stale edit tabs,
    logout, same-tab account switching and desktop/tablet/390px layouts.

Production deployment is a separate action after product/privacy review.

## Verification on 9 October 2026

Current main's CI was green before this feature branch was created. The final
implementation passed:

- `mvn -f backend/pom.xml verify`: 557 unit/integration tests and 22 packaged API
  tests, with no failures, errors or skips. This includes ArchitectureTest,
  V36-to-V37 and fresh-schema migration tests, anonymous/owner/other-user HTTP
  access checks, mixed visibility pagination/stats/bookmark checks, immediate
  snapshot invalidation, and the concurrent privatization/vote characterization.
- `npm run test:coverage -- --maxWorkers=2` from `frontend`: 450 tests across 45
  files. Coverage includes both transition confirmations and their keyboard focus,
  private creation/editing/cards/details/comparison, session replacement, stale
  mutations and My Builds URL/query behavior.
- `npm run build` from `frontend`, `git diff --check`, and PowerShell parser
  validation of `SeedDemoData.ps1`.

Existing coverage thresholds were unchanged. Backend JaCoCo: 97.57% lines,
90.22% branches, 97.33% instructions. Frontend: 95.44% lines/statements,
92.40% branches, 90.19% functions. Reports are at
`backend/target/site/jacoco/index.html` and `frontend/coverage/index.html`.

Backend verification used the disposable database
`ringlab_visibility_final_20261009`; it did not reset the development database.
Packaged startup still emits the existing ignored
`quarkus.log.category.io.quarkus.level` configuration warning. Negative database
constraint tests also emit expected Hibernate warnings.

The existing local PostgreSQL, Quarkus and Vite processes were reused. The frontend
and build API, including page stats, responded through `http://localhost:5173`.
A temporary local build was created privately, published, made private again,
checked after logout, and then deleted. Desktop (1171px), tablet (768px), and mobile
(390px) editor/detail checks found no horizontal overflow after the responsive
adjustments. Existing demo builds and account collection settings were preserved.

For manual review, use the two-account checklist above. Focused IntelliJ entry
points are `BuildServiceTest`, `BuildVisibilityMigrationIntegrationTest`,
`RepositoryContractIntegrationTest`, `CommunityApiIntegrationTest`,
`AcceptanceTest`, and `ArchitectureTest`. The full verification commands can be
repeated from the repository root:

```powershell
mvn -f backend/pom.xml verify
npm --prefix frontend run test:coverage -- --maxWorkers=2
npm --prefix frontend run build
git diff --check
```

Before repeating Maven verification, configure both the Quarkus test datasource
and packaged application's database/key environment for a disposable database,
as described in README's packaged API test instructions. No broader requested
automated suite was intentionally skipped. Manual two-account product/privacy
review remains pending; no production deployment was performed.
