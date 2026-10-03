# Phase 5B: reviewed catalog corrections

Evidence cutoff: **2026-10-03**. Phase 5A was audit-only; this is the approved
implementation. Canonical CSVs remain the maintenance source; PostgreSQL is the
runtime source. No scraping or network dependency is added to the application.

## Claim-specific evidence

- **VERIFIED_OFFICIAL**: [SEGA's Amigo release notice](https://sonicracing.sega.com/en/details/000118wbfzajpy.html)
  establishes Amigo, the Classic skin and Locomotive de Amigo as released on
  2026-09-09. Classic is not a separate racer in RingLab.
- **SUPPORTED_BY_COMMUNITY_SOURCE**: Amigo's HANDLING type and five-stat vector
  **(6, 15, 17, 9, 13)**, in Speed/Acceleration/Handling/Power/Boost order, come
  from the [community builder](https://www.srcgadgetbuilder.com/build) and its
  [linked racer workbook](https://docs.google.com/spreadsheets/d/1iNFHfwyTBuC_bnuyBn_VQkoduUmhr4Tu/edit?gid=816749913).
  They are not official numerical verification; the builder and workbook share lineage.
  Only the supported 1.4.1 snapshot receives this row.
- **SUPPORTED_BY_COMMUNITY_SOURCE**: Substitute Item costs **2**, supported by
  the builder, [Yoshister Gadget List](https://docs.google.com/spreadsheets/u/0/d/1_B2mHZUP6J6jeZfcwbM2HkLvjkwMYuACE4l6Ktt-UkI/htmlview/sheet?headers=true&gid=0)
  and [Japanese support-gadget page](https://w.atwiki.jp/sonicracingcw/pages/46.html).
  SEGA's patch notes establish the gadget, not its exact cost.
- **SUPPORTED_BY_COMMUNITY_SOURCE**: Locomotive's POWER type is shared by the
  builder/workbook and [Japanese machine table](https://w.atwiki.jp/sonicracingcw/pages/27.html).
  **CONFLICTING**: workbook parts (13,10,7,16,13) imply stock
  (39,30,21,48,39); the Japanese stock table reports (39,30,21,48,42).
  All three part identities exist, but there are **no stat rows in any patch**.
  A missing row propagates unknown through the existing calculators; it is never zero.
- **VERIFIED_OFFICIAL**: the [1.2.2 notice](https://asia.sega.com/SonicRacingCrossWorlds/en/update/v-1-2-2.html)
  is dated **2025-12-18**. This corrects the previous 2025-12-22 metadata using
  the notice-date convention; it changes no numerical snapshot.
- **SUPPORTED_BY_COMMUNITY_SOURCE**: [Japanese item-control documentation](https://w.atwiki.jp/sonicracingcw/pages/47.html)
  additionally corroborates Double Down's **−10 to every stat**. Its existing
  numerical rule is unchanged. This does not verify all additive interactions.
- **REPOSITORY_LEGACY**: the 1.2.0, 1.2.2 and 1.3.1 numerical snapshots were copied
  by V22/V24, not independently reconstructed historical measurements.
  **UNKNOWN** includes new identities absent from those snapshots.

A source attached to an effect does not officially verify every catalog field.
The descriptions below summarize reviewed behavior; they are not physical-speed,
charge-time or utility conversions into five-stat points.

## All 38 additions

Every identity/cost is **SUPPORTED_BY_COMMUNITY_SOURCE**, from the approved Phase 5A
ledger, builder and Yoshister list. The linked Japanese category provides further
effect context. Stable UUIDs use prefix `84000000-0000-4000-8000-`, suffixes
000000000001 through 000000000038 in the order below.

| Gadget | Cost | Runtime kind | Reviewed interpretation / limit |
|---|---:|---|---|
| Air Trick Bounty | 1 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/41.html): Awards rings when performing air tricks. |
| Item Attack Bounty | 1 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/41.html): Awards rings after an item hits an opponent. |
| Runoff Bounty | 1 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/41.html): Awards rings while crossing runoff areas with a speed item. |
| Slipstream Bounty | 1 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/41.html): Awards rings during slipstreams. |
| Travel Ring Bounty | 1 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/41.html): Awards rings when passing through a Travel Ring. |
| Morph Action Bounty | 1 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/41.html): Awards rings for charge actions in water or flight. |
| Perfect Charge Bounty | 2 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/41.html): Awards rings for releasing a drift as its charge level fills. |
| Dash Panel Bounty | 2 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/41.html): Awards rings when using dash panels or gates. |
| Dash Panel Mini Bounty | 1 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/41.html): Awards a smaller ring reward when using dash panels or gates. |
| Dash Panel Combo Bounty | 2 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/41.html): Awards rings for consecutive dash panels. |
| 200 Ring Limit | 2 | NON_STAT | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/42.html): Raises ring capacity to 200. |
| Ring Gain Mini Boost | 1 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/42.html): Briefly increases physical speed when collecting rings. |
| Ring Gain Boost | 2 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/42.html): Grants a larger brief physical speed increase when collecting rings. |
| Ring Range UP | 1 | NON_STAT | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/42.html): Extends the ring pickup range. |
| Air Drift Mobility UP | 1 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/43.html): Changes air drift movement and release behavior in flight form. |
| Charge Jump Mobility UP | 1 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/43.html): Increases lateral air trick movement after a charge jump in water form. |
| Air Trick Adept | 1 | NON_STAT | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/43.html): Increases air trick animation speed. |
| Air Trick Expert | 2 | NON_STAT | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/43.html): Increases air trick animation speed more than Air Trick Adept. |
| Lv1 Quick Charge | 2 | NON_STAT | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/44.html): Shortens level 1 drift charge time. |
| Lv2 Quick Charge | 1 | NON_STAT | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/44.html): Shortens level 2 drift charge time. |
| Lv3 Quick Charge | 1 | NON_STAT | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/44.html): Shortens level 3 drift charge time. |
| Technical Drift | 2 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/44.html): Trades drift traction for faster charge in land form. |
| Counter Quick Charge | 3 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/44.html): Accelerates charging when changing drift direction in land form. |
| Maximum Traction | 1 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/46.html): Reduces slipping on sand and ice. |
| Second Wind | 1 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/46.html): Grants a Boost item after falling off the course. |
| Bumper Guard | 1 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/46.html): Prevents ring loss from wall contact. |
| Boost Starter | 1 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/48.html): Grants a Boost item at race start. |
| Double Boost Specialist | 2 | NON_STAT | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/48.html): Adds Double Boost to item boxes and grants one at race start. |
| Dark Chao Starter | 1 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/48.html): Grants a Dark Chao item at race start. |
| Giant Spiked Iron Ball | 1 | NON_STAT | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/48.html): Increases Spiked Iron Ball size and item availability. |
| Short Fuse | 1 | NON_STAT | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/48.html): Speeds up Bomb charging and increases its item availability. |
| Monster Truck Starter | 3 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/48.html): Grants a Monster Truck item at race start. |
| Item Hoarder Kit | 3 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/49.html): Combines extra item capacity with item rewards at the start of later laps. |
| Damage Support Kit | 3 | CONDITIONAL | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/49.html): Combines starting invincibility with a Boost item after falling off the course. |
| Perfect Charge Kit | 3 | UNSUPPORTED | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/88.html): Released festival kit. Complete effect support is not yet reviewed. |
| Speedy Shortcut Kit | 3 | UNSUPPORTED | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/88.html): Released festival kit. Complete effect support is not yet reviewed. |
| Air Trick Action Kit | 3 | UNSUPPORTED | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/88.html): Released festival kit. Complete effect support is not yet reviewed. |
| 4th Stage Charge Kit | 3 | UNSUPPORTED | [Evidence](https://w.atwiki.jp/sonicracingcw/pages/88.html): Released festival kit. Complete effect support is not yet reviewed. |

There are **34 ordinary-unlock identities and four released festival kits**.
Festival acquisition can be time-limited; the items are not future content.
The [advanced-kit list](https://w.atwiki.jp/sonicracingcw/pages/88.html) records the
TMNT, Axel, Amigo and second Miku festivals. Identity/cost approval does not
establish full effect support: the four kits stay UNSUPPORTED conservatively.

Classification totals: **24 CONDITIONAL, 10 NON_STAT, 4 UNSUPPORTED,
0 new PASSIVE**. CONDITIONAL corresponds to conditional/scenario behavior;
NON_STAT corresponds to non-stat utility; UNSUPPORTED means incomplete review.
The zero vectors required by the rule storage format are placeholders for these
kinds, not numerical evidence. All new conditional rows set
`scenario_stat_potential=false`: their reviewed behavior is utility, timing or
physical motion, not a modeled five-stat adjustment. No new scenario calculator
rule is introduced. No missing `stat_table` is treated as proof of zero.

The closed **23-effect additive passive policy** and all prior individual effect
rows remain unchanged. Ruleset metadata advances to
`crossworlds-1.4.1-passive-2026-10-03.1`; the separate additive-v1 policy ID stays
unchanged. The frontend cache key follows the fact revision.

## Resulting catalog and support

| Data | Current count |
|---|---:|
| Racers | 53 |
| Machines | 63 |
| Machine parts | 177 (63 FRONT, 63 REAR, 51 TIRE) |
| Gadgets | 117 |
| Maps | 44 |
| Versions | 4 |
| Racer stats in 1.4.1 | 53 |
| Racer stats in each older snapshot | 52 |
| Part stats in each snapshot | 174 |
| Racer stat rows across snapshots | 209 |
| Part stat rows across snapshots | 696 |
| Passive fact rows (all kinds) | 137 |
| Scenario fact rows | 10 |
| Source rows | 388 |

Catalog + stat + rule records total **1,899**. No new skins, dummy/Unknown builder
records, Avatar, Bayonetta, Godzilla or Evangelion content are added. No artwork is
downloaded: existing initials/fallback rendering covers the new identities.
Editor feedback identifies unsupported effects even when their effect ID is not
`stats`; it no longer describes every missing effect as unresolved stacking.

New identities inherit the existing exclusion model: absence of an exclusion means
owned, including festival items; excluding a machine covers its parts.
Amigo participates in either recommendation mode on 1.4.1. Locomotive cannot compete
without known part totals, and a required unknown part yields UNAVAILABLE.
Utility and conditional gadgets add no objective value. Unsupported kits make
kept/locked evaluations unavailable; they cannot quietly enter optimization as zero.
The passive display may retain a known subtotal with PARTIAL coverage, while the
recommendation solver rejects that incomplete evaluation.

Strict, Balanced, budgets, ordering, gadget scopes and scenario assumptions do not
change. KEEP_CURRENT preserves identities and validates current costs;
OPTIMIZE_UNLOCKED can replace unlocked gadgets, preserving legal locks.

## Migration and importer safety

**V35__reviewed_catalog_corrections.sql** is a deliberate, bounded correction to
published facts. Its exact effects are:

1. Update Substitute Item cost 1 → 2.
2. Correct the 1.2.2 release date.
3. Insert one racer, one machine, three parts, 38 gadgets and one 1.4.1 racer-stat row.
4. Insert 38 classifications and 114 source references, and revise one passive
   ruleset label.

No schema change, deletion, historical stat edit, ownership rewrite or user-build
rewrite occurs. V1–V34 stay unchanged. This exceptional published-data correction
uses a migration because the ordinary importer correctly rejects changes to
published costs, dates, stat membership and rules. General import permissions are
not widened to make a one-time correction. Future additions/new snapshots continue
through CSV → validate → plan → approved apply → PostgreSQL.

Before V35, planning these CSVs against V34 must report unsafe published changes
and write nothing. After V35, the canonical plan must have **zero inserts, zero
updates, zero deletes**; an identical apply is a no-op. Startup runs Flyway as
already configured; it does not read or auto-import CSVs. New snapshot coverage
continues to require an explicit row per identity; unknown facts must use blank
cells there. Existing published snapshots may legitimately omit new identities.

A corrected cost can invalidate old plates, including three two-slot gadgets
whose sum is six but which cannot fit two rows of three. Stored gadget identities
and ordering remain intact. Reading a legacy build remains possible; passive stats
mark invalid loadouts, saving again requires fixing the plate, and KEEP_CURRENT
explains that the current gadgets no longer fit.

**Deployment/recovery implications:** V35 runs on backend upgrade in the normal
Flyway transaction. Take the documented database backup before any authorized
production upgrade. An application-image rollback does not reverse V35 or restore
cost 1. Do not delete the new identities to undo the change: builds may reference
them. Use a reviewed forward correction, or coordinate a full backup restore with
the downtime/data-loss implications in the production runbook. This task does not
authorize production deployment.

## Unchanged disputed data

All existing machine-part numerical CSVs are untouched, including Dragon Brave
(all parts), Jaws Rocket (FRONT/REAR), Sakura Board (FRONT/REAR), TYPE-W Windy
(REAR), Triple Fan (FRONT/REAR) and Pizzafire Van. Locomotive has no invented Boost
allocation. Stock totals are not divided to manufacture per-part values.

## Verification contract

- `Phase5bCatalogTest`: exact identities/costs/classifications, patch membership,
  both recommendation modes, corrected plate legality, ownership and unavailable facts.
- `BuildServiceTest`: legacy build remains readable; invalid resave/create fails;
  explicit player repair succeeds.
- `GameDataImportIntegrationTest`: V34 → V35 rehearsal with a saved legacy plate,
  migrated database/canonical parity, no-op plan/apply and existing importer guards.
- Existing frozen 79-gadget characterization still checks all prior effect facts and
  calculations; only its expected fact-revision label is allowed to advance.
- Frontend tests cover fallback artwork and unknown-stat presentation alongside
  the full existing editor/collection/recommendation suite.

Run `mvn -f backend/pom.xml verify` using a disposable test database and JWT keys
as described in [validation](validation.md); use `npm run test:coverage` and
`npm run build` in frontend, then the [offline/import plan commands](../game-data/README.md)
and `git diff --check`.
