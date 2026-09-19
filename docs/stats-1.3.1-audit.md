# Ver. 1.3.1 base-stat audit

Checked 2026-09-19 against main `03cfa842625ef083e9f134e7cc81e7ff03256e60`.
Decision: **no numerical data imported**. V16 creates storage only. All catalog identities survive.
Test fixtures containing numbers are synthetic, isolated test snapshots, not game-data evidence.

## Evidence and limits

- **S1:** [supplied community spreadsheet](https://docs.google.com/spreadsheets/d/1StdwFmVCdYBJnCi7HpuzW5iPU3czAv0njwjgaJcCgY4/edit?gid=0).
  The normal research reader could not open it; direct public HTML and
  [CSV export](https://docs.google.com/spreadsheets/d/1StdwFmVCdYBJnCi7HpuzW5iPU3czAv0njwjgaJcCgY4/export?format=csv&gid=0)
  were accessible. One tab, Sheet1, contains a machine-name column labelled Parts and a character
  table. No visible patch attribution or historical revision establishes Ver. 1.3.1. It includes
  36 character rows including excluded Super Sonic, 35 matching RingLab racers. Rush Roadstar and
  PAC-MAN Mobile have empty machine values. Machine rows do not distinguish FRONT/REAR/TIRE.
  Fractional values exist (e.g. Sakura Board acceleration 17.5), so storage must not round to integers.
- **S2:** [SRCW Stats](https://srcwstats.com/). Accessible app shell and public JavaScript entry
  `/assets/index-D7yDazzX.js`, inspected for historical version attribution. No readable historical
  1.3.1 dataset was obtained. The text reader exposes no table. No database credentials or external
  service calls were used to retrieve its data. This is an access/provenance limitation, not evidence
  of agreement with S1.
- **S3:** [SRC Gadget Builder](https://www.srcgadgetbuilder.com/build). Accessible server-rendered
  shell provides character/machine/gadget controls and an applied-gadget-effects panel, but no
  historical 1.3.1 numerical table was obtained from the inspected content. Current gadget values
  are excluded. Its linked reference spreadsheet does not establish independent corroboration.
- **L:** [existing catalog ledger](game-data-sources.md), V2/V4/V6/V10/V11, and the existing
  `game_versions` row date 2026-03-18. L supports identity/release classification, not historical
  numerical stats. The ledger's linked official release notices remain the release evidence.

No confirmed numerical disagreement could be established between two dated 1.3.1 sources, because
no such comparable pair was obtained. **This does not mean the sources agree.** Unresolved issues:

1. S1's patch applicability is unverified for every populated row.
2. S1's machine-level values cannot be assigned to three individual part rows or divided by three
   without evidence. Extreme Gear component semantics are deliberately deferred.
3. S2/S3 historical values were unavailable to this audit; independent numerical comparison remains
   manual work. No CONFLICT row is asserted without an observed contradictory value.
4. S1 omits some already released entities and contains future-name placeholders. Presence in S1
   does not prove release, completeness, or applicability to the target snapshot.

The task's release-date rule is applied at the 2026-03-18 baseline: releases after that date have
no entry in this snapshot, including the March 25 Mega Man pack. This classification does not
claim which executable patch was running on their later release day. Ver. 1.3.2 is not a new
balance snapshot; Ver. 1.4.1 is not modelled or backfilled.

## Classification

VERIFIED means all five historical values are supported; PARTIAL means some are; CONFLICT means
observed unresolved disagreement; NOT_FOUND means no verifiable historical block was obtained
(even if an undated current number exists); NOT_IN_VERSION means release after the baseline.
All numbers below describe persisted 1.3.1 data after V16, not source-table population.

| Entity | Complete | Partial | No stats | NOT_FOUND | NOT_IN_VERSION |
| --- | ---: | ---: | ---: | ---: | ---: |
| Racers | 0 | 0 | 52 | 41 | 11 |
| Machine parts | 0 | 0 | 186 | 165 | 21 |

No verified/partial rows are seeded and no placeholder zero or all-null row is necessary.
Machine totals are derived, so there is no machine-stat dataset to seed.

## Racer ledger

Canonical names uniquely identify existing records; their stable IDs remain unchanged.
S1+L means the source has an undated matching character and L establishes its identity. L-only
means S1 has no matching character row. S2/S3 limitations apply to every row.

| Racer | Classification | Evidence / reason |
| --- | --- | --- |
| Sonic the Hedgehog | NOT_FOUND | S1+L; undated Sonic alias |
| Miles "Tails" Prower | NOT_FOUND | S1+L; Tails alias |
| Knuckles the Echidna | NOT_FOUND | S1+L; Knuckles alias |
| Amy Rose | NOT_FOUND | S1+L |
| Cream & Cheese | NOT_FOUND | S1+L; Cream and Cheese alias |
| Big the Cat | NOT_FOUND | S1+L; Big alias |
| Silver the Hedgehog | NOT_FOUND | S1+L; Silver alias |
| Blaze the Cat | NOT_FOUND | S1+L; Blaze alias |
| Shadow the Hedgehog | NOT_FOUND | S1+L; Shadow alias |
| Rouge the Bat | NOT_FOUND | S1+L; Rouge alias |
| E-123 Omega | NOT_FOUND | S1+L |
| Vector the Crocodile | NOT_FOUND | S1+L; Vector alias |
| Espio the Chameleon | NOT_FOUND | S1+L; Espio alias |
| Charmy Bee | NOT_FOUND | S1+L; Charmy alias |
| Zavok | NOT_FOUND | S1+L |
| Zazz | NOT_FOUND | S1+L |
| Dr. Eggman | NOT_FOUND | S1+L |
| Metal Sonic | NOT_FOUND | S1+L |
| Egg Pawn | NOT_FOUND | S1+L |
| Sage | NOT_FOUND | S1+L |
| Jet the Hawk | NOT_FOUND | S1+L; Jet alias |
| Wave the Swallow | NOT_FOUND | S1+L; Wave alias |
| Storm the Albatross | NOT_FOUND | S1+L; Storm alias |
| Hatsune Miku | NOT_FOUND | S1+L |
| Joker | NOT_FOUND | S1+L |
| Ichiban Kasuga | NOT_FOUND | S1+L |
| NiGHTS | NOT_FOUND | L; absent from S1 character table |
| AiAi | NOT_FOUND | L; absent from S1 character table |
| Tangle | NOT_FOUND | L; absent from S1 character table |
| Whisper | NOT_FOUND | L; absent from S1 character table |
| Red | NOT_IN_VERSION | L; 2026-04-15 |
| Goro Majima (Captain Majima) | NOT_IN_VERSION | L; 2026-04-29 |
| Arle | NOT_IN_VERSION | L; 2026-05-27 |
| Classic Sonic | NOT_IN_VERSION | L; June 2026 release notice |
| Axel | NOT_IN_VERSION | L; 2026-08-25 |
| Steve | NOT_FOUND | S1+L |
| Alex | NOT_FOUND | S1+L |
| Creeper | NOT_FOUND | S1+L |
| SpongeBob SquarePants | NOT_FOUND | S1+L; SpongeBob alias |
| Patrick Star | NOT_FOUND | S1+L |
| PAC-MAN | NOT_FOUND | L; absent from S1 character table |
| Blinky | NOT_FOUND | L; absent from S1 character table |
| Mega Man | NOT_IN_VERSION | L; 2026-03-25 |
| Proto Man | NOT_IN_VERSION | L; 2026-03-25 |
| Leonardo | NOT_IN_VERSION | L; 2026-07-28 |
| Raphael | NOT_IN_VERSION | L; 2026-07-28 |
| Donatello | NOT_IN_VERSION | L; 2026-07-28 |
| Michelangelo | NOT_IN_VERSION | L; 2026-07-28 |
| Tails Nine | NOT_FOUND | S1+L; Nine alias |
| Knuckles the Dread | NOT_FOUND | S1+L |
| Rusty Rose | NOT_FOUND | S1+L |
| Werehog | NOT_FOUND | S1+L; Sonic the Werehog alias |

## Machine-part ledger

Each row explicitly classifies **all three existing records**, identified by unique
`(source machine name, FRONT/REAR/TIRE)`. Thus these 62 rows cover 186 part records without
implying that the game's actual Extreme Gear layout has three parts. NF = NOT_FOUND;
NV = NOT_IN_VERSION. S1 machine values are undated and not per-part contributions in every case.

| Source machine | FRONT | REAR | TIRE | Evidence / reason |
| --- | --- | --- | --- | --- |
| Speedster Lightning | NF | NF | NF | S1+L; no verified per-part breakdown |
| Dark Reaper | NF | NF | NF | S1+L |
| Whirlwind Sport | NF | NF | NF | S1+L |
| Jumble Rage | NF | NF | NF | S1+L |
| Pink Cabriolet | NF | NF | NF | S1+L |
| Neo Lightron | NF | NF | NF | S1+L |
| Land Smasher | NF | NF | NF | S1+L |
| Road Dragoon | NF | NF | NF | S1+L |
| TYPE-J Iota | NF | NF | NF | S1+L; Extreme Gear mapping deferred |
| TYPE-S Stream | NF | NF | NF | S1+L; Extreme Gear mapping deferred |
| Diva Macchina | NF | NF | NF | S1+L; Extreme Gear mapping deferred |
| Arsene Wing | NF | NF | NF | S1+L |
| Dragon Brave | NF | NF | NF | S1+L |
| Dream Sleeper | NF | NF | NF | L; absent from S1 |
| Banana Cruiser | NF | NF | NF | L; absent from S1 |
| Super Roaster | NV | NV | NV | L; 2026-04-15 |
| Goromaru | NV | NV | NV | L; 2026-04-29 |
| Twinkle Bayoen | NV | NV | NV | L; 2026-05-27 |
| Mach Cyclone | NV | NV | NV | L; June 2026 release notice |
| Devolada Yellow Jack | NV | NV | NV | L; 2026-08-25 |
| Minecart | NF | NF | NF | S1+L; Mine Cart alias |
| Patty Wagon | NF | NF | NF | S1+L |
| PAC-MAN Mobile | NF | NF | NF | L; S1 values empty |
| Rush Roadstar | NV | NV | NV | L; 2026-03-25; S1 values empty |
| Pizzafire Van | NV | NV | NV | L; 2026-07-28 |
| Blue Star | NF | NF | NF | S1+L; Extreme Gear mapping deferred |
| Mirage Blade | NF | NF | NF | S1+L |
| Super Shining | NF | NF | NF | S1+L |
| Royal Chariot | NF | NF | NF | S1+L |
| Stealth Chaser | NF | NF | NF | S1+L |
| Retro Future | NF | NF | NF | S1+L |
| Wild GT | NF | NF | NF | S1+L |
| Buster M | NF | NF | NF | S1+L |
| Hyper Scorpion | NF | NF | NF | S1+L |
| Cyber Spear | NF | NF | NF | S1+L |
| Pawn Calibur | NF | NF | NF | S1+L |
| Long Stinger | NF | NF | NF | S1+L |
| Radical Fours | NF | NF | NF | S1+L |
| Racing Buggy | NF | NF | NF | S1+L |
| Giganto Liner | NF | NF | NF | S1+L |
| Lip Spyder | NF | NF | NF | S1+L |
| Victoria Carriage | NF | NF | NF | S1+L |
| Moto Beetle | NF | NF | NF | S1+L |
| Ancient Throne | NF | NF | NF | S1+L |
| Little Lady | NF | NF | NF | S1+L |
| Hot Hatch | NF | NF | NF | S1+L |
| Fossil Rock | NF | NF | NF | S1+L |
| Knight Tank | NF | NF | NF | S1+L |
| Fang Loader | NF | NF | NF | S1+L |
| Beat Gator | NF | NF | NF | S1+L |
| Frog Cruiser | NF | NF | NF | S1+L |
| Cross Dozer | NF | NF | NF | S1+L |
| Trail Runner | NF | NF | NF | S1+L |
| Egg Drillster Mk.II | NF | NF | NF | S1+L |
| Beast Spike | NF | NF | NF | S1+L |
| Sakura Board | NF | NF | NF | S1+L; fractional value; mapping deferred |
| Triple Fan | NF | NF | NF | S1+L; fractional value; mapping deferred |
| Pop Float | NF | NF | NF | S1+L; mapping deferred |
| TYPE-W Windy | NF | NF | NF | S1+L; mapping deferred |
| Wispon Booster | NF | NF | NF | S1+L; mapping deferred |
| Quad Copter | NF | NF | NF | S1+L; mapping deferred |
| Jaws Rocket | NF | NF | NF | S1+L; mapping deferred |

## Manual data review before an import

Obtain a dated 1.3.1 capture or revision with clear stat units, verify character aliases and
individual FRONT/REAR/TIRE contributions against an independent source, and record disagreements
per field. Do not infer values from type, stock totals, nearby characters, or blank cells. Resolve
Extreme Gear layout separately before importing contributions that depend on that correction.
Then add a new Flyway data migration with provenance here. This empty baseline is safe to review
as infrastructure, but must not be described as a verified populated historical dataset.

## Implementation and verification handoff

Files added:

- `backend/src/main/resources/db/migration/V16__versioned_base_stats.sql`
- `backend/src/main/java/dev/ringlab/domain/gamedata/BaseStats.java`
- `backend/src/main/java/dev/ringlab/port/out/BaseStatsRepository.java`
- `backend/src/main/java/dev/ringlab/application/gamedata/BaseStatsService.java`
- `backend/src/main/java/dev/ringlab/adapter/out/db/gamedata/BaseStatsDbAdapter.java`
- `backend/src/main/java/dev/ringlab/adapter/in/rest/gamedata/BaseStatsRestResource.java`
- `backend/src/test/java/dev/ringlab/domain/gamedata/BaseStatsTest.java`
- `backend/src/test/java/dev/ringlab/application/BaseStatsServiceTest.java`
- `backend/src/test/java/dev/ringlab/BaseStatsIntegrationTest.java`
- `frontend/src/BaseStats.tsx` and `frontend/src/BaseStats.test.tsx`
- `docs/stats-1.3.1-audit.md`

Files modified: `frontend/src/types.ts`, `frontend/src/styles.css`, the four page files
`frontend/src/pages/{GameData,BuildEditor,BuildDetails,CompareBuilds}.tsx`,
`frontend/src/pages/GameData.test.tsx`, `docs/game-data-schema.md`, `docs/architecture.md`.
No dependency, previous migration, build-write contract, gadget rule, or catalog row changes.
An unrelated untracked `Start-RingLab.ps1` appeared during implementation and was left untouched.

Actual verification on 2026-09-19:

- `mvn -f backend/pom.xml "-Dtest=BaseStatsTest" test`: passed, 3 tests; main and test compilation passed.
- `mvn -f backend/pom.xml "-Dtest=BaseStatsServiceTest" test`: passed, 4 tests; updated test compilation passed.
- From `frontend`, `npm run test:coverage`: passed, 103 tests, all existing gates passed
  (statements/lines 83.13%, branches 83.89%, functions 71.69%).
- After adding the catalog visibility test and separating the compare stats panel,
  `npm test -- src/pages/GameData.test.tsx src/pages/CompareBuilds.test.tsx`: passed, 10 tests.
- From `frontend`, `npm run build`: passed again after those final frontend changes.
- `git diff --check`: passed; only Git's existing LF/CRLF conversion notices.

Database-backed tests, packaged API tests and the backend coverage gate remain **unverified**.
Docker Desktop was started once, but exited. Its host log reports that startup cannot access/rename
`C:/Users/Alex/AppData/Local/Docker/run/sailor-ingest.sock` to `.stale`. The Linux engine named pipe
never became available. No Docker reset, volume deletion, dependency retry, development reseed or
production action was performed. The requested complete local stack/proxy check is consequently
blocked; no working local site is claimed.

After restoring Docker, run `mvn -f backend/pom.xml test` for all unit/Quarkus tests. Run
`mvn -f backend/pom.xml verify` with the [README's packaged-test setup](../README.md#testing)
and a **disposable test database** for packaged verification and the unchanged backend coverage gate.
Inspect `backend/target/site/jacoco/index.html` after that run. The new integration class verifies
V16 persistence, composite-key uniqueness, version isolation, decimal/null JSON and create/edit
compatibility. It can also be run directly from IntelliJ. For the final frontend snapshot,
`npm run test:coverage` and `npm run build` remain the normal manual/CI commands.

Finally start the documented local stack (`docker compose up -d`, backend `mvn quarkus:dev`,
frontend `npm run dev`) and verify both the page and `/api/stats/catalog?gameVersionId=50000000-0000-0000-0000-000000000002`
through `http://127.0.0.1:5173`. Preserve development volumes, existing data and JWT keys.
