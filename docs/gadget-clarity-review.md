# Gadget clarity, acquisition and artwork review

Reviewed 2026-10-09 against main `d0bc85b7dfe52ad33edef2aa3608d234cb9944be`.
This changes presentation and acquisition metadata, not recommendation mathematics,
costs, numerical snapshots, rule classifications or compatibility.

The artwork-completion results below describe the first pass. The subsequent
[uniform artwork pass](#uniform-artwork-pass) supersedes its mixed-source image set.

## Reproduction and guidance

`Phase5bCatalogTest` reproduces 130 Ring Limit + 200 Ring Limit + 4th Stage Charge
Kit in both Strict and Balanced modes. The plate fits (1 + 2 + 3, rows of three).
The 4th Stage kit has an UNSUPPORTED effect. KEEP_CURRENT is unavailable;
OPTIMIZE_UNLOCKED with no gadget locks succeeds without that kit; locking it
makes optimization unavailable. Unknown effects have never counted as zero.

The old frontend heading was **Recommendation unavailable**, followed by:
“Required stats or rules are missing or unsupported. Details below explain what
could not be evaluated.” Strict added “Legal completions exist, but no fully
evaluable passive-stat candidate is available with these locks.” Balanced added
“Legal completions exist, but no complete supported passive candidate can be
evaluated. Check the selected patch, parts and kept/locked gadgets.”

The dialog now names every reviewed unsupported gadget required by its scope and
locks before Calculate. Keeping it says to remove it or choose Optimize unlocked
gadgets. Locking it says to unlock/remove it in the editor and reopen recommendations.
An unlocked unsupported selection permits calculation and explains that the
optimizer may remove it. The backend's unavailable reason also names required
unsupported gadgets. Missing frontend metadata leaves the server authoritative;
partial current-stat display alone does not block candidate search.

## Shared presentation and acquisition

`features/gadgets/GadgetMetadata.tsx` and `gadgetPresentation.ts` are shared by
the selector, Game Collection, recommendation selections and build-detail tooltips.
PASSIVE → Passive stats; CONDITIONAL → Race condition; NON_STAT → Utility;
UNSUPPORTED → Effect unsupported. Multiple kinds produce multiple badges.
Scenario modeled requires an actual reviewed scenario control, independently of
the effect kind. A native expandable legend explains each category.

`GadgetAcquisitionKind` is a domain enum: STANDARD_UNLOCK, FESTIVAL_REWARD, UNKNOWN.
V36 adds string-persisted columns; the existing importer and API carry the kind
and optional label. Festival labels are required; UNKNOWN has no label.
All 117 identities were reviewed: **99 standard unlocks, 18 festival rewards,
0 unresolved acquisition kinds**. Exact ordinary progression thresholds are
omitted because sources disagree on some thresholds. The UI only badges festival
history; acquisition details explain standard/unknown records on demand.

| Gadget | Originally awarded during |
|---|---|
| Boost Character Kit | Hatsune Miku Festival |
| Power Character Kit | Minecraft Festival |
| Handling Character Kit | Joker Festival |
| Comeback Kit | Ichiban Kasuga Festival |
| Sea Dog Kit | SpongeBob Festival |
| Acceleration Character Kit | PAC-MAN Festival |
| Ace Pilot Kit | NiGHTS Festival |
| All-Rounder Kit | Super Monkey Ball Festival |
| Wisp Hoarder Kit | Tangle & Whisper Festival |
| Speed Character Kit | Red Festival |
| Item Buster Kit | Mega Man Festival |
| Drift Spinner Kit | Captain Majima Festival |
| Panel Combo Kit | Puyo Puyo Festival |
| Spin Dash Kit | Sonic 35th Anniversary Festival |
| Perfect Charge Kit | Axel Festival |
| Speedy Shortcut Kit | Hatsune Miku 2nd Festival |
| Air Trick Action Kit | TMNT Festival |
| 4th Stage Charge Kit | Samba de Amigo Festival |

The complete [acquisition ledger](../game-data/review/gadget-acquisition.csv)
maps each UUID and English identity to the Phase 5A community builder identifier
and Japanese category page. “Red” is the official character name; the builder's
Angry Birds festival label refers to the same event. These are original reward
histories, never claims of permanent exclusivity. Japanese pages list later ticket
purchases for older rewards; official updates also document returning gadgets.

Evidence includes the existing [Phase 5A/5B review](game-data-phase5b.md),
[Yoshister list](https://docs.google.com/spreadsheets/u/0/d/1_B2mHZUP6J6jeZfcwbM2HkLvjkwMYuACE4l6Ktt-UkI/htmlview/sheet?headers=true&gid=0),
[community builder](https://www.srcgadgetbuilder.com/build),
[Japanese advanced kits](https://w.atwiki.jp/sonicracingcw/pages/88.html),
[character kits](https://w.atwiki.jp/sonicracingcw/pages/56.html),
[official 1.4.1 notice](https://asia.sega.com/SonicRacingCrossWorlds/en/update/v-1-4-1.html)
and [official Amigo notice](https://sonicracing.sega.com/en/details/000118wbfzajpy.html).
Earlier official festival/artwork references remain in [catalog sources](game-data-sources.md).

## Artwork inventory

The [117-row inventory](../game-data/review/gadget-artwork.json) records the before
canonical path, fallback-map entry, matching existing asset, rendered result and
status alongside the final canonical path, exact source identity and SHA-256.
New downloads also record page, attachment URL, attribution, retrieval date and
deterministic local filename.

| State | Before | After |
|---|---:|---:|
| Canonical local icon | 68 | 117 |
| Existing icon rendered through transitional fallback map | 11 | 0 needed |
| Exact local icon present but unwired | 2 | 0 |
| Missing local icon | 36 | 0 |
| Uncertain identity | 0 | 0 |

**81 existing assets were reused**: all 68 canonical assets, ten Tuner 1/2 icons
and Boost Machine Kit from the fallback map, and previously unwired Perfect
Charge Kit and Air Trick Action Kit. The fallback map retains its 11 entries
for older API responses; it was not expanded. Canonical CSV paths now drive every
current icon (49 blank paths filled).

**36 exact icons were newly sourced** from the documented Japanese wiki's isolated
in-game attachment images: Air Trick Bounty, Item Attack Bounty, Runoff Bounty,
Slipstream Bounty, Travel Ring Bounty, Morph Action Bounty, Perfect Charge Bounty,
Dash Panel Bounty, Dash Panel Mini Bounty, Dash Panel Combo Bounty, 200 Ring Limit,
Ring Gain Mini Boost, Ring Gain Boost, Ring Range UP, Air Drift Mobility UP,
Charge Jump Mobility UP, Air Trick Adept, Air Trick Expert, Lv1 Quick Charge,
Lv2 Quick Charge, Lv3 Quick Charge, Technical Drift, Counter Quick Charge,
Maximum Traction, Second Wind, Bumper Guard, Boost Starter, Double Boost Specialist,
Dark Chao Starter, Giant Spiked Iron Ball, Short Fuse, Monster Truck Starter,
Item Hoarder Kit, Damage Support Kit, Speedy Shortcut Kit and 4th Stage Charge Kit.

Source bytes are unchanged: new JPEGs retain their pale backgrounds and existing
transparent images retain transparency. CSS contains images without distortion.
No page UI screenshots, approximations, generated replacements or hotlinks are
used. All 117 files have distinct paths and checksums. **No gadget-icon gaps
remain.** Artwork belongs to SEGA and respective rights holders; attribution is
not a claim that artwork is public domain.

## Ring-capacity compatibility finding

The [ring category](https://w.atwiki.jp/sonicracingcw/pages/42.html) describes
130 Ring Limit and 200 Ring Limit as capacity effects. The advanced-kit page
describes Comeback Kit's 110-ring cap and says it does not stack with ring-capacity
gadgets. The [bounty category](https://w.atwiki.jp/sonicracingcw/pages/41.html)
also describes Route Planner Bounty granting 100 rings and a 200-ring cap when
selecting the Travel Ring destination. Yoshister corroborates those capacities.

These descriptions do **not** establish that the game forbids equipping the
gadgets together, nor settle every precedence interaction. Non-stacking effects
and illegal equipment are different claims. No prohibition was added. The
130 + 200 pair still passes existing plate validation; recommendation support
remains separate. Direct in-game confirmation or an explicit reliable equip
restriction is needed before implementing compatibility validation.

## Verification and local review

Focused backend tests: Phase5bCatalogTest, GameDataCsvReaderTest,
GadgetArtworkInventoryTest, GameDataImportServiceTest and GameDataResponseTest
(89 passed). Full `mvn -f backend/pom.xml verify`: 546 tests plus 20 packaged API
tests passed against disposable `ringlab_gadget_clarity_test`; existing JaCoCo
checks passed. HTML: `backend/target/site/jacoco/index.html`.

Frontend: `npm run test:coverage -- --maxWorkers=2` passed all 409 tests in 43
files (95.30% statements/lines, 92.12% branches, 90.06% functions); thresholds
were unchanged. `npm run build` passed TypeScript and Vite. `git diff --check`
passed. Earlier frontend runs exposed stale metadata mocks; those fixtures now
use the endpoint contracts. Responsive collection checks include a 390px viewport
with no horizontal overflow or badge collision, and a desktop screenshot in
`.tools/gadget-clarity/collection-desktop.jpg`.

Browser checks also reproduced all three scope/lock warnings using the existing
local demo account without publishing a build. All 117 editor icons loaded and
the selector cards had no internal overflow at desktop, tablet and 390px mobile.
A separate existing footer issue remains: its links extend roughly 21px beyond
a 768px viewport. That unrelated layout was not changed in this pass.

Importer validate succeeded. Disposable PLAN: 117 gadget updates, 184 changed
fields (117 kinds, 18 labels, 49 paths), zero inserts/deletes and no numerical/rule
changes. APPLY committed; the second PLAN was a no-op. The same inspected plan
was explicitly applied to local development.

Local startup verified PostgreSQL, backend, frontend and the frontend API proxy.
Healthy PostgreSQL/backend services and JWT keys were reused; Vite was refreshed.
Both `http://localhost:5173` and `https://dev.ringlabgarage.com` responded. Through
the frontend proxy, `/api/gadgets` returned 117 canonical paths and 18 festival
rewards. No development accounts, builds or database volumes were reset.

Generate the ignored self-contained sheet with `python scripts/gadget-review.py`.
Output: `.tools/gadget-clarity/review.html`, served at
`http://localhost:5177/review.html`. It contains all 117 cards with artwork, name,
cost, badges and source identifier. Each appears once: unsupported first, then
festival history, then passive/race-condition/utility. All applicable badges remain.
The artifact and logs are ignored; no generated screenshot is committed.

Production requires later explicit visual approval and deployment. Deploying
this revision runs V36 but still requires the separately reviewed importer
PLAN/APPLY for acquisition/artwork presentation content.

## Uniform artwork pass

On 2026-10-09, compared Starting Boost Bounty, Air Trick Bounty, 200 Ring Limit,
Speed Tuner 1, Power Machine Kit, Power Character Kit, Perfect Charge Kit and
4th Stage Charge Kit across existing assets, Sonic Wiki, the Japanese wiki
where indexed, and SRCW Gadget Builder. The Japanese images include pale
backgrounds and slot-count tabs. Sonic Wiki and the builder provide isolated
transparent icons. Sonic Wiki was selected to retain the established project
source policy and avoid unnecessary dependence on another asset host.

The live Sonic Wiki gadget table supplied **117/117 exact image matches**,
including all newer festival kits. Its name `Inventory Plus` maps to RingLab's
existing `Item Stock Plus` identity; no catalog names or effects were changed.
All 117 retrieved originals decode as **576 × 512 RGBA WebP**, with actual
transparent pixels. Fandom serves WebP bytes even for PNG-named source URLs.
The downloaded bytes are preserved without cropping, resizing, background
removal or generated artwork; the shared canvas and existing `object-fit: contain`
give consistent display framing.

**106 local WebP assets were added and 11 byte-identical existing assets reused.**
The canonical CSV changes only 106 `image_path` values. No exceptions or missing
icons remain. The inventory records the exact source URL, source name, retrieval
date, checksum and previous path for each gadget. Older PNG/JPEG files remain
available for databases and saved responses still using the previous paths;
they are not used by the newly imported canonical catalog.

The offline artwork guard checks all catalog-to-file identities and hashes,
distinct paths/bytes, source attribution, and the reviewed transparent WebP
canvas. The local review sheet is regenerated with all 117 icons. The ignored
eight-gadget comparison is `.tools/gadget-clarity/source-comparison.html`.

This remains presentation-only. Database consumers need an explicit importer
PLAN/APPLY for the new paths; restarting alone does not update them. Production
deployment and its importer APPLY remain pending visual approval.

Verification for this pass: all 117 source images decoded with real transparency;
the browser loaded all 117 review images at the expected dimensions. The frontend
build and `git diff --check` passed. Local importer validation passed; PLAN/APPLY
changed exactly 106 image paths with no inserts/deletes, and a second PLAN was a
no-op. The frontend and proxied gadget API responded successfully, and all 117
served asset checksums matched the ledger.

The targeted `GadgetArtworkInventoryTest` invocation was blocked before tests ran:
the offline `.tools/m2` cache lacks `maven-resources-plugin:3.4.0` selected by the
installed Maven. No dependency downloads, retries or broader backend tests were
attempted. Run that class in IntelliJ, or from the repository root run
`mvn -f backend/pom.xml -Dtest=GadgetArtworkInventoryTest test` with the normal
configured Maven cache. Full backend/frontend test suites were not rerun for
this artwork-only pass.
