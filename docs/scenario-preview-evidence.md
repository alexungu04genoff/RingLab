# Scenario Preview v1 — evidence audit

Reviewed 2026-09-30, **1.4.1 only**, before adding numerical rules. This is
stat-point previewing, not a race simulation or a claim about effective performance.

## Sources actually inspected

- **S**: [SEGA 1.4.1](https://asia.sega.com/SonicRacingCrossWorlds/en/update/v-1-4-1.html):
  changed lap/form bonuses; Ring Engine combination fixes and mode exceptions.
- **Y**: [Yoshister's original current 1.4 sheet](https://docs.google.com/spreadsheets/u/0/d/1_B2mHZUP6J6jeZfcwbM2HkLvjkwMYuACE4l6Ktt-UkI/htmlview/sheet?headers=true&gid=0):
  read actual table cells, including individual triggers and bundled effects.
- **B**: [SRCW Gadget Builder](https://www.srcgadgetbuilder.com/build): public
  `252-neok-oop8.js` gadget records and `3fo_6rojgq9h9.js` calculator, loaded and
  inspected. Module 92265 only evaluates `none/not/equal` rows. It ignores `LAP1`,
  `LAP3`, `mode`, `burning`, and `evolution`. Its conditional data corroborates
  individual values, **not** conditional calculation, caps, or additive pairs.
  B credits Y; these are one lineage, not independent experiments.
- **J**: Japanese wiki [parameter gadgets](https://w.atwiki.jp/sonicracingcw/pages/45.html)
  and [advanced kits](https://w.atwiki.jp/sonicracingcw/pages/88.html), used as
  corroboration/limitations. Evolution's “maximum 100” does not unambiguously
  distinguish accumulated bonus from effective total or establish reset semantics.
- **L**: existing [passive source ledger](passive-gadget-sources.md) and
  [catalog audit](gadget-passive-audit-1.4.1.md).

No game execution was available. The optional linked Yoshister document was not
accessible through the web reader. Nothing here is based on invented game tests.

## Complete CONDITIONAL-effect audit

IDs shown as numbers use `70000000-0000-4000-8000-` plus the twelve-digit number.
Other IDs are complete. Multiple rows explicitly cover separate effects of a kit.
“Unsupported utility” means its activation is not modeled, **not** zero benefit.
No unlisted additive stat pair is authorized by this audit.

| Gadget / identity | Condition | Numeric effect | Units | Cap/stack | Evidence | Status |
| --- | --- | --- | --- | --- | --- | --- |
| Quick Starter / 45 | Lap 1 | +20 each | stat points | Fixed, removed outside lap; concurrent numeric stack unverified | S,Y,B,J | Supported alone |
| Super Quick Starter / 47 | Lap 1 | +60 each | stat points | Fixed, not accumulated; overlapping starter pair unverified | S,Y,B,J | Supported alone |
| Slow Starter / 46 | Lap 3 | +20 each | stat points | Fixed, removed outside lap | S,Y,B,J | Supported alone |
| Super Slow Starter / 48 | Lap 3 | +60 each | stat points | Fixed, not accumulated | S,Y,B,J | Supported alone |
| Sea Dog Kit / 15 | WATER now | +20 each | stat points | Fixed, removed outside form | S,Y,B,J | Supported alone |
| Ace Pilot Kit / 17, transformation | FLIGHT now | +20 each | stat points | Fixed, removed outside form | S,Y,B,J | Supported alone |
| Ace Pilot Kit / 17, panel/gate reward | Cross qualifying panel/gate | Ring reward | rings | Panel identity required; not a stat modifier | Y,B,L | Unsupported utility |
| All-Rounder Kit / 18, transformation | WATER or FLIGHT now | +20 each | stat points | Fixed; other transformation kit overlap unverified | Y,B,J | Supported alone |
| All-Rounder Kit / 18, terrain | Offroad/traction state | Physical speed/traction | speed, handling behavior | No conversion to points; duration/mode context unmodeled | Y,B,L | Unsupported utility |
| Ring Engine / 5a000a58-7d7a-581c-80e3-6ae8661215b7 | At least one ring held now | +12 each | stat points | Fixed, independent of positive count; ceases at zero; dual engine unverified | Y,B,S | Supported alone |
| Ring Engine, physical speed/drain | Same held-ring condition | Separate speed increase and drain | km/h or mph in differing sources; rings/sec | No time simulation or conversion | Y,B,S | Descriptive only |
| Hyper Ring Engine / 182bdfa8-44d3-5de8-941b-d525381dd0a3 | Rings held now | Ring-dependent stats and speed | stat points, physical speed | Cap interpretation and dual-engine/mode behavior incomplete | Y,B,S | Unsupported |
| Collision Evolution / 5 | Successful collisions since race start | +1 each per trigger | stat points | Complete cap/reset/shared accumulation not established | Y,B,J | Unsupported |
| Ring Evolution / 6 | Rings gained, not currently held | +1 each per ten gained | stat points | Starting/reward-ring counting, cap/reset incomplete | Y,B,J | Unsupported |
| Damage Evolution / 7 | Damage triggers | +7 each per trigger | stat points | Cap/reset and event qualification incomplete | Y,B,J | Unsupported |
| Item Hit Evolution / 8 | Successful item hits | +7 each per trigger | stat points | Multi-target hit counting and cap/reset incomplete | Y,B,J | Unsupported |
| Perfect Landing / 2 | Successful landing boost active now | Landing boost | physical speed, time | No stacking or strength modeled | Y,B | Active non-stat description |
| Invincible Finish / 4 | Within 300m of finish now | Invincibility | distance/utility | No points; not lap inference | Y,B | Active non-stat description |
| Ultimate Air Trick / 1 | Three airborne tricks then landing | Invincibility/landing boost | events/time | Timing window unmodeled | Y,B,L | Unsupported utility |
| Substitute Item / 3 | Homing hit with held item | Protection consuming item | items | Inventory/attack state unmodeled | Y,B,L | Unsupported utility |
| Starting Boost Bounty / 27 | Successful start boost | Ring reward | rings | No start-event input | Y,B,L | Unsupported utility |
| Collision Boost / 29 | Qualifying machine collision | Boost | physical speed/time | No points; event window unmodeled | Y,B,L | Unsupported utility |
| Ring Thief / 31 | Collision with racer | Stolen rings | rings/cooldown | No inventory or cooldown simulation | Y,B,L | Unsupported utility |
| Mini Ring Thief / 32 | Collision with racer | Stolen rings | rings/cooldown | No inventory or cooldown simulation | Y,B,L | Unsupported utility |
| Invincible Start / 36 | Race start window | Invincibility | time | Lap 1 alone does not establish active window | Y,B,L | Unsupported utility |
| Perfect Charge Boost / 43 | Correctly timed drift release | Boost duration | time | No points or timed event model | S,Y,B,L | Unsupported utility |
| Panel Combo Kit / 23 | Consecutive panels within window | Accumulating ring reward | rings/time | Not its separate passive H/P bonus | Y,B,L | Unsupported utility |
| Speed Machine Kit / 49 | Collect rings | Mini boost | physical speed/time | Separate passive Speed +20 unchanged | Y,B,L | Unsupported utility |
| Acceleration Machine Kit / 34 | Damage recovery | Recovery benefit | time | Separate passive Acceleration +20 unchanged | Y,B,L | Unsupported utility |
| Handling Machine Kit / 37 | Level 4 drift | Charge/invincibility | level/time | Separate passive Handling +20 unchanged | Y,B,L | Unsupported utility |
| Power Machine Kit / 38 | Collision | Boost | physical speed/time | Separate passive Power +20 unchanged | Y,B,L | Unsupported utility |
| Boost Machine Kit / 62 | Drift boost | Ring reward | rings | Separate passive Boost +20 unchanged | Y,B,L | Unsupported utility |
| Speed Character Kit / 20, start | Start window | Invincibility | time | Racer bonus/penalty remain passive | Y,B,L | Unsupported utility |
| Speed Character Kit / 20, start boost | Successful start boost | Ring reward | rings | No start-event input | Y,B,L | Unsupported utility |
| Acceleration Character Kit / 16, slipstream | Active slipstream | Ring reward | rings/time | Racer bonus/penalty remain passive | Y,B,L | Unsupported utility |
| Acceleration Character Kit / 16, ring boost | Collect rings | Boost | physical speed/time | No conversion to Acceleration | Y,B,L | Unsupported utility |
| Handling Character Kit / 13 | Collision | Ring theft | rings | Bundled variant disputed in ledger | Y,B,L | Unsupported utility |
| Power Character Kit / 12 | Collision | Boost | physical speed/time | Racer bonus/penalty remain passive | Y,B,L | Unsupported utility |
| Boost Character Kit / 11 | Air trick | Ring reward | rings | Racer bonus/penalty remain passive | Y,B,L | Unsupported utility |
| Champion Bounty / b3002fe1-32a2-5a06-8c07-64d0511811ad | Leading race position | Ring reward | rings/time | Position/time unmodeled | Y,B,L | Unsupported utility |
| Damage Mercy / 97147799-cf1d-5f50-b133-3ff6091574d3 | Recovery after hit | Invincibility | time | Damage count alone insufficient | Y,B,L | Unsupported utility |
| Item Mercy / f3b3dd31-9912-5a84-a8db-a76d1f93dcbe | Recovery after hit | Item reward | items | Damage count alone insufficient | Y,B,L | Unsupported utility |
| Less is More / 180a09fc-0034-5f56-be95-2938ce9ed61d | Low held rings | Top speed | physical speed | No stat-point conversion | Y,B,L | Unsupported utility |
| Ring Mercy / b5f42975-23b3-5a1d-9173-c18d2e584244 | Recovery after hit | Ring reward | rings | Damage count alone insufficient | Y,B,L | Unsupported utility |
| Route Planner Bounty / 175c2797-0be5-571a-99ac-af4bfdbf58a4 | Choose Travel Ring | Rings/capacity | rings | No map-to-event inference | Y,B,L | Unsupported utility |
| Strong Finish / 9735d3ab-d53b-52a1-a80d-ed0cb89429c2 | Start of lap 3 window | Invincibility | time | Lap 3 alone insufficient | Y,B,L | Unsupported utility |

## Supported scope and deliberate limits

Eight stat gadgets: four Starters, three transformation kits, regular Ring Engine.
No accumulation inputs are exposed: **no Evolution formula or arbitrary maximum is
implemented**. Consequently event-count/maximum-stack numerical regressions cannot
honestly be provided in v1; unsupported-effect regressions protect that boundary.
No held-versus-collected ambiguity: the sole ring input is explicitly rings held.

No additive overlapping conditional/conditional or conditional/passive pair was
verified. Concurrent nonzero stat modifiers therefore leave the scenario partial;
the passive result stays intact and the uncertain scenario contribution is excluded.
Mutually exclusive lap/form conditions and descriptive non-stat effects can coexist.
Unknown numeric effects also prevent an exact total. Unsupported utilities are
reported separately without pretending they change the five numbers.

All numerical adjustments are raw `BigDecimal` points. No 100-point total clamp,
effective cap, rounding model, lap-time prediction or time-based ring drain is
inferred. Resetting preview discards context; no context crosses races or is saved.
