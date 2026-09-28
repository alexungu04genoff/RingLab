# Reviewed passive gadget rules

Reviewed 2026-09-27 for **CrossWorlds 1.4.1 only**. The official
[update index](https://asia.sega.com/SonicRacingCrossWorlds/en/update/) still listed
1.4.1 as its latest published game update when checked. Future patches are not assumed
compatible. Historical base-stat copies remain the approximations documented in the
existing base-stat ledger.

## Evidence and lineage

- [SEGA 1.4.1 patch notice](https://asia.sega.com/SonicRacingCrossWorlds/en/update/v-1-4-1.html)
  establishes changed tuner bonuses, Machine Kit bonus/removal of old penalties, and
  Panel Combo Kit bonuses. It does not establish every unchanged penalty or interaction.
- Meohong's [SRCW Gadget Builder](https://www.srcgadgetbuilder.com/build) credits the
  [original Gadget List, current 1.4 tab](https://docs.google.com/spreadsheets/u/0/d/1_B2mHZUP6J6jeZfcwbM2HkLvjkwMYuACE4l6Ktt-UkI/htmlview/sheet?headers=true&gid=0).
  Actual table cells and the builder's public structured `stat_table` records were read.
  These are one evidence lineage, not independent confirmations.
- Independent community documentation was inspected for
  [tuners](https://w.atwiki.jp/sonicracingcw/pages/45.html),
  [Machine Kits](https://w.atwiki.jp/sonicracingcw/pages/37.html),
  [Character Kits](https://w.atwiki.jp/sonicracingcw/pages/56.html),
  [Drift Charge Kit](https://w.atwiki.jp/sonicracingcw/pages/49.html), and
  [Panel Combo Kit](https://w.atwiki.jp/sonicracingcw/pages/88.html).
  Double Down's numeric penalty is supported by the original table/builder; this review
  does not claim an independent second numeric confirmation for it.
- [SRCW Stats](https://srcwstats.com/) returned an application shell. Public JavaScript
  was inspected as an alternative; it loads numeric rows from a remote database rather
  than embedding them. Those rows were not inspected, so it is **not** counted as numeric
  verification. RingLab uses no private API or runtime scraping.

## Implemented values

All values are **stat points**. Every Tuner gives a non-matching known MACHINE type +8 in
the named stat without a penalty. A matching MACHINE gets +20 and the listed penalty.
An unknown/incomplete machine requires selection; it does not receive +8.

| Gadget | Matching penalty | Stable ID suffix |
| --- | --- | --- |
| Speed Tuner 1 | Acceleration −4 | 052 |
| Speed Tuner 2 | Acceleration −2, Boost −2 | 053 |
| Acceleration Tuner 1 | Handling −4 | 054 |
| Acceleration Tuner 2 | Handling −2, Boost −2 | 055 |
| Handling Tuner 1 | Power −4 | 056 |
| Handling Tuner 2 | Speed −2, Power −2 | 057 |
| Power Tuner 1 | Acceleration −4 | 058 |
| Power Tuner 2 | Acceleration −2, Handling −2 | 059 |
| Boost Tuner 1 | Speed −4 | 060 |
| Boost Tuner 2 | Speed −2, Power −2 | 061 |

Five Machine Kits give +20 to their named stat only when MACHINE type matches, without
other stat-point penalties in 1.4.1. A known non-match receives no passive points.
Five Character Kits check RACER type: +7 to the named stat, with Speed → Acceleration −5,
Acceleration → Boost −5, Handling → Speed −5, Power → Handling −5, Boost → Power −5.
A non-matching racer receives neither bonus nor penalty.

Drift Charge Kit adds Handling +3. Panel Combo Kit adds Handling +8 and Power +8 without
requiring a panel event; ring rewards are a separate conditional effect. Double Down
subtracts 10 from all five stats; its double-item capability is separate.

V32 adds the previously absent ten Tuners (one slot each) and Boost Machine Kit (three
slots), with IDs `70000000-0000-4000-8000-000000000052` through `...062`. Existing costs and
identities are preserved; missing artwork uses the labelled fallback. It also corrects
older Ring Engine and Panel Combo prose that conflated units/conditions. No old migration
is edited and no derived total is stored.

## Stacking, caps and exclusions

Independent tuner documentation explicitly supports a same-type Tuner 1 + Tuner 2 pair.
The 2026-09-28 review also inspected the public [SRCW Gadget Builder](https://www.srcgadgetbuilder.com/build)
calculation module (`/_next/static/immutable/chunks/3fo_6rojgq9h9.js`): it chooses each
gadget's matching/nonmatching stat row and adds its signed values per stat, without
excluding bonuses that offset penalties. This is community-model corroboration,
not an independent in-game measurement. Ruleset `2026-09-28.3` applies that arithmetic
to all ten machine tuners via one `machine-tuners` stacking group. On a Speed machine,
both Speed, Acceleration and Power tuners give +40 Speed, +10 Acceleration,
+16 Power and −2 Boost. Other gadget families retain the existing overlap checks.

Any single applicable passive modifier is supported. Modifiers affecting disjoint stats are evaluated independently:
on a Handling machine, Drift Charge Kit (+3 Handling), Acceleration Tuner 2
(+8 Acceleration) and Boost Tuner 2 (+8 Boost) do not stack on the same stat.
This is arithmetic over the reviewed individual rules, not new evidence for arbitrary
stacking. Other overlapping pairs remain unsupported, independently of saved order.
Unrelated known modifiers survive those unresolved pairs. Non-matching zero effects
and separately classified race/non-stat effects do not block a known modifier.

No universal effective cap or final rounding rule is imposed. BigDecimal preserves decimal
precision; unknown base fields stay unknown. Values may exceed 100 or be negative. Results
are labelled known passive arithmetic, not exact complete in-game performance. The existing
base-bar display scale is separate from calculation.

Lap starters, Evolution, ring engines and water/flight kit bonuses are conditional and
excluded. Physical km/h are not Speed points. Charge timing, boost duration, item quantity
and order, recovery and invincibility are separate from five-stat adjustments. Handling
Character Kit's exact bundled Ring Thief variant is disputed; its +7/−5 passive part is
supported separately. The disputed ring quantity does not make that passive part unknown.
Unreviewed gadgets are explicitly unsupported, never assumed zero.
Dummy/unknown identities in the external table are not imported.

### Utility coverage correction (2026-09-28)

Ruleset `2026-09-28.4` classifies the 42 remaining catalog gadgets using the original
Gadget List's current 1.4 table, checked against the existing catalog descriptions.
Item, ring, recovery, invincibility and technique utilities contribute **zero passive
stat points**. Conditional race effects also contribute zero without an assumed event.
All-Rounder's transformation/terrain effects and Less is More's ring-dependent physical
speed remain conditional. Existing passive bonuses and penalties are unchanged.

The production failure was Charmy Bee + Ancient Throne with Damage Evolution,
Invincible Finish and Perfect Landing: valid stats `(29,66,61,35,49)` were marked partial
because the last two gadgets lacked rule entries. Their finish/landing effects now have
explicit conditional rules, so that complete setup can be a Balanced reference and
retain its gadgets. The same correction covers the other reviewed utilities, including
Handling Character Kit's ring-theft capability. Unknown identities and unresolved
overlapping passive modifiers still remain unsupported. No schema or catalog cost changes.

The subsequent [full 79-gadget audit](gadget-passive-audit-1.4.1.md) records every
catalog entry, matching/non-matching passive values, corrected utility explanations,
and the separate Substitute Item slot-cost discrepancy.

## Implementation and compatibility

### Acceleration Tuner 2 + Acceleration Machine Kit (2026-09-28)

Ruleset `2026-09-28.5` adds **only** the pair of `stats` effects on stable catalog
IDs ending `055` and `034`. Their existing individual values and slot costs are
unchanged. Other Tuner/Kit pairs have not been enabled by this review, nor does this
pair grant transitive permission to a third overlapping effect.

Reproduced through the existing local read-only draft endpoint: Knuckles the Echidna
and all three Giganto Liner parts on 1.4.1 returned base `(58,65,29,54,34)`, `PARTIAL`,
zero included adjustments and both passive effects `UNSUPPORTED`. The tuner had
`machine-tuners`; the kit had no stacking group. No saved build was edited.

Individual evidence: the original Gadget List's current 1.4 sheet, rows 133 and 159,
was read again. Tuner 2 gives matching `+20 Acceleration, -2 Handling, -2 Boost`,
otherwise `+8 Acceleration`; the kit gives matching `+20 Acceleration`, otherwise
no passive adjustment. SEGA's 1.4.1 notice confirms changed bonuses and the removed
kit penalty, but is **not** evidence of this pair's stacking.

Combined evidence: fetched the reference builder's publicly served calculation
module `/_next/static/immutable/chunks/3fo_6rojgq9h9.js` and data module
`/_next/static/immutable/chunks/252-neok-oop8.js`. Executed its actual exported
`computeBuildStats` function in an isolated Node VM with actual `gadget_041` and
`gadget_080` records. With the above base and an Acceleration machine it returned:

| Selection | Speed | Acceleration | Handling | Power | Boost |
| --- | ---: | ---: | ---: | ---: | ---: |
| Tuner 2 alone | 58 | 85 | 27 | 54 | 32 |
| Machine Kit alone | 58 | 85 | 29 | 54 | 34 |
| Both, either order | 58 | 105 | 27 | 54 | 32 |
| Both on a Speed machine, same numeric base | 58 | 73 | 29 | 54 | 34 |

Module SHA-256s, respectively:
`03875e8b15748b7122103179865058015e71f9f78a3db041d2817135b662133d` and
`0632652888229f510a4afa63f51215bed6a61304cae5190174cedd134e2f3fca`.
The function chooses type-dependent rows and sums signed points; this is observed
community-calculator behavior, not an independent in-game experiment. The original
sheet and builder remain one evidence lineage. No effective cap or in-game rounding
was established. Acceleration 105 is an unclamped raw subtotal, not a performance
guarantee. Recovery remains a separate race-time capability, never extra points.

Both recommendation modes use this same domain arithmetic. Pairwise rejection is
still monotone for fixed types: adding a third effect cannot make a rejected pair
supported. Tests exercise discovery from an empty gadget selection, both locked
orders, unresolved pairs, and draft/persisted agreement.

Verification: 37 pure backend tests passed (`PassiveStatsCalculatorTest`,
`PassiveStatsServiceTest`, `BuildRecommendationSolverTest`) using an offline Maven
run in a temporary source copy; 16 `PassiveStats.test.tsx` tests and the frontend
build passed. The running local API then returned `CALCULATED`, ruleset `.5`,
adjustments `(0,40,-2,0,-2)` and result `(58,105,27,54,32)` for the same read-only
draft request. No services were stopped and no saved data was changed. Integration
and full-suite tests were not run.

`PassiveGadgetRules` is a typed local table keyed by UUID, ruleset
`crossworlds-1.4.1-passive-2026-09-28.5`. `PassiveStatsCalculator` is pure domain arithmetic;
`PassiveStatsService` validates drafts, resolves actual racer/machine types separately, and
batches page data. Top-three exports reuse it with already resolved metadata. No new rules
engine, live scraping, ranking, ownership, publication or map mechanics are introduced.

`/api/stats/build` remains base-only. Added endpoints:
`/api/stats/passive-build` (draft references and repeated `gadgetId`),
`/api/stats/persisted/{id}` (saved references including readable legacy base stats), and
`/api/stats/gadget-rules` (collection metadata). Page/saved/top-three stats retain their base
fields and add `passive` data containing coverage, adjustments, result, effect statuses and
provenance. Enriched cards make no extra requests. Keys include patch, racer, all parts,
gadget IDs and ruleset; maps are excluded. The UI aborts stale requests, starts in Base, and
uses one shared comparison mode. JSON, copy and Discord label adjusted values separately.

## Isolated verification and integration

Worktree: `C:\Users\Alex\.codex\worktrees\passive-gadget-stats\RingLab`.
Its base is integrated commit `af463a9`, including the completed map/UI/community work.
The gadget implementation is uncommitted on `codex/passive-gadget-stats`; checking out
that branch elsewhere does not copy its uncommitted new and modified files.
Databases `ringlab_gadgets_test` and `ringlab_gadgets_preview` use the isolated PostgreSQL
instance on loopback port 55435. Preview ports: backend 8083, frontend 5175. Initial
implementation and verification left the existing 8080/5173 processes and database untouched.

Targeted IntelliJ classes: `PassiveStatsCalculatorTest`, `PassiveStatsServiceTest`.
Apply V32 to a chosen dev database only during deliberate later startup after integrating
the reviewed source. Keep the existing base-stat migrations; do not reseed community data.
For later integration, review the complete worktree diff including new files, integrate
the backend, frontend and V32 together, and use the normal documented local startup.
Flyway adds catalog identities and corrects gadget descriptions; it does not change saved
build selections, users, votes, comments or map recommendations. The first start applies
V32 to whichever database the operator selects. Back up that database beforehand.

Actual checks (2026-09-27):

- 18 targeted backend tests passed, including calculator/service and page adapters.
- The final backend run passed all 290 unit/acceptance/integration tests, including
  architecture, password recovery and maps. Old exact-object assertions were updated to
  check unchanged base values separately from the new additive `passive` field.
- Maven `verify` completed successfully, including all 18 packaged API tests and the
  unchanged backend coverage thresholds. The packaged launch emitted an existing
  unrecognized logging-category configuration warning; no test failed.
- Frontend: all 252 tests in 32 files passed with `npm run test:coverage -- --maxWorkers=2`.
  The existing coverage thresholds passed without changes.
- `npm run build` passed (TypeScript and Vite).

For a later full check, set `DB_USER` and `DB_PASSWORD` for the isolated test database,
then run from `backend` in PowerShell (Java 21 and Maven on PATH):

```powershell
$env:DB_URL = 'jdbc:postgresql://127.0.0.1:55435/ringlab_gadgets_test'
$env:PUBLIC_BASE_URL = 'http://localhost:5175'
mvn "-Dquarkus.datasource.jdbc.url=$env:DB_URL" "-Dquarkus.datasource.username=$env:DB_USER" "-Dquarkus.datasource.password=$env:DB_PASSWORD" -Dquarkus.datasource.devservices.enabled=false -Dquarkus.compose.devservices.enabled=false -Dquarkus.http.test-port=8085 -Dquarkus.http.test-ssl-port=8446 verify
```

From `frontend`, run `npm run test:coverage -- --maxWorkers=2` and `npm run build`.
Stop only this worktree's preview backend before Maven verification so it cannot rebuild
the same output directory concurrently; the normal 8080/5173 dev stack can stay running.

The isolated frontend is `http://127.0.0.1:5175` (API target 8083). After verification,
the preview backend runs the verified packaged application with its isolated preview
database, mock mail and separate JWT keys. Its log is
`E:\RingLab\.tools\gadgets-preview-backend.log`. External news is disabled in this preview.
After the restart, the frontend and persisted stats API both responded successfully
through the frontend proxy. The visible example was refreshed with passive adjustments displayed
and confirmed Speed 105, Acceleration 24, Handling 59, Power 52, Boost 32.

Five isolated examples use Amy Rose + Speedster Lightning in 1.4.1. Their base stats are
Speed 65, Acceleration 30, Handling 59, Power 52, Boost 34:

| Example build ID | Modifier | Demonstrated result |
| --- | --- | --- |
| `1b47c64d-df45-417e-80e6-1b98871a56cb` | None | Base unchanged |
| `944e61cf-8f0b-4eb2-8d29-13f5f1b45528` | Drift Charge Kit | Handling 59 + 3 = 62 |
| `a78b0c12-f8cb-4ea6-9c4b-5f9103c78fac` | Speed Tuner 1 | Speed 65 + 20 = 85; Acceleration 30 − 4 = 26 |
| `35cc2a35-da40-491f-a13e-18d3eb0cfdd7` | Quick Starter | Base unchanged; lap effect explicitly excluded |
| `80dd7693-1102-4345-b3be-a995f1118173` | Speed Tuners 1 + 2 | Speed 105; Acceleration 24; Boost 32 |

Browser checks confirmed saved-detail values above 100, signed penalties and effect
explanations on desktop and at 360/390/412 CSS pixels. The narrow detail action row now
wraps; no horizontal document overflow remained at those widths. Comparison has exactly
one mode control and showed Speed 105 versus 65 with Quick Starter's conditional effect
excluded. Recommended maps remain at the bottom of both comparison columns.

Editor checks confirmed removing Tuner 2 recalculates Speed to 85 and Acceleration to 26;
selecting Water Palace leaves those values unchanged and updates the map preview.
Selecting 1.3.1 makes gadget results unavailable while Base remains readable. Explore,
Top 3 and Saved Builds displayed the same values as the persisted API. The collection
displayed matching +20/−4 and non-matching +8 from the shared metadata; its descriptions
and expanded rules use the whole card width. No browser console errors were observed.
The 13 affected collection/passive presentation tests passed again after the layout fixes.

Current presentation (2026-09-27): cards, editor, details and comparison automatically
include supported passive adjustments, without a display-mode toggle. Solid
racer, striped machine and outlined gadget segments distinguish the contributions.
Coverage stays visible; base/adjustment/result arithmetic and effect explanations
remain in Details. Unsupported calculations retain readable base values. Map pills
fit their content, and machine thumbnails are 48px with readable part badges.

After those changes, `npm run test:coverage -- --maxWorkers=2` again passed all 252 tests
in 32 files (91.96% statements/lines, 91.08% branches, 87.13% functions), and
`npm run build` passed. Logs: `E:\RingLab\.tools\gadgets-final-ui-coverage.log` and
`E:\RingLab\.tools\gadgets-final-ui-build.log`. Backend source is unchanged since the
successful final Maven verification, so that completed run was not repeated.
The final card layout was checked on desktop and at 360, 390 and 412 CSS pixels:
the 105 Speed subtotal, signed reductions, expanded arithmetic and effect details
fit without horizontal overflow or clipped numeric rows. The persisted API through
port 5175 still returned Base Speed 65 and adjusted Speed 105.

Cards also show a Gear / signed-point marker under each stat with an applied modifier,
including in Base mode. Opening it names the contributing gadgets and shows the arithmetic,
explicitly stating whether the displayed bar includes the adjustment. Conditional,
non-stat and unsupported effects cannot create these numeric markers. The 8 focused
passive presentation tests and frontend build passed after this addition; Drift Charge's
Handling +3 disclosure was checked at 390px without horizontal overflow.

Public development integration (2026-09-27, subsequently authorized by the user):
the complete feature was copied into `E:\RingLab`, preserving the badge styling.
Before V32, the active Docker `ringlab` database was backed up to
`E:\RingLab\.tools\ringlab-before-gadgets-20260927-041909.dump` and its archive validated.
The existing development server hot-reloaded successfully without restarting services.
V32 succeeded; the 63 builds, 855 votes and 439 comments remained unchanged.
The frontend production build and `git diff --check` passed. Through
`https://dev.ringlabgarage.com/?sort=rated`, the Knuckles community card displayed
Drift Charge Kit's Handling marker and arithmetic `53 +3 = 56`; switching its mode
changed the displayed Handling value to 56 with an explicit partial-calculation label.
The marker was checked on desktop and at 390 CSS pixels. No production access,
community regeneration, commit or push was performed.

## Drift Spinner Kit clarification (2026-09-28)

The catalog description and [Gadget reference](https://sonic.fandom.com/wiki/Gadget_(Sonic_Racing%3A_CrossWorlds))
describe faster Level 1 drift charging and knockback while drifting. These are
non-stat race-time effects, with no five-stat point adjustment. Rule `drift-spinner`
now classifies them explicitly, allowing a complete starting setup with Drift Spinner
Kit and Inventory Swap to serve as a Balanced reference. No charge-time percentage,
knockback strength, or Handling bonus is inferred. Unknown gadgets remain unsupported.
