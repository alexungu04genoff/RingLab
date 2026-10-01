# Current-feature DEV demonstration data

The existing `backend/scripts/SeedDemoData.ps1` generator has an additive
`-CurrentFeatures` mode. It preserves the established expanded community plan and
the frozen Miku optimizer template, and adds ten teaching loadouts through
`FeatureDemoPlan.ps1`. There is no second writer or fixture database.

## Regenerate / complete the DEV dataset

Run from the repository root with PowerShell 7 and the existing DEV PostgreSQL
and Quarkus services already healthy:

```powershell
pwsh -NoProfile -File backend/scripts/SeedDemoData.ps1 -BaseUrl http://127.0.0.1:8080 -DatabaseName ringlab -ExpandedCommunity -BuildCount 240 -RandomSeed 20260927 -ReferenceTime '2026-09-27T01:00:00Z' -CurrentFeatures -Apply
```

Omit `-Apply` to inspect planned additions and conflicts first. Do not combine
this mode with `-Refresh`, promotion, timestamp changes, or fixture-only modes.
The original community parameters are retained so existing fixture keys and
engagement are not reshuffled. The generator never starts services.

To normalize an existing manifest-owned dataset, add
`-NormalizeControlledFixtures` to the command above. Omit `-Apply` for a
read-only cleanup preview. This mode never creates missing rows and updates
only teaching titles plus the two explicitly repurposed scenario loadouts.
The complete key/ID/old-title/new-title ledger is in
[demo-fixture-normalization.md](demo-fixture-normalization.md).

The plan contains **161 reserved accounts, 251 builds, 1,694 votes, 556 comments,
and 355 map associations**. These are desired plan totals, not a claim that the
live database exactly matches them. Existing manual content and engagement are
preserved, so actual totals may differ. There are 184 ordinary titles and 67
labelled teaching examples, including 36 explicit controlled fixtures (19 ranking,
6 comment-pagination, 8 scenario, 2 recommendation, 1 optimizer). The existing
three deliberately promoted ranking examples now use the Ranking prefix and
are excluded from normal Top 3 alongside the other teaching fixtures.
There are 52 racers, 203 mixed setups, 49 Boost builds without tires, 229 builds
on 1.4.1 and 22 on older patches.

## Teaching loadouts

New loadouts use the isolated `ringlab_demo_optimizer` account and the
`[Demo · <Feature>]` convention recognized by the community Top 3 policy. They receive
no votes/comments and contain no stored scenario, locks, solver result or
ownership settings. The combined Starter/Sea Dog fixture demonstrates separate
conditions and the honest unsupported overlap; other scenario loadouts isolate
their target gadget. All use reviewed 1.4.1 data.

| Descriptive name (Scenario unless noted) | Manual demonstration |
| --- | --- |
| Quick Starter + Sea Dog | Lap 1/Normal: Starter +20; Lap 2/Water: Sea Dog +20; Lap 1/Water: partial, overlap unverified; reset |
| Super Slow Starter: final lap | Lap 2 inactive; Lap 3 +60 to each stat |
| Invincible Finish — Near the finish | Within 300 metres: active invincibility without stat points; reuses former water fixture ID |
| Ace Pilot: flight form | Normal inactive; Flight +20; ring utility separate |
| All-Rounder: either transformation | Normal inactive; Water or Flight +20 |
| Ring Engine: rings held now | 0 inactive; 1 or more held rings fixed +12 |
| Perfect Landing: active utility | Active landing boost, no extra stat points |
| Damage Evolution: honest unknown | Unsupported accumulation; partial result |
| Combined locks: keep several favourites (Recommendation) | Remix; lock Amy, front, Perfect Landing and Quick Starter; run Strict |
| Collection exclusions: another starting point (Ownership) | Compare real solver suggestions before/after manually excluding a suggested item; restore afterward |

The preserved `[Demo · Recommendation] Miku — Boost trade-off baseline` explains
Balanced with Boost parts and no tire. Its existing description supplies the
priorities/loss limits. Recommendations always come from the real solver.
Use one's own isolated demo account for the collection walkthrough; the seeder
does not change private ownership. Saves are not supported by the existing
fixture writer and are not fabricated here.

## Safety and reruns

The writer requires a loopback API, the current checkout's development process,
an established loopback database connection, healthy local Compose PostgreSQL,
current migrations, and matching database/API build identities. Applied migrations
are only read. Accounts are created/reused through the existing development-only
bootstrap, which verifies the reserved username/email/password identity.

Build ownership requires the existing manifest ID, expected author and matching
managed-content fingerprint. Titles never confer ownership. Unmanaged title
collisions and missing previously managed rows now become conflicts rather than
being adopted/recreated. Concurrent changes are checked before updates in the
legacy refresh mode. CurrentFeatures forbids that mode and preserves existing
build content and engagement, including manual edits. New builds use the existing
REST writer and manifest checkpoint after each creation. A crash between creation
and checkpoint leaves a title conflict for review; it does not authorize adoption.

CurrentFeatures creates engagement for newly added community builds only; it does
not refill or reconcile engagement on pre-existing builds on a later run. This
preserves human activity, including intentional deletion. A partial interrupted
engagement run must be reviewed rather than forcefully reconciled. No rows are
deleted by CurrentFeatures. Existing Wilson/comment fixtures are retained.

## Initial additive generation verification on 2026-10-01

The historical checks below precede normalization. Current naming, the combined
scenario limitation and cleanup verification are recorded in
[demo-fixture-normalization.md](demo-fixture-normalization.md).

The DEV stack was already healthy when generation resumed. Apply added exactly
ten builds and no votes, comments or map associations. Actual counts after apply:

| Scope | Users | Builds | Votes | Comments | Map associations |
| --- | ---: | ---: | ---: | ---: | ---: |
| Reserved demo accounts / manifest-owned builds | 161 | 252 | 1,745 | 562 | 358 |
| Whole DEV database | 167 | 256 | 1,745 | 562 | 360 |

The manifest also retains one earlier Amy fixture outside the current 251-build
plan. Its content and existing manual engagement explain differences from plan
totals. The manifest has 67 labelled teaching builds and 185 ordinary titles;
36 teaching fixtures have the specific ranking/comment/scenario/recommendation/
optimizer roles listed above. Five builds outside the current plan were retained
(four outside the manifest and the earlier Amy fixture).

- `pwsh -NoProfile -File backend/scripts/SeedDemoData.Tests.ps1`: passed. Tests
  cover deterministic planning, existing ranking/comment fixtures, valid plates,
  Boost composition, all new identities, multiple-gadget locks, controlled prefixes,
  missing-catalog rejection, no stored scenario/engagement and conflicting modes.
- Offline preview against the existing DEV catalog snapshot passed composition,
  source-type, patch, stat-data, gadget and map-reference validation for all 251 builds.
- Live preflight passed against local Compose PostgreSQL and Flyway V33. All
  252 actual manifest builds passed composition, references, source racing-type
  and Gadget Plate checks; all 49 Boost builds have no tire.
- The pre-apply snapshot comparison confirmed all 246 existing build rows,
  309 gadget associations, 1,745 votes and 562 comments stayed identical.
- A post-apply generator preview proposed zero additions or updates: 251 plan
  builds retained, with five manual records preserved. This was a read-only
  rerun preview, not a second apply.
- Live scenario endpoint checks passed for inactive/active Quick Starter (+20),
  Super Slow Starter (+60), Sea Dog (+20), Ace Pilot (+20), All-Rounder in both
  Water and Flight (+20), and Ring Engine (0 versus 1 held ring, +12). Each
  numeric check covered all five stats. Perfect Landing returned ACTIVE_NON_STAT
  with no points. Damage Evolution returned PARTIAL/UNSUPPORTED with no total.
  Ace Pilot and All-Rounder retain separate unsupported utility details while
  their verified numerical effects calculate normally.
- Explore returned 256 builds and distinct 12-item pages. Racer, source machine,
  title, patch and specific-map filters returned useful results. The latest-patch
  filter returned 233 builds; a specific map returned eight. The 101-comment
  fixture returned two distinct 20-item pages. The three established Top 3 Demo
  winners remained in place; new teaching fixtures did not enter Top 3.
- Browser checks through localhost:5173 confirmed fixture search, details and
  side-by-side Compare for Quick Starter versus Sea Dog. Signing into the
  isolated optimizer account and using Remix on the combined-locks fixture
  copied Amy, all three selected Speed parts and both ordered gadgets into a
  legal 2/6-slot draft. The draft was not published; no build was created.

No full backend/integration suite or frontend build was run for this fixture-only
change. Existing targeted generator tests and live data checks are the relevant
verification; application source was unchanged.

No backend/frontend application behavior or architecture changed. No production
access, schema changes, migrations, reset, service restart, commit or push occurred.
