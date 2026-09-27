# Author-selected Recommended Maps

The catalog and source/artwork ledger are in [map-catalog-sources.md](map-catalog-sources.md).
The verified import has 44 records: 24 base main courses, 15 CrossWorld destinations,
and 5 released collaboration main courses. Course category and content-pack membership
are separate. No map availability is inferred from a build's patch.

## Storage and compatibility

V30 adds `race_maps` and `build_recommended_maps`; V31 imports verified identities and
local artwork paths. Zero associations means All maps (no author preference supplied).
Nonzero associations are a set. Selecting every current catalog record remains Selected.
Map deletion is restricted while referenced; build deletion cascades its associations.
Neither migration modifies existing builds, votes, comments, bookmarks, gadgets or remix links.

`GET /api/maps` returns the catalog in stable display order. Build responses everywhere
include `mapRecommendations: { mode: "ALL" | "SELECTED", maps: [...] }`.
Create/edit requests accept `recommendedMapIds`:

| Input | Create | Update |
| --- | --- | --- |
| Omitted | All maps | Preserve saved selection |
| `[]` | All maps | Clear to All maps |
| Nonempty UUID array | Selected set | Replace selected set |
| Null, malformed/null/duplicate/unknown IDs | Reject | Reject |

Selections are bounded by the supported catalog, not a fixed small count. Missing artwork
does not invalidate an identity. The UI has labelled image fallbacks. No persisted mode
column, automatic recommendations, stats effects or ranking adjustments are introduced.

## Browsing and consumers

Explore/My Builds and Saved Builds accept `mapId=<uuid>&includeAllMaps=true|false`.
With a map, the default `true` includes that explicit selection plus All maps builds;
`false` includes only explicit recommendations. No map means no map predicate.
Unknown IDs and unsupported boolean strings return 400; malformed UUID query values
follow the existing JAX-RS parameter-conversion convention. Filtering precedes ranking,
counts and pagination and uses collection membership/emptiness, avoiding duplicate rows.
All existing filters and bookmark ownership still apply.

URL state supports pagination reset, filter chips, clear, back and forward. The Maps tab's
Find recommended builds link opens explicit-only filtering. Shared cards show a compact
map control immediately before the machine badge; activation opens a native modal dialog
with Escape, focus trapping and return focus. All maps shows an explanation, not the catalog.
The editor, remix, details, comparison and copy/share formatters use the same presentation
helpers. Collection search is available on every tab. Comparisons show map preferences at
the bottom of each column, highlight differences, and use set membership, not array order. The selection tray stores identity
and display labels only; comparison and bookmarks load current builds.

Top 3 membership stays global. Its shared response includes recommendations. The existing
60-second snapshot lifetime (configurable 1–300 seconds) also governs map edits: refreshed
snapshots receive a new revision/ETag even without vote changes. Discord output remains
mention-safe and within its existing combined size budget; nothing posts to Discord.

## Apply and demonstrate locally

Do not run ordinary startup against an existing database until you deliberately choose to
apply V30/V31. The populated preview now uses database `ringlab_maps_dev_preview_20260927`
on port 5432: a snapshot copy of the existing local dev database, plus map demos and the
later explicitly requested expanded fictional community. The completed preview contains
246 builds, 167 accounts, 1,870 votes and 586 comments. 179 builds recommend specific maps;
the remainder use All maps. The managed plan targets 240 builds across 40 demo authors,
with bracketed feature examples and ordinary build titles. Records with outside activity
or changed managed fields are retained rather than overwritten. Existing votes are preserved
outside the controlled ranking fixtures belonging exclusively to reserved demo accounts.
The original source was already at V31 when inspected and had 63 builds with zero map
associations, 855 votes and 439 comments; this task did not migrate or reset that source.
Tests use `ringlab_maps_test` in a separate PostgreSQL cluster on port 55435.
The preview backend uses port 8080 and the Vite proxy port 5173;
mock mail is enabled and Steam news is disabled for this isolated preview.

To restart the populated isolated preview, point Quarkus at it explicitly:

```powershell
cd E:\RingLab\backend
mvn '-Dquarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:5432/ringlab_maps_dev_preview_20260927' `
    '-Dquarkus.datasource.username=ringlab' '-Dquarkus.datasource.password=ringlab' `
    '-Dquarkus.datasource.devservices.enabled=false' '-Dquarkus.mailer.mock=true' quarkus:dev
```

Flyway applies the new migrations during startup. To apply later to your normal local
database, review the migration diff and take your normal backup first, then intentionally
use its configuration. No old migration needs editing or repairing.

The optional `scripts/Add-MapDemo.ps1` takes a signed-in user's token and a loopback API URL.
It adds only the three named `[Map Demo]` builds, leaves existing matches unchanged, and
does not create votes/comments/bookmarks or regenerate a community. Those demo titles
are excluded from global Top 3. Supply the token from a local session, without saving it
in source or command-history literals:

```powershell
cd E:\RingLab
.\scripts\Add-MapDemo.ps1 -Token $localSession.token
```

The isolated preview already contains the three examples. Its demo-only login is
`ringlab_demo_maps` / `MapPreview-2026!`; it has no relationship to real accounts.
Try the map controls, edit/remix, compare two examples, and Maps → Find recommended builds.

The expanded generator accepts `-ExpandedCommunity -BuildCount 240` and an explicit
`-DatabaseName ringlab_maps_dev_preview_20260927` with a separate manifest. It spaces API
requests at least two seconds apart (at most 30/minute), leaving capacity for normal browsing.
It does not change server rate limits. Reusing its manifest resumes completed work safely.

The editor allows choosing a stock machine or first part without choosing a machine type
first. The selected source sets the type and filters the remaining parts. Map selection uses
an artwork grid, aligned controls, and a live summary under Your combination.

## Verification

Completed: 274 backend tests and 18 packaged checks; packaging and coverage checks passed.
Frontend: 245 tests across 31 files, coverage 91.72% statements / 91.52% branches, production
build passed. The expanded planner's pure PowerShell tests passed. Desktop and 360/390/412
CSS-pixel map layouts were checked, including selection summary, comparison order, dialog
outside dismissal and focus return. Direct stock selection was verified in the browser.
The populated frontend and proxied build API responded successfully after generation.

Focused IntelliJ classes: `BuildServiceTest`, `MapRecommendationsContractTest`,
`RecommendedMapsIntegrationTest`, `RecommendedMapsMigrationIntegrationTest`,
`CommunitySnapshotCacheTest`, and `CommunityExportTest`. The first two are pure unit tests;
the two integration classes must use an isolated PostgreSQL database.

Full backend verification (set DB_URL/DB_USER/DB_PASSWORD to the same isolated database for
the packaged test; its existing resources supply temporary JWT keys and loopback SMTP):

```powershell
cd E:\RingLab\backend
$env:DB_URL='jdbc:postgresql://127.0.0.1:55435/ringlab_maps_test'
$env:DB_USER='ringlab_maps'
$env:DB_PASSWORD='maps-local'
$env:PUBLIC_BASE_URL='http://localhost:5173'
mvn '-Dquarkus.datasource.jdbc.url=jdbc:postgresql://127.0.0.1:55435/ringlab_maps_test' `
    '-Dquarkus.datasource.username=ringlab_maps' '-Dquarkus.datasource.password=maps-local' `
    '-Dquarkus.datasource.devservices.enabled=false' verify
cd ..\frontend
npm run test:coverage -- --maxWorkers=2
npm run build
```

Backend reports: `backend/target/surefire-reports`, `backend/target/failsafe-reports`,
`backend/target/site/jacoco/index.html`. Frontend coverage: `frontend/coverage/index.html`.
