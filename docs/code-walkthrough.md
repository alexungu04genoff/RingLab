# Backend code walkthrough

Use these paths in order for a short thesis demonstration. All names refer to
implemented code. Tests can be opened beside the use case and run from IntelliJ.

## Private Saved Builds

1. `frontend/src/SavedBuilds.tsx`: `SaveBuildButton` registers a visible build ID.
   The provider deduplicates IDs in batches of at most 50, then sends an authenticated
   PUT or DELETE only after an explicit click. Pending controls share state; failures
   preserve the last confirmed value and expose retry. Signed-out links return to
   the build after normal login without saving automatically.
2. `adapter/in/rest/build/SavedBuildRestResource`: all four routes require the user
   role. `CurrentUser.id()` selects the actor; a client-supplied userId is irrelevant.
3. `application/build/SavedBuildService`: saving first verifies the live build exists;
   removing calls only the bookmark repository. Listing hydrates one bounded page
   with `BuildRepository.findAll`, then restores bookmark chronology.
4. `port/out/SavedBuildRepository` and `adapter/out/db/build/SavedBuildDbAdapter`:
   PostgreSQL `ON CONFLICT DO NOTHING` preserves the first timestamp under concurrent
   saves. V28's composite primary key enforces uniqueness and both foreign keys
   cascade. Only the source-build foreign-key violation maps to build-not-found.
5. The private list reuses `BuildResponseAssembler` and `BaseStatsService.buildPage`.
   Author credit and canonical patch stats remain those of the live build. The
   response includes private/no-store headers; no bookmark fields enter the public
   community snapshot.
6. `frontend/src/pages/SavedBuildsPage.tsx`: authenticated list, search/patch filters,
   bookmark dates, pagination reconciliation, existing cards and shared comparison.
   Safe browse-origin handling recognizes `/saved-builds` and its query string.

Run `SavedBuildServiceTest`, `SavedBuildIntegrationTest`, and the bookmark contract
in `ApiContract` alongside `SavedBuilds.test.tsx` and `SavedBuildsPage.test.tsx`.

## Google sign-in and existing accounts

Follow `AuthRestResource.google` into
[ExternalAuthService.login](../backend/src/main/java/dev/ringlab/application/auth/ExternalAuthService.java).
The Google adapter verifies the token before any account lookup or write. An existing
provider/subject link takes precedence over email. Otherwise, an exact verified Gmail
match may link to an already-verified RingLab account; other collisions require an
authenticated link. The existing account is returned unchanged, preserving ownership.

For a new account, [ExternalAccountRegistration](../backend/src/main/java/dev/ringlab/application/auth/ExternalAccountRegistration.java)
selects a readable available username and creates a passwordless user. The parent
service creates the provider link in the same transaction. It then returns a domain
`User`; only REST creates the JWT and response DTO. Neither class stores request data
in bean fields.

**Tests:** `ExternalAuthServiceTest` checks both accepted and rejected linking cases,
`GoogleIdentityVerificationAdapterTest` checks signed credentials, and
`GoogleAuthIntegrationTest` checks persistence and the REST session boundary.

On the frontend, `AuthPage` delegates submissions to `useAuthForm`. The hook ignores
abandoned requests and responses belonging to an older session. `BuildEditor` delegates
source loading to `useBuildDraft`, so edit/remix navigation and draft conversion can
be read separately from the form layout.

For local registration, follow `AuthService.register` into
[EmailVerificationService.issueVerification](../backend/src/main/java/dev/ringlab/application/auth/EmailVerificationService.java).
The account and token digest share the registration transaction. `verifyEmail` locks
and consumes the token; `resendVerification` replaces it only for an unverified local
account. `AuthRestResource` sends the email after the service transaction completes.
`AuthServiceTest` exercises these collaborating services with in-memory repositories,
including expiry, single use, resend invalidation and the local login verification gate.

Explore separates its searchable control in `SearchableFilter` from URL/preference
rules in `exploreFilters.ts`. The page retains loading, navigation and layout. Use
`SearchableFilter.test.tsx` for keyboard selection and cancellation, and
`ExploreNavigation.test.tsx` for stale preferences and removing a saved sort.

## 1. Save or edit a build: the complete boundary crossing

Start at [BuildRestResource.create/edit](../backend/src/main/java/dev/ringlab/adapter/in/rest/build/BuildRestResource.java).
The resource owns routes, Bean Validation and the `user` role. `CurrentUser.id()`
supplies the authenticated actor; the request cannot choose an author.

Open [BuildService.create/edit](../backend/src/main/java/dev/ringlab/application/build/BuildService.java),
then [BuildDraftValidator.validate](../backend/src/main/java/dev/ringlab/application/build/BuildDraftValidator.java).
The service owns the transaction, ownership, remix-source lookup, IDs, timestamps
and save. The validator checks text, references, machine composition, patch and
gadgets in that order. Edit checks ownership before validating the new draft,
retains `createdAt` and provenance, and changes `updatedAt`.

Follow `builds.save(build)` through [BuildRepository](../backend/src/main/java/dev/ringlab/port/out/BuildRepository.java)
to [BuildDbAdapter.save](../backend/src/main/java/dev/ringlab/adapter/out/db/build/BuildDbAdapter.java).
MapStruct's `BuildDbMapper` converts to `BuildDbEntity`; the adapter merges and
flushes, translating only the known missing-remix-parent constraint. Flyway's
constraints are the final consistency boundary. A source deletion clears remix
provenance without deleting the remix.

Finally open [BuildResponseAssembler.assemble](../backend/src/main/java/dev/ringlab/adapter/in/rest/build/BuildResponseAssembler.java).
It enriches the saved build with public author/catalog data, optional provenance
and votes. It belongs in REST because it returns HTTP response records. Assembly
still occurs after the service transaction; a disappeared optional source becomes
null, whereas requesting a missing build remains an error.

**Tests:** [BuildServiceTest](../backend/src/test/java/dev/ringlab/application/BuildServiceTest.java),
[BuildRestResourceTest](../backend/src/test/java/dev/ringlab/adapter/in/rest/build/BuildRestResourceTest.java),
[BuildResponseAssemblerTest](../backend/src/test/java/dev/ringlab/adapter/in/rest/build/BuildResponseAssemblerTest.java),
[ParentDeletionIntegrationTest](../backend/src/test/java/dev/ringlab/ParentDeletionIntegrationTest.java),
and the shared [ApiContract](../backend/src/test/java/dev/ringlab/ApiContract.java).

**Tradeoff:** explicit bounded response lookups are easy to follow but grow with
page size. This refactor preserves them; it does not claim a query-speed improvement.

## 2. Validate machine parts and the Gadget Plate

In `BuildDraftValidator`, open `validateMachineParts` and `validateGadgets`.
[MachineCompatibility.requireCompatible](../backend/src/main/java/dev/ringlab/application/gamedata/MachineCompatibility.java)
resolves each part's source machine through `GameDataRepository`. Parts must have
the same known machine `RacingType`; the racer's type is independent. Parts may
come from different source machines of that type.
[MachineComposition.requiredSlots](../backend/src/main/java/dev/ringlab/domain/gamedata/MachineComposition.java)
requires FRONT/REAR for BOOST and FRONT/REAR/TIRE for the other types.

Gadget IDs must be distinct and resolve to known, valid current costs.
[GadgetPlate.canFit](../backend/src/main/java/dev/ringlab/domain/build/GadgetPlate.java)
checks placement into two rows of three slots. Its internal cost sorting does not
reorder the build's gadget list; `Build` takes an immutable ordered snapshot.

**Tests:** `BuildServiceTest` covers every machine type, mixed compatible sources,
invalid references, ownership and validation precedence; [GadgetPlateTest](../backend/src/test/java/dev/ringlab/domain/build/GadgetPlateTest.java)
covers valid and impossible placements.

**Tradeoff:** publishing, incomplete stats previews and community eligibility are
different policies. They share canonical rules where applicable, not one universal
validator. Gadget effects and historical gadget costs remain unimplemented.

## 3. Rank browsing results and select community builds

Start at `BuildService.list`: validate the query, retrieve candidate facts and vote
counts, rank globally, paginate, then `hydrateInRankedOrder`. `pageSummaries` reuses
ranking counts; NEWEST fetches counts only for hydrated page items.
`BuildDbAdapter.searchCandidates` owns filtering, including literal case-insensitive
search across title/racer/source-machine/gadget names; it does not own ranking.

Open [BuildRanking.comparator](../backend/src/main/java/dev/ringlab/domain/build/ranking/BuildRanking.java)
and [WilsonScore.lowerBound](../backend/src/main/java/dev/ringlab/domain/build/ranking/WilsonScore.java).
BEST_RATED uses full-precision Wilson, patch release date, fewer downvotes at zero
Wilson, creation time, then UUID. The existing patch tie-breaker is preserved.

Next open [CommunitySelectionService.select](../backend/src/main/java/dev/ringlab/application/community/CommunitySelectionService.java).
It uses that same comparator, hydrates at most 50 candidates at a time, and stops
after three eligible builds. [CommunitySnapshotAssembler](../backend/src/main/java/dev/ringlab/application/community/CommunitySnapshotAssembler.java)
is created inside the selection transaction: `eligible` delegates to the existing
`CommunityEligibility` policy, and `assemble` reuses author and per-version stats
lookups. Missing hydration, controlled demos and invalid configurations are skipped;
versionless/unknown-stat builds can remain eligible.

[CommunitySnapshotCache.get](../backend/src/main/java/dev/ringlab/application/community/CommunitySnapshotCache.java)
publishes only after `select()`'s REQUIRES_NEW transaction returns. It retains its
existing synchronized expiry/failure behavior. Selection lookup maps are never
stored in this shared cache or an application-scoped assembler.

**Tests:** `BuildServiceTest`, [BuildRankingTest](../backend/src/test/java/dev/ringlab/domain/build/ranking/BuildRankingTest.java),
[CommunitySelectionTest](../backend/src/test/java/dev/ringlab/application/community/CommunitySelectionTest.java),
[CommunitySnapshotCacheTest](../backend/src/test/java/dev/ringlab/application/community/CommunitySnapshotCacheTest.java),
`BuildRankingIntegrationTest` and `CommunityApiIntegrationTest`.

**Tradeoff:** all matching lightweight candidates are ranked in memory. Hydration
is bounded, but candidate ranking is not; no cache or query redesign is introduced.

## 4. Resolve a version and calculate base stats

Start at [BaseStatsRestResource.build/catalog](../backend/src/main/java/dev/ringlab/adapter/in/rest/gamedata/BaseStatsRestResource.java),
then [BaseStatsService.buildBreakdown/catalog](../backend/src/main/java/dev/ringlab/application/gamedata/BaseStatsService.java).
The service checks the version and supplied references before reading
[BaseStatsRepository](../backend/src/main/java/dev/ringlab/port/out/BaseStatsRepository.java).
[BaseStatsDbAdapter](../backend/src/main/java/dev/ringlab/adapter/out/db/gamedata/BaseStatsDbAdapter.java)
owns the two parameterized historical-stat queries.

[BaseStatsBreakdown.calculate](../backend/src/main/java/dev/ringlab/domain/gamedata/BaseStatsBreakdown.java)
is pure and shared by preview and community assembly. It returns character,
machine and total contributions using [BaseStats.sum](../backend/src/main/java/dev/ringlab/domain/gamedata/BaseStats.java),
the only arithmetic implementation. BigDecimal precision is retained; null means
unknown, not zero, and affects only that field. A null tire is omitted; a selected
tire with no historical row contributes unknowns. No version means no stats reads
or fallback, but invalid supplied catalog IDs still fail validation.

**Tests:** [BaseStatsBreakdownTest](../backend/src/test/java/dev/ringlab/domain/gamedata/BaseStatsBreakdownTest.java),
[BaseStatsServiceTest](../backend/src/test/java/dev/ringlab/application/BaseStatsServiceTest.java),
`BaseStatsTest` and `BaseStatsIntegrationTest`.

**Tradeoff:** preview accepts incomplete and legacy incompatible selections. It
does not determine publishability or calculate gadget-modified/effective stats.

## 5. Vote and comment

Start at [VoteRestResource.put/remove](../backend/src/main/java/dev/ringlab/adapter/in/rest/vote/VoteRestResource.java),
then [VoteService](../backend/src/main/java/dev/ringlab/application/vote/VoteService.java)
and `VoteRepository`. [VoteDbAdapter.put](../backend/src/main/java/dev/ringlab/adapter/out/db/vote/VoteDbAdapter.java)
uses PostgreSQL upsert for one vote per user/build. Removal is idempotent and
`VoteSummary` derives score from counts; the client never owns the score.

For comments, start at [CommentRestResource](../backend/src/main/java/dev/ringlab/adapter/in/rest/comment/CommentRestResource.java)
or `CommentDeletionRestResource`, then [CommentService](../backend/src/main/java/dev/ringlab/application/comment/CommentService.java).
The service validates text and build existence, trims text on creation, and checks
the comment author's ownership on deletion. `CommentRepository` leads to
[CommentDbAdapter](../backend/src/main/java/dev/ringlab/adapter/out/db/comment/CommentDbAdapter.java),
which orders by creation time then ID before pagination and returns the full count.

**Tests:** `VoteServiceTest`, `CommentServiceTest`,
[RepositoryContractIntegrationTest](../backend/src/test/java/dev/ringlab/RepositoryContractIntegrationTest.java)
and `ApiContract` cover replacement/removal, concurrency, ownership and ordering.

**Tradeoff:** these small services deliberately remain intact. Comments are flat,
scores are derived, and database constraints supply the final concurrency guarantees.
