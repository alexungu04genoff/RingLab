# Best Rated ranking validation pack

Best Rated sorts by full-precision Wilson descending, known game-version release date descending (unknown last), fewer downvotes when Wilson is exactly zero, build creation time descending, then UUID ascending. The release-date comparison precedes Wilson-zero evidence: a newer-patch 0/1 build beats an older-patch 0/0 build. Within one patch, 0/0 beats 0/1 beats 0/5. A net-negative build can have positive Wilson and therefore beat an unrated build. `NEWEST` and `SCORE` have not changed.

Search ordinary build titles for `Wilson Demo`, `Version Demo`, or the readable names below. Internal fixture keys stay in the seed manifest and tests, not in visible titles or descriptions. These controlled demos retain their existing bracketed labels and remain excluded from Top Community Builds. Top 3 ranking is validated separately with eligible fixtures in `CommunitySelectionTest` and `CommunityApiIntegrationTest`; the endpoint has a 60-second snapshot cache.

| Keyword | Readable demo names (without bracketed label) | Votes ↑/↓ | Patch | Fixed UTC creation time | Expected relative order | Result |
| --- | --- | ---: | --- | --- | --- | --- |
| Wilson Demo | Small sample, perfect approval / Large sample, strong approval | 3/0; 40/1 | plan-assigned | existing managed times | Community favourite first (sample size) | Existing plan/domain coverage retained |
| Wilson Demo | Same score, unanimous approval / Same score, divided opinion | 15/0; 25/10 | plan-assigned | existing managed times | Everyone liked first (approval evidence) | Existing plan/domain coverage retained |
| Wilson Demo | Close confidence scores: sixteen clean votes / Close confidence scores: thirty-eight approvals | 16/0; 38/3 | plan-assigned | existing managed times | Quiet favourite first (both Wilson values round to 0.81) | Existing plan/domain coverage retained |
| Wilson Demo | Zero-confidence tie: no votes / Zero-confidence tie: one downvote / Zero-confidence tie: five downvotes | 0/0; 0/1; 0/5 | current | 2026-09-18 12:00Z | Listed order | Existing plan/domain/API coverage retained |
| patch | Newer patch wins a confidence tie / Older patch in a confidence tie | 0/1; 0/0 | newest; oldest | 2026-09-18 12:00Z | Latest patch first | Existing plan/domain/API coverage retained |
| garage pick | Last week's garage pick / This week's garage pick | 8/2; 8/2 | current | 2026-09-17 12:00Z; 2026-09-19 12:00Z | This week first | Existing plan/domain/API coverage retained |
| morning garage experiment | A morning garage experiment / Another morning garage experiment | 3/1; 3/1 | current | both 2026-09-18 12:00Z | Ascending actual UUID from manifest | Existing domain/API coverage retained |

The frontend currently displays net vote score, not Wilson rounded to two decimals. The 0.81 pair is a two-decimal reference comparison; no UI score formatting was changed.

From the repository root, first export the live catalog read-only after local preflight, then preview without writes using that local snapshot:

```powershell
pwsh -NoProfile -File backend/scripts/SeedDemoData.ps1 -ExportCatalogSnapshot backend/demo-catalog.local.json
pwsh -NoProfile -File backend/scripts/SeedDemoData.ps1 -Preview -CatalogSnapshotPath backend/demo-catalog.local.json
pwsh -NoProfile -File backend/scripts/SeedDemoData.Tests.ps1
```

To inspect planned changes against the **local development** API without writing, and then explicitly apply them after reviewing conflicts and ownership:

```powershell
pwsh -NoProfile -File backend/scripts/SeedDemoData.ps1 -Refresh -SetRankingTimestamps
pwsh -NoProfile -File backend/scripts/SeedDemoData.ps1 -Refresh -SetRankingTimestamps -Apply
```

`-SetRankingTimestamps` is opt-in, requires `-Refresh`, and only targets verified manifest-owned ranking fixtures. The SQL update checks the exact build ID, owner, and prior timestamps. The seeder's loopback URL, local Docker Compose, Flyway, ownership, managed-field fingerprint, and foreign-vote protections remain in force. The `-Apply` command is documented for owner approval; it was **not run** for this task. The manifest stores actual UUIDs in `backend/.ringlab-demo-state.json`; search titles and compare their order through the normal Explore Best Rated page. The Top 3 list must not contain any controlled-demo title.

Automated isolated verification (a separate disposable database, never the development database):

```powershell
mvn -f backend/pom.xml '-Dtest=BuildRankingTest,BuildServiceTest,CommunitySelectionTest' test
mvn -f backend/pom.xml '-Dtest=BuildRankingIntegrationTest,CommunityApiIntegrationTest' '-Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/ringlab_ranking_verify_20260922' '-Dquarkus.datasource.username=ringlab' '-Dquarkus.datasource.password=ringlab' '-Dquarkus.datasource.devservices.enabled=false' test
git diff --check
```

The isolated test database was created for this work and is left available for inspection. Broader Maven `verify`/coverage gates and live demo seeding were not run.
