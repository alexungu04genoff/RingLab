# Frontend organization

This refactor moves source and tests by responsibility, separates the app shell
from mounting, and extracts the existing mixed component file. No backend, API,
schema, dependencies, calculation rules, or visual design changed.

## Old → new file map

Paths below are relative to `frontend/src`. Matching implementation and test
files move together. The route components and their route interaction tests
remain in `pages`; their imports were updated.

| Old | New |
| --- | --- |
| `BaseStats.test.tsx` | `features/stats/BaseStats.test.tsx` |
| `BaseStats.tsx` | `features/stats/BaseStats.tsx` |
| `BuildComparison.test.tsx` | `features/builds/BuildComparison.test.tsx` |
| `BuildComparison.tsx` | `features/builds/BuildComparison.tsx` |
| `BuildRecommendationDialog.test.tsx` | `features/recommendations/BuildRecommendationDialog.test.tsx` |
| `BuildRecommendationDialog.tsx` | `features/recommendations/BuildRecommendationDialog.tsx` |
| `CardClarity.test.tsx` | `features/builds/CardClarity.test.tsx` |
| `CardStats.tsx` | `features/stats/CardStats.tsx` |
| `Collection.test.tsx` | `features/collection/Collection.test.tsx` |
| `Collection.tsx` | `features/collection/Collection.tsx` |
| `GoogleSignIn.tsx` | `features/auth/GoogleSignIn.tsx` |
| `InfoPopover.test.tsx` | `shared/ui/InfoPopover.test.tsx` |
| `InfoPopover.tsx` | `shared/ui/InfoPopover.tsx` |
| `LatestNews.tsx` | `features/news/LatestNews.tsx` |
| `MapRecommendations.test.tsx` | `features/maps/MapRecommendations.test.tsx` |
| `MapRecommendations.tsx` | `features/maps/MapRecommendations.tsx` |
| `PageCardStats.test.tsx` | `features/stats/PageCardStats.test.tsx` |
| `PassiveStats.test.tsx` | `features/stats/PassiveStats.test.tsx` |
| `PassiveStats.tsx` | `features/stats/PassiveStats.tsx` |
| `RacingTypeBadge.tsx` | `shared/ui/RacingTypeBadge.tsx` |
| `SavedBuilds.test.tsx` | `features/saved-builds/SavedBuilds.test.tsx` |
| `SavedBuilds.tsx` | `features/saved-builds/SavedBuilds.tsx` |
| Gadget effect/acquisition badges | `features/gadgets/GadgetMetadata.tsx` |
| `ScenarioPreview.test.tsx` | `features/stats/ScenarioPreview.test.tsx` |
| `ScenarioPreview.tsx` | `features/stats/ScenarioPreview.tsx` |
| `SearchableFilter.test.tsx` | `shared/ui/SearchableFilter.test.tsx` |
| `SearchableFilter.tsx` | `shared/ui/SearchableFilter.tsx` |
| `SnapshotTime.tsx` | `shared/ui/SnapshotTime.tsx` |
| `StatBar.tsx` | `shared/ui/StatBar.tsx` |
| `StockMachineCard.test.tsx` | `features/collection/StockMachineCard.test.tsx` |
| `StockMachineCard.tsx` | `features/collection/StockMachineCard.tsx` |
| `TopCommunityBuilds.test.tsx` | `features/builds/TopCommunityBuilds.test.tsx` |
| `TopCommunityBuilds.tsx` | `features/builds/TopCommunityBuilds.tsx` |
| `api.test.ts` | `shared/api/api.test.ts` |
| `api.ts` | `shared/api/api.ts` |
| `auth.test.tsx` | `features/auth/auth.test.tsx` |
| `auth.tsx` | `features/auth/auth.tsx` |
| `buildForm.test.ts` | `features/builds/buildForm.test.ts` |
| `buildForm.ts` | `features/builds/buildForm.ts` |
| `buildSharing.test.ts` | `features/builds/buildSharing.test.ts` |
| `buildSharing.ts` | `features/builds/buildSharing.ts` |
| `commentPagination.test.ts` | `features/comments/commentPagination.test.ts` |
| `commentPagination.ts` | `features/comments/commentPagination.ts` |
| `devApiProxy.test.ts` | `../devApiProxy.test.ts` |
| `gadgetArtwork.ts` | `features/collection/gadgetArtwork.ts` |
| `icons.tsx` | `shared/ui/icons.tsx` |
| `machineComposition.ts` | `features/builds/machineComposition.ts` |
| `mapSelection.ts` | `features/maps/mapSelection.ts` |
| `pages/exploreFilters.ts` | `features/builds/exploreFilters.ts` |
| `pages/useAuthForm.ts` | `features/auth/useAuthForm.ts` |
| `pages/useBuildDraft.ts` | `features/builds/useBuildDraft.ts` |
| `recommendation.test.ts` | `features/recommendations/recommendation.test.ts` |
| `recommendation.ts` | `features/recommendations/recommendation.ts` |
| `stats.ts` | `features/stats/stats.ts` |
| `types.ts` | `shared/types.ts` |
| `useBuildRecommendation.ts` | `features/recommendations/useBuildRecommendation.ts` |
| `useLoad.test.tsx` | `shared/hooks/useLoad.test.tsx` |
| `useLoad.ts` | `shared/hooks/useLoad.ts` |
| `main.tsx` | `main.tsx` (mounting/providers) + `app/App.tsx` (unchanged shell/routes) |
| `balancedRecommendation.css` | `styles/recommendation.css` |
| `styles.css` | Ordered files imported by `styles/index.css` (see below) |

### Former components.tsx responsibilities

| Existing responsibility | New file |
| --- | --- |
| SiteFooter | `app/SiteFooter.tsx` |
| Artwork rendering | `shared/ui/Artwork.tsx` |
| Artwork ownership/catalog fallback | `features/collection/CollectionArtwork.tsx` |
| ErrorNotice | `shared/ui/ErrorNotice.tsx` |
| date, countLabel | `shared/lib/format.ts` |
| browseOrigin, buildDetailsOrigin | `features/builds/buildNavigation.ts` |
| BuildCard, BuildPartIcon, BuildGadgetIcon, patchAge | `features/builds/BuildCard.tsx` |
| MachineSetup, isStockSetup | `features/builds/MachineSetup.tsx` |
| ItemSelect | `features/collection/ItemSelect.tsx` |
| Racing type helper re-exports | Consumers import `shared/ui/RacingTypeBadge.tsx` directly |

`components.tsx` is removed. Its test cases are retained beside Artwork,
CollectionArtwork, ItemSelect, and SiteFooter. CollectionArtwork preserves the
existing category inference and gadget image fallbacks, then passes presentation
props to Artwork. Shared Artwork has no knowledge of ownership storage or API
endpoints. The generic proxy test now sits beside `frontend/devApiProxy.ts`.
The standalone `frontend/tests/voting.tsx` harness imports the moved auth/API modules.

## Final source tree

`frontend/src`:

```text
├── app
│   ├── App.tsx
│   ├── SiteFooter.test.tsx
│   └── SiteFooter.tsx
├── features
│   ├── auth
│   │   ├── auth.test.tsx
│   │   ├── auth.tsx
│   │   ├── GoogleSignIn.tsx
│   │   └── useAuthForm.ts
│   ├── builds
│   │   ├── BuildCard.tsx
│   │   ├── BuildComparison.test.tsx
│   │   ├── BuildComparison.tsx
│   │   ├── buildForm.test.ts
│   │   ├── buildForm.ts
│   │   ├── buildNavigation.ts
│   │   ├── buildSharing.test.ts
│   │   ├── buildSharing.ts
│   │   ├── CardClarity.test.tsx
│   │   ├── exploreFilters.ts
│   │   ├── machineComposition.ts
│   │   ├── MachineSetup.tsx
│   │   ├── TopCommunityBuilds.test.tsx
│   │   ├── TopCommunityBuilds.tsx
│   │   └── useBuildDraft.ts
│   ├── collection
│   │   ├── Collection.test.tsx
│   │   ├── Collection.tsx
│   │   ├── CollectionArtwork.test.tsx
│   │   ├── CollectionArtwork.tsx
│   │   ├── gadgetArtwork.ts
│   │   ├── ItemSelect.test.tsx
│   │   ├── ItemSelect.tsx
│   │   ├── StockMachineCard.test.tsx
│   │   └── StockMachineCard.tsx
│   ├── comments
│   │   ├── commentPagination.test.ts
│   │   └── commentPagination.ts
│   ├── maps
│   │   ├── MapRecommendations.test.tsx
│   │   ├── MapRecommendations.tsx
│   │   └── mapSelection.ts
│   ├── news
│   │   └── LatestNews.tsx
│   ├── recommendations
│   │   ├── BuildRecommendationDialog.test.tsx
│   │   ├── BuildRecommendationDialog.tsx
│   │   ├── recommendation.test.ts
│   │   ├── recommendation.ts
│   │   └── useBuildRecommendation.ts
│   ├── saved-builds
│   │   ├── SavedBuilds.test.tsx
│   │   └── SavedBuilds.tsx
│   └── stats
│       ├── BaseStats.test.tsx
│       ├── BaseStats.tsx
│       ├── CardStats.tsx
│       ├── PageCardStats.test.tsx
│       ├── PassiveStats.test.tsx
│       ├── PassiveStats.tsx
│       ├── ScenarioPreview.test.tsx
│       ├── ScenarioPreview.tsx
│       └── stats.ts
├── pages
│   ├── AccountPage.test.tsx
│   ├── AccountPage.tsx
│   ├── AuthPage.test.tsx
│   ├── AuthPage.tsx
│   ├── BuildDetails.test.tsx
│   ├── BuildDetails.tsx
│   ├── BuildEditor.test.tsx
│   ├── BuildEditor.tsx
│   ├── CompareBuilds.test.tsx
│   ├── CompareBuilds.tsx
│   ├── Explore.test.tsx
│   ├── Explore.tsx
│   ├── ExploreNavigation.test.tsx
│   ├── ForgotPasswordPage.tsx
│   ├── GameData.test.tsx
│   ├── GameData.tsx
│   ├── Guide.test.tsx
│   ├── Guide.tsx
│   ├── MachineSetup.test.tsx
│   ├── PasswordRecovery.test.tsx
│   ├── Remix.test.tsx
│   ├── ResendVerificationPage.test.tsx
│   ├── ResendVerificationPage.tsx
│   ├── ResetPasswordPage.tsx
│   ├── SavedBuildsPage.test.tsx
│   ├── SavedBuildsPage.tsx
│   ├── VerifyEmailPage.test.tsx
│   └── VerifyEmailPage.tsx
├── shared
│   ├── api
│   │   ├── api.test.ts
│   │   └── api.ts
│   ├── hooks
│   │   ├── useLoad.test.tsx
│   │   └── useLoad.ts
│   ├── lib
│   │   └── format.ts
│   ├── ui
│   │   ├── Artwork.test.tsx
│   │   ├── Artwork.tsx
│   │   ├── ErrorNotice.tsx
│   │   ├── icons.tsx
│   │   ├── InfoPopover.test.tsx
│   │   ├── InfoPopover.tsx
│   │   ├── RacingTypeBadge.tsx
│   │   ├── SearchableFilter.test.tsx
│   │   ├── SearchableFilter.tsx
│   │   ├── SnapshotTime.tsx
│   │   └── StatBar.tsx
│   └── types.ts
├── styles
│   ├── auth.css
│   ├── base.css
│   ├── build-details.css
│   ├── builds.css
│   ├── collection.css
│   ├── editor.css
│   ├── index.css
│   ├── layout-support.css
│   ├── layout.css
│   ├── machine-selection.css
│   ├── maps.css
│   ├── news-and-build-comparison.css
│   ├── readability.css
│   ├── recommendation.css
│   ├── responsive.css
│   ├── scenario.css
│   ├── stats-and-collection.css
│   ├── stats.css
│   └── tokens.css
└── main.tsx
```

## CSS split and cascade

The following files reconstruct the original stylesheet in its exact source order.
Concatenating them reproduces the original source, including every selector,
declaration, media/container query, and late override (with normalized line endings).

| Order | File under styles/ | Lines |
| --- | --- | --- |
| 1 | `tokens.css` | 22 |
| 2 | `base.css` | 118 |
| 3 | `layout.css` | 446 |
| 4 | `builds.css` | 624 |
| 5 | `layout-support.css` | 82 |
| 6 | `editor.css` | 445 |
| 7 | `build-details.css` | 410 |
| 8 | `auth.css` | 55 |
| 9 | `collection.css` | 237 |
| 10 | `responsive.css` | 429 |
| 11 | `news-and-build-comparison.css` | 277 |
| 12 | `stats.css` | 54 |
| 13 | `readability.css` | 108 |
| 14 | `maps.css` | 82 |
| 15 | `machine-selection.css` | 56 |
| 16 | `stats-and-collection.css` | 179 |
| 17 | `scenario.css` | 38 |

`recommendation.css` is the unchanged former balanced recommendation stylesheet.
It retains its existing import through BuildRecommendationDialog, before the global
manifest. Its specificity and position are preserved.

The original CSS interleaves feature styles and later corrections. Adjacent sections
that cover multiple views stay together (notably news/comparison and stats/collection)
to avoid silently changing the cascade. Shared StatBar, StatLegend, InfoPopover, and
ownership presentation classes remain single shared selector definitions; this
refactor introduces no feature copies or selector changes. Grouping those late
sections more aggressively would be a separate cascade refactor.

## Dependency direction

- `shared` imports only shared modules and third-party libraries. API contracts
  remain explicit in `shared/types.ts`.
- Features import shared code and the feature responsibilities they actually reuse.
  For example, stats validates setups through build helpers; build cards compose
  stats, collection artwork, maps, comparison, and saved-build controls.
- Pages compose feature UI and shared infrastructure; reusable draft/auth/filter
  state lives in features, not pages.
- App owns shell/routes, while main mounts the existing providers in their original order.

There are no production runtime dependency cycles or unresolved relative imports.
No alias or barrel exports were needed. Shared gadget metadata lives in gadgets; StatBar,
StatLegend, and InfoPopover retain their existing implementations under shared UI.

## Verification

- `npm test`: 40 files / 359 tests passed before the final split of the Artwork
  test file. The coverage run below checks the final 41-file arrangement.
- `npm run test:coverage -- --maxWorkers=2`: 41 files / 359 tests passed; existing
  thresholds passed. Statements/lines 94.03%, branches 91.78%, functions 86.10%.
  The initial default-worker coverage run timed out in four interaction cases;
  the two-worker run passed without changes to assertions or timeouts.
- `npm run build`: TypeScript production check and Vite production bundling passed.
- All 49 matched production module bodies were unchanged against HEAD after
  excluding imports/comments; the stylesheet reconstruction also matched HEAD.
  Imports, file locations, the app-shell extraction, and the Artwork boundary are
  the intended source changes.
- Browser checks use the existing local stack at `http://localhost:5173`.
  Public Explore, build details/Scenario Preview, collection (racers, gadgets,
  maps), the populated two-build comparison, and the Calculation notes popover
  fit the measured 360/390/412 CSS-pixel viewports without horizontal page overflow.
  Browser console checks found no warnings or errors. The documented Quick Starter
  + Sea Dog fixture returned +40 to every stat for Lap 1 + Water: 105 / 70 / 99 / 92 / 74.

Coverage HTML is at `frontend/coverage/index.html` (generated, untracked output).
No backend tests, database resets, service restarts, commits, pushes, or deployments
were performed.

For manual verification, run from `frontend`:

```powershell
npm test
npm run test:coverage -- --maxWorkers=2
npm run build
```

## Later component splits

These were kept intact apart from imports:

- BuildEditor (518 lines): selection sections, gadget ordering/plate UI, and
  recommendation/lock coordination are clear candidates for a later split.
- BuildDetails (464 lines): voting/comments, setup presentation, and sharing
  could be separated from route/session orchestration.
- BuildRecommendationDialog (387 lines): configuration/priority controls and
  result/delta presentation could be separated while retaining request/apply guards.
- PassiveStats (214 lines): catalog rule badges/details and calculated stat panels
  have distinct responsibilities; keep calculation explanations consistent.
- ScenarioPreview (210 lines): context inputs and result presentation can be split
  while retaining shared-scale geometry and stale-request handling.

The app shell remains outside unit-test coverage and is checked in the browser.
Authenticated editor, ownership mutation, and recommendation Apply are covered
by the existing interaction tests; a signed-in browser walkthrough remains useful
for final manual review.

One separate data note: the existing Quick Starter + Sea Dog fixture's saved
description still says the simultaneous combination is partial, while the live
calculation correctly returns the supported +40 combination. The saved description
was left unchanged because this task excludes database/fixture mutations.
