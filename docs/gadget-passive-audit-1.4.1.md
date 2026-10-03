# Gadget passive-stat audit — 1.4.1

Reviewed 2026-09-28 against all **79 gadgets currently in RingLab's catalog**.
This checks the passive baseline used by Strict and Balanced, not race simulation.
There are **23 passive modifiers**, **30 conditional-only gadgets**, and **26 utilities**.
Every current catalog identity has a rule. No gadget is classified by parsing its name
or description at runtime; unknown future identities remain explicitly unreviewed.

## Sources and interpretation

- [SEGA 1.4.1 patch notes](https://asia.sega.com/SonicRacingCrossWorlds/en/update/v-1-4-1.html)
  confirm the changed tuner, machine-kit, lap, transformation and Panel Combo values.
- [Original Gadget List, current 1.4 table](https://docs.google.com/spreadsheets/u/0/d/1_B2mHZUP6J6jeZfcwbM2HkLvjkwMYuACE4l6Ktt-UkI/htmlview/sheet?headers=true&gid=0)
  supplies per-gadget effects. Catalog aliases include Item Stock Plus / Inventory Plus,
  Boost Item Chance UP / Speed Item Chance UP, and Item Hit Evolution / Item Evolution.
- The [Character Kit review](https://w.atwiki.jp/sonicracingcw/pages/56.html)
  corroborates character-type bonuses, penalties and bundled capabilities.
- Numeric provenance and stacking limits remain in [the source ledger](passive-gadget-sources.md).

**Conditional** means +0 to this passive baseline: no lap, ring count, terrain,
transformation, hit or action is supplied. It does not mean the gadget has no benefit
in a race. **Utility** also means +0; item behavior, charge timing, recovery and
invincibility are not five-stat points. Existing passive penalties are never erased.

## All catalog gadgets

All omitted stats are unchanged. Machine/Character Kits require the named type on
the machine/racer respectively; a non-match contributes zero. Tuners use the named
machine type: every non-match gives +8 to the named stat with no penalty.

| Gadget | Passive contribution / classification |
| --- | --- |
| 130 Ring Limit | +0 — utility |
| Acceleration Character Kit | Matching racer: Acceleration +7, Boost −5 |
| Acceleration Machine Kit | Matching machine: Acceleration +20 |
| Acceleration Tuner 1 | Matching machine: Acceleration +20, Handling −4 |
| Acceleration Tuner 2 | Matching machine: Acceleration +20, Handling −2, Boost −2 |
| Ace Pilot Kit | +0 — conditional |
| All-Rounder Kit | +0 — conditional |
| Attack Item Chance UP | +0 — utility |
| Boost Character Kit | Matching racer: Boost +7, Power −5 |
| Boost Item Chance UP | +0 — utility |
| Boost Machine Kit | Matching machine: Boost +20 |
| Boost Tuner 1 | Matching machine: Boost +20, Speed −4 |
| Boost Tuner 2 | Matching machine: Boost +20, Speed −2, Power −2 |
| Champion Bounty | +0 — conditional |
| Collision Boost | +0 — conditional |
| Collision Evolution | +0 — conditional |
| Comeback Kit | +0 — utility |
| Crash Pads | +0 — utility |
| Damage Evolution | +0 — conditional |
| Damage Mercy | +0 — conditional |
| Defense Item Chance UP | +0 — utility |
| Double Down | All five stats −10 |
| Drift Charge Kit | Handling +3 |
| Drift Spinner Kit | +0 — utility |
| Extended Slipstream | +0 — utility |
| Friction Drift | +0 — utility |
| Giant Rocket Punch | +0 — utility |
| Go Go Omochao | +0 — utility |
| Handling Character Kit | Matching racer: Handling +7, Speed −5 |
| Handling Machine Kit | Matching machine: Handling +20 |
| Handling Tuner 1 | Matching machine: Handling +20, Power −4 |
| Handling Tuner 2 | Matching machine: Handling +20, Speed −2, Power −2 |
| Hazard Item Chance UP | +0 — utility |
| Hyper Ring Engine | +0 — conditional |
| Inventory Swap | +0 — utility |
| Invincible Finish | +0 — conditional |
| Invincible Start | +0 — conditional |
| Item Buster Kit | +0 — utility |
| Item Hit Evolution | +0 — conditional |
| Item Keeper | +0 — utility |
| Item Mercy | +0 — conditional |
| Item Stock Plus | +0 — utility |
| Less is More | +0 — conditional |
| Lucky Pair | +0 — utility |
| Mini Ring Thief | +0 — conditional |
| Panel Combo Kit | Handling +8, Power +8 |
| Perfect Charge Boost | +0 — conditional |
| Perfect Landing | +0 — conditional |
| Power Character Kit | Matching racer: Power +7, Handling −5 |
| Power Machine Kit | Matching machine: Power +20 |
| Power Tuner 1 | Matching machine: Power +20, Acceleration −4 |
| Power Tuner 2 | Matching machine: Power +20, Acceleration −2, Handling −2 |
| Quick Recovery | +0 — utility |
| Quick Starter | +0 — conditional |
| Ring Doubler | +0 — utility |
| Ring Engine | +0 — conditional |
| Ring Evolution | +0 — conditional |
| Ring Mercy | +0 — conditional |
| Ring Thief | +0 — conditional |
| Route Planner Bounty | +0 — conditional |
| Sea Dog Kit | +0 — conditional |
| Slow Starter | +0 — conditional |
| Speed Character Kit | Matching racer: Speed +7, Acceleration −5 |
| Speed Machine Kit | Matching machine: Speed +20 |
| Speed Tuner 1 | Matching machine: Speed +20, Acceleration −4 |
| Speed Tuner 2 | Matching machine: Speed +20, Acceleration −2, Boost −2 |
| Spin Dash Kit | +0 — utility |
| Spin Drift | +0 — utility |
| Starting Boost Bounty | +0 — conditional |
| Strong Finish | +0 — conditional |
| Substitute Item | +0 — conditional |
| Summon Item Box | +0 — utility |
| Super Quick Starter | +0 — conditional |
| Super Slow Starter | +0 — conditional |
| Ultimate Air Trick | +0 — conditional |
| Ultimate Charge | +0 — utility |
| Warp Ring Specialist | +0 — utility |
| Wisp Chance UP | +0 — utility |
| Wisp Hoarder Kit | +0 — utility |

## Corrections and limits

The original fix adds the 42 missing rule entries and stops Handling Character Kit's
uncertain ring-theft variant from blocking its known passive adjustment. This full
audit also corrects Acceleration Character Kit's ring-collection boost explanation
and Boost Character Kit's air-trick ring reward explanation. Ring Engine explanations
now explicitly acknowledge conditional stat effects as well as physical speed.

`PassiveStatsCalculatorTest` covers all 79 individual gadgets across all 25 racer/machine
type pairs (1,975 cases within the unit tests). It also combines each of the 56 zero
contributors with Boost Tuner 1 to protect both matching penalties and non-matching
bonuses. The original Charmy Bee / Ancient Throne regression remains in the calculator,
Balanced solver and dialog tests. The additive-v1 policy now covers the closed set
of 23 numerical passive effects; every plate-legal subset and all 25 known type pairs
are checked against independent canonical-vector sums. This establishes implementation
consistency with the community model, not official verification of in-game stacking.

Separate catalog discrepancy: Substitute Item costs **1** in RingLab but **2** in the
reviewed Gadget List. No slot costs, migrations or saved-build legality are changed
by this passive-stat correction; resolving that discrepancy needs a separate catalog
change. This audit covers RingLab's 79 entries, not every gadget listed in the game.

## Verification

`mvn -o "-Dtest=PassiveStatsCalculatorTest,BalancedSolverTest" test` passed from
`backend`: 22 pure unit tests, zero failures/errors. The normal Maven cache resolved
the earlier alternate-cache blocker without downloads. Comparing this audit's names
against the public catalog found 79 unique entries and no missing/extra names.
Existing test-compilation warnings concern deprecated and unchecked APIs in unrelated
test classes. No integration/full-suite tests or coverage analysis were run.
