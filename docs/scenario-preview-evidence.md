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
accessible through the web reader during the initial audit; its text export was
retrieved in the follow-up below. Nothing here is based on invented game tests.

## Complete CONDITIONAL-effect audit

IDs shown as numbers use `70000000-0000-4000-8000-` plus the twelve-digit number.
Other IDs are complete. Multiple rows explicitly cover separate effects of a kit.
"Unsupported utility" means its activation is not modeled, **not** zero benefit.
No unlisted additive stat pair is authorized by this audit. The explicitly
user-approved Quick Starter + Sea Dog assumption below is separate from evidence.

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
verified. Except for the user-approved Quick Starter + Sea Dog assumption below,
concurrent nonzero stat modifiers therefore leave the scenario partial;
the passive result stays intact and the uncertain scenario contribution is excluded.
Mutually exclusive lap/form conditions and descriptive non-stat effects can coexist.
Unknown numeric effects also prevent an exact total. Unsupported utilities are
reported separately without pretending they change the five numbers.

All numerical adjustments are raw `BigDecimal` points. No 100-point total clamp,
effective cap, rounding model, lap-time prediction or time-based ring drain is
inferred. Resetting preview discards context; no context crosses races or is saved.

## Quick Starter + Sea Dog follow-up (2026-10-01)

Reproduced against the existing local DEV build with Amy Rose, stock Speedster
Lightning parts, Quick Starter (45), Sea Dog Kit (15), and Ver. 1.4.1. The two
gadgets cost four slots and fit the plate. No saved build was changed.

The rejection is in `ScenarioStatsCalculator`: more than one active numerical
effect excludes all active scenario adjustments. Its separate passive gate also
excludes them when any nonzero passive adjustment is applied, or any passive
effect is unsupported. These are conservative coverage gates, not evidence of
in-game incompatibility. All currently modeled scenario vectors affect all five
stats, so each overlaps every nonzero passive vector. Separating the layers alone
does not establish additive behavior for those overlaps.

Evidence checked again:

- SEGA's 1.4.1 notes establish the individual Starter and Water bonuses, without
  specifying their simultaneous interaction.
- The live reference builder still serves `3fo_6rojgq9h9.js`; its contents matched
  the audited local copy. Its stat calculator selects `none/not/equal` rows and
  does not evaluate the lap/form rows. Selecting both gadgets there cannot prove
  a Lap 1 + WATER calculation.
- The original sheet's linked [research document text export](https://docs.google.com/document/d/1fz6H6dh0r_L2_qhct6WWXpnxae-QoTvANnfJ9N200Uc/export?format=txt)
  was accessible this time. It discusses other physical-speed/utility stacks,
  but supplies no Quick Starter + Sea Dog stat calculation or numerical
  passive/scenario compatibility evidence. Those unrelated stacks cannot
  authorize this pair.

At the end of this evidence-only investigation, no pair permission was added.
In particular, +40 to each stat remained a
**conditional arithmetic expectation, not a verified combination**. No numerical
passive/scenario pair was newly authorized either. Effective caps and rounding
remain unknown. A reference calculation that actually evaluates both conditions,
or direct documented game evidence, is still needed before enabling the pair.

Read-only local API results, in Speed / Acceleration / Handling / Power / Boost order:

| Context | Result | Effect statuses |
| --- | --- | --- |
| Lap 2, NORMAL | 65 / 30 / 59 / 52 / 34 | Both inactive |
| Lap 1, NORMAL | 85 / 50 / 79 / 72 / 54 | Quick Starter applied; Sea Dog inactive |
| Lap 2, WATER | 85 / 50 / 79 / 72 / 54 | Sea Dog applied; Quick Starter inactive |
| Lap 1, WATER | PARTIAL; subtotal 65 / 30 / 59 / 52 / 34; no exact total | Both unsupported, adjustments null |
| Return to Lap 2, NORMAL | 65 / 30 / 59 / 52 / 34 | Both inactive |

The browser also reproduced all four states. Clicking **Reset to Passive only**
hid the scenario result and returned to the original passive values above. A
read-only API request with reversed gadget order returned the same PARTIAL result,
both adjustments null. No scenario settings or build changes were saved.

The existing regression covers both gadget orders, individual effects, unresolved
scenario pairs, and an unresolved passive/scenario overlap. Compatible numerical-pair
success assertions would invent the evidence this investigation could not obtain.
The targeted command `mvn -f backend/pom.xml -o -Dtest=ScenarioStatsCalculatorTest test`
stopped before running tests: the local cache lacked
`org.apache.maven.shared:maven-filtering:3.3.1`. No dependency download retries,
broader suites, or service restarts were attempted. Run
`mvn -f backend/pom.xml -Dtest=ScenarioStatsCalculatorTest test` manually when the
required Maven artifacts are available. Documentation whitespace checks passed.

## User-approved additive assumption (2026-10-01)

After the investigation, the project owner explicitly requested assuming that
Quick Starter and Sea Dog work together, giving +40 to each stat when both are
active. Ruleset `crossworlds-1.4.1-scenario-2026-10-01.1` implements that modeling
decision. **This is not newly verified game behavior.** The sources above still
support the individual values only; effective caps, rounding and performance
remain unverified.

Permission is symmetric and limited to Quick Starter (45), effect `other-0`, and
Sea Dog Kit (15), effect `other-0`, on the already supported 1.4.1 rules. The
calculator checks every active numerical pair rather than rejecting solely by
active effect count. The permission does not extend to Super Starters, other
form kits, a third overlapping numerical effect, or passive/scenario overlaps.
Existing passive adjustments and unresolved-effect safeguards remain unchanged.
When both bonuses apply, their existing effects-details explanations identify
the additive assumption. Their individual +20 vectors are summed without a
100-point clamp, and both are individually marked `ACTIVE_AND_APPLIED`.

For the DEV loadout above, neither active remains 65 / 30 / 59 / 52 / 34; either
alone remains 85 / 50 / 79 / 72 / 54; both active now calculate
**105 / 70 / 99 / 92 / 74**. Reset still discards the scenario context.
No fixtures, saved builds, ownership, recommendation logic or schema were changed.

Regression coverage now asserts all four contexts, return to inactive conditions,
both saved orders, exact combined vectors, assumption explanations, stable effect
identity, unrelated unresolved pairs, and no transitive permission to a third
scenario effect or passive modifier. The previously reported Maven cache blocker
still prevents claiming a successful unit-test run.

Post-change verification: the running DEV API returned the new ruleset and exact
five-stat vectors for neither active, each alone, both together in either order,
and returning to inactive facts. Unrelated Quick Starter + Ace Pilot and the
Quick Starter + Sea Dog + Acceleration Tuner 2 passive overlap remained PARTIAL.
The browser showed both effects applied with +40 on every stat, the assumption
inside effects details, and Reset returning to Passive only. Whitespace checks
passed. No full-suite or frontend build was run for this domain-only change.
The existing saved demo description still refers to the old partial result;
it was deliberately left untouched under the no-fixture-changes constraint.
