# Top community builds integration

RingLab exposes a public read-only snapshot and a Discord message formatter. Neither endpoint
publishes, schedules, or edits Discord messages. Link previews/Open Graph are a separate feature;
returning JSON does not implement them.

## Endpoints

- `GET /api/community/top-builds`: schema version 1, `ranking: "best-rated"`, `scope: "overall"`,
  `patches: "all"`, `snapshotAt` (UTC ISO timestamp), and 0–3 `items` in rank order.
- `GET /api/community/top-builds/discord`: ready-to-send Discord message JSON from the same snapshot.

No query parameters are supported: either endpoint returns 400 if **any** parameter is present.
No login, browser cookie, author selection, or saved preference is required. Both endpoints use
the existing build-list IP rate-limit bucket (`RATE_LIMIT_BUILD_LIST`, default 60/minute), including
conditional requests. Existing CORS and authentication policies are unchanged.

Each JSON item contains `rank`, `build` (the existing public BuildResponse contract), `machineType`,
`stats` (the existing total/character/machine breakdown), `buildUrl`, and `artworkUrl`.
The build contains ID, title, description, public author ID/username, racer, selected front/rear/tire
parts, patch, ordered gadgets, creation/update timestamps, remix provenance and vote totals.
It never contains email, credentials or a personalized vote. Boost builds have `tirePart: null`.
Missing numerical stats remain null, including each unknown breakdown contribution.

Example envelope (items omitted here for brevity; actual integration-test responses are saved in
`backend/target/community-examples/top-builds.json` and `discord.json`):

```json
{"schemaVersion":1,"ranking":"best-rated","scope":"overall","patches":"all","snapshotAt":"2026-09-20T12:00:00Z","items":[]}
```

An empty Discord result is still a valid message:

```json
{"content":"No eligible community builds are available yet.","embeds":[],"allowed_mentions":{"parse":[]}}
```

## Selection and eligibility

Selection calls the production `BuildRanking` Best Rated comparator: positive, neutral, negative
score groups; descending Wilson lower bound within each group; descending net score; descending
creation timestamp; ascending textual UUID. No separate ranking formula exists in the frontend,
REST resource, database or Discord formatter.

All public builds, racers, authors and patches in the current environment are candidates. Before
taking three, skip titles beginning exactly (case sensitive, no trimming) with:
`[Wilson Demo]`, `[Comment Demo]`, `[Pagination Demo]`, `[Compare Demo]`, `[Remix Demo]`,
`[Machine Demo]`, `[Gadget Demo]`, `[Ownership Demo]`, `[Vote Demo]`, `[Version Demo]`, `[DEMO]`.
These reserved fixture labels are centralized in `CommunityEligibility`; there is no existing
persisted fixture designation. Ordinary titles containing “demo”, `ringlab_demo_*` authors,
and unlabelled showcase builds are eligible. No one-per-author or one-per-racer rule exists.

A selected build must reference an existing racer, valid FRONT and REAR parts, compatible source
machine types, and the required TIRE (or no tire for Boost). Compatibility uses the existing
`MachineCompatibility` policy. Gadgets must exist, have known current costs 1–3, be unique and
fit `GadgetPlate`'s two rows of three. An existing referenced patch is required when specified;
legacy unspecified patches are allowed and have unknown stats. Missing numerical stats do not
disqualify a build. Checks never repair or write a legacy build. Catalog/DB failures propagate
to the existing safe server-error mapper, rather than becoming an empty leaderboard.

Normal `/api/builds` searches do not use the controlled-demo eligibility exclusions. Public Explore
requests `excludeTop=true`, which removes the current snapshot's winner IDs before ranking,
counting and pagination; the promoted Top 3 therefore do not appear again in the lower grid.
Controlled demos remain searchable there. My Builds and API requests without that explicit flag
remain complete. The dedicated showcase appears only on public Explore, above filters. Its fetch key
never contains search parameters. Copy top 3 uses the snapshot currently displayed and performs no
network request.

## Cache and URLs

One synchronized in-memory cache entry per backend process stores a fully resolved immutable
snapshot. `COMMUNITY_SNAPSHOT_LIFETIME` defaults to `PT60S`, configurable between 1 and 300 seconds.
The lifetime starts when computation starts. Successful simultaneous misses share one computation;
failures are not cached. A database transaction completes before publishing a new entry. There is
no write-triggered invalidation. Votes, edits, deletions and catalog eligibility changes become
visible on the first request after expiry. Manual Refresh revalidates; it does not bypass the TTL.
An already-open page retains its displayed snapshot until manual refresh or remount, with its
timestamp visible. A refresh failure retains the last displayed data with a warning.

Both endpoints use the same selected snapshot and stable timestamp, with representation-specific
ETags. Send `If-None-Match` to receive an empty 304 when unchanged. ETags change on recomputation,
even if the winners are identical. `Cache-Control: public, no-cache, must-revalidate` permits storage
but requires validation on reuse, so browser/CDN freshness does not add another 60 seconds.
Requests straddling expiry may naturally observe different snapshots.

Configure `PUBLIC_BASE_URL` as the frontend HTTP(S) origin. Development defaults to
`http://localhost:5173`; production requires an explicit value. `PUBLIC_ASSET_ORIGIN` optionally
selects a different trusted asset origin, defaulting to the site origin. Origins cannot contain
credentials, paths, queries or fragments. Host/forwarded-host headers are never used.
Artwork is accepted only from `/assets/racers/<lowercase-slug>.(png|webp|jpg)`; other paths yield null.
The formatter fetches no images. Discord must be able to reach artwork over the public network:
an absolute localhost URL is still local and will not work for external Discord rendering.

The cache bounds retained data to three builds. Computation reuses the existing all-candidate
ranking projection and vote summaries (O(N) transient memory; O(N log N) sort), hydrates at most
50 builds per batch and stops after finding three eligible builds. Catalog data is loaded once per
computation and numerical stats once per selected patch. Worst case scans all candidates when few
are eligible; this deliberately preserves the canonical global ranking without introducing SQL
Wilson formulas or a misleading candidate cap. There are no retries, background polling or jobs.

## Read-only consumers

```sh
curl -i http://localhost:5173/api/community/top-builds
curl -i -H 'If-None-Match: "<ETag from previous response>"' http://localhost:5173/api/community/top-builds
curl http://localhost:5173/api/community/top-builds/discord
```

```powershell
$snapshot = Invoke-RestMethod 'http://localhost:5173/api/community/top-builds'
$snapshot.items | ForEach-Object { "#$($_.rank) $($_.build.title) $($_.buildUrl)" }
```

Example external JavaScript consumer (no session):

```js
const response = await fetch(`${process.env.RINGLAB_ORIGIN}/api/community/top-builds`);
if (!response.ok) throw new Error(`RingLab returned ${response.status}`);
const snapshot = await response.json();
for (const item of snapshot.items) console.log(item.rank, item.build.title, item.buildUrl);
```

## Optional Discord posting by the consumer

This is a documented example only; RingLab never executes it. Keep the webhook URL in a server-side
environment variable, never in frontend configuration or source control:

```powershell
# RINGLAB_ORIGIN must be publicly accessible; DISCORD_WEBHOOK_URL is a secret set outside source.
$payload = Invoke-RestMethod "$env:RINGLAB_ORIGIN/api/community/top-builds/discord"
Invoke-RestMethod -Method Post -Uri $env:DISCORD_WEBHOOK_URL -ContentType 'application/json' `
  -Body ($payload | ConvertTo-Json -Depth 20)
```

The formatter emits at most three embeds with distinct build links, optional thumbnails, compact
votes, author, racer, machine type, patch, components and gadgets. It excludes build descriptions.
Mentions are disabled with `allowed_mentions: { parse: [] }`. User text has Markdown/control syntax
removed and mention/autolink punctuation neutralized. Truncation preserves surrogate pairs.
Each title is at most 256 UTF-16 units and each description at most 1700, bounding the combined text
to 5868 (below 6000), with no extra author/footer/field text. These conservative budgets follow
the [official Discord message/embed limits](https://github.com/discord/discord-api-docs/blob/main/developers/resources/message.mdx).

## Verification

Pure tests: `CommunitySelectionTest`, `CommunitySnapshotCacheTest`, `CommunityExportTest`.
HTTP/database test: `CommunityApiIntegrationTest`, against an isolated database only; writes actual
response samples under `backend/target/community-examples/`. Existing ranking, repository and
stats integration tests continue covering shared behavior. `TopCommunityBuilds.test.tsx` covers
independence, demos/pagination, copy success/failure, refresh/retry, empty state and My Builds.

Run frontend `npm run test:coverage` and `npm run build`. Run backend verification only with the
README's disposable-database configuration; never aim acceptance tests at development data.

### Local verification recorded 2026-09-20

From `E:\RingLab`, the focused pure-test command passed:

```powershell
mvn -f backend/pom.xml "-Dtest=CommunitySelectionTest,CommunitySnapshotCacheTest,CommunityExportTest,BuildRankingTest,WilsonScoreTest,BaseStatsServiceTest,ArchitectureTest" test
```

Final backend verification used the separately created local database
`ringlab_community_verify_20260920`, not the development database:

```powershell
$env:PUBLIC_BASE_URL = 'http://localhost:5173'
mvn -f backend/pom.xml "-Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/ringlab_community_verify_20260920" "-Dquarkus.datasource.username=ringlab" "-Dquarkus.datasource.password=ringlab" "-Dquarkus.datasource.devservices.enabled=false" verify
```

Result: 212 Surefire tests and 15 packaged API tests passed; architecture tests and existing
JaCoCo gates passed. The report is `backend/target/site/jacoco/index.html`. Maven emitted an
existing unrecognized logging-category configuration warning; verification still succeeded.

From `E:\RingLab\frontend`:

```powershell
npm run test:coverage
npm run build
```

Result: 135 tests in 20 files passed; coverage was 86.92% statements/lines, 88.12% branches,
77.71% functions. TypeScript and Vite production build passed, including a final rebuild after
the scoped mobile tooltip layout adjustment. No coverage thresholds were changed.

Actual saved API responses from the final isolated run have snapshot timestamp
`2026-09-20T19:30:01.298777800Z` and these winners (other acceptance tests also create eligible builds):

1. `Unique Needle`: `/builds/a7cb7178-0218-4f8b-ba41-8dc8aed08429`
2. `Literal %_ build`: `/builds/5e8b6125-966d-438e-9d84-38f82afe2206`
3. `Community API example 1`: `/builds/999facaf-cd68-4304-960e-29f85699b1cc`

Their absolute URLs use `http://localhost:5173`; Discord embeds contain those same ordered links.
See the complete generated JSON files mentioned above for the full contracts. They are build
artifacts, not committed fixtures, and later test runs replace them.

Browser verification used the existing local stack: the frontend and proxied API returned 200;
searching `wilson` kept the same showcase/timestamp while showing all 10 matching demos below.
Desktop cards displayed in three columns; at a 390px viewport they stacked with no showcase
elements extending past the viewport and readable stat labels. The computer-use verification
identified and bounded gadget tooltips within the new showcase only. Existing lower-list mobile
tooltip overflow is outside this feature's scope. Automated tests additionally cover saved
preferences, other filters, pagination, independent failures, and copying the displayed snapshot.

No development data reset, fixture regeneration, migration edits, production access, deployment,
Discord posting, commit or push was performed. The isolated verification database remains available
for inspection. Review the diff and the generated responses before committing; no broader test run
remains outstanding for this implementation.
