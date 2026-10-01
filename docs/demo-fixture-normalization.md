# Controlled DEV fixture normalization — 2026-10-01

Ownership is proven by manifest key, ID, expected author and managed-content fingerprint.
Titles in this report describe presentation; they do not authorize writes.

## Single combined scenario loadout

Stable key `feature-quick-v1`, build ID `8e5d36a4-9556-4350-aa3d-e37f17b00470`:
Amy Rose (`0093f0e6-3a7c-57ad-af1a-6f7db022fa09`), Speedster Lightning Front
(`10000000-0000-4000-8000-000000000001`), Rear
(`20000000-0000-4000-8000-000000000001`) and Tires
(`30000000-0000-4000-8000-000000000001`), patch 1.4.1
(`50000000-0000-0000-0000-000000000001`). Ordered gadgets are Quick Starter
(`70000000-0000-4000-8000-000000000045`, 1 slot) and Sea Dog Kit
(`70000000-0000-4000-8000-000000000015`, 3 slots). The 4/6-slot plate fits
one full row plus one slot in the other; there are no filler gadgets or maps.

The former separate water demo keeps key `feature-water-v1` and ID
`039e0183-ab8a-44a6-aff8-78b72eac5a33`, repurposed as Invincible Finish
(`70000000-0000-4000-8000-000000000004`). No row is deleted or recreated.

Stats below use Speed / Acceleration / Handling / Power / Boost order.

| Preview state | Actual result | Effect status |
| --- | --- | --- |
| Passive only | 65 / 30 / 59 / 52 / 34 | Conditional bonuses not evaluated |
| Lap 1, Normal | 85 / 50 / 79 / 72 / 54 | Quick Starter applied; Sea Dog inactive |
| Lap 2, Water | 85 / 50 / 79 / 72 / 54 | Quick Starter inactive; Sea Dog applied |
| Lap 1, Water | Partial; known subtotal 65 / 30 / 59 / 52 / 34; no exact total | Both individual numerical effects remain unsupported together |
| Reset to Passive only | 65 / 30 / 59 / 52 / 34 | Scenario context discarded |

The individual rule vectors are independently read from the shared scenario
rule definitions and added to the returned passive result for verification. No combined
+40 assertion is made. [SEGA 1.4.1](https://asia.sega.com/SonicRacingCrossWorlds/en/update/v-1-4-1.html)
confirms the individual +20 Starter and Sea Dog adjustments, not their additive
interaction. The audited reference calculator only evaluates none/not/equal
rows, not LAP1 or mode rows. The [existing evidence audit](scenario-preview-evidence.md)
therefore does not establish this overlap. No scenario stacking restriction was
relaxed and no optimizer or formula changed.

Build requests store neither Lap nor vehicle form. Reset changes only the
ephemeral preview; no build update is sent by scenario evaluation.

## Naming policy and regeneration

The generator chooses teaching status from stable plan definitions, then proves
row ownership using the manifest before updating. The public application has no
fixture manifest or persisted fixture metadata. Its existing presentation-based
Top 3 exclusion policy now recognizes `[Demo · <Feature>]` while retaining old
prefix compatibility. No schema or file-system coupling was introduced. Public
ranking arithmetic and ordinary Explore sorting are unchanged. The three former
Top 3 teaching examples now belong to Ranking and are excluded from normal Top 3.

From the repository root:

```powershell
pwsh -NoProfile -File backend/scripts/SeedDemoData.ps1 -BaseUrl http://127.0.0.1:8080 -DatabaseName ringlab -ExpandedCommunity -BuildCount 240 -RandomSeed 20260927 -ReferenceTime '2026-09-27T01:00:00Z' -CurrentFeatures -NormalizeControlledFixtures -Apply
```

Omit `-Apply` for a read-only preview. The cleanup mode never creates missing
rows, changes ordinary titles or rewrites existing engagement.

The generator tests passed. Targeted Java tests
`CommunityEligibilityTest,ScenarioStatsCalculatorTest` could not run because the
offline `.tools/m2` cache lacks several current dependencies. No downloads or
broader test command were attempted. With the normal configured Maven repository,
run `mvn -f backend/pom.xml -Dtest=CommunityEligibilityTest,ScenarioStatsCalculatorTest test`,
or run those two classes directly in IntelliJ. No full suite or frontend build
was run for these fixture and backend policy changes.

## Complete rename ledger

The apply completed with 67 updates, zero additions and zero deletions. A
subsequent preview proposed zero changes. All 256 build IDs remained unchanged;
185 ordinary manifest titles and four non-manifest builds were untouched.
Snapshot comparison preserved all 1,745 votes, 562 comments, 360 map associations
and the one saved-build relationship. Racer/parts/patch/remix origin/author/
creation time remained unchanged. Only the two documented scenario descriptions
and ordered gadget lists changed; normal REST edits updated their timestamps.

Controlled totals: Ranking 22, Comments 6, Recommendation 2, Ownership 1,
Scenario 8, Stats 14, Maps 4, Machines 4, Remix 6: **67**. The 19 focused ranking
fixtures retained their votes. Comment totals remained 21/40/41/61/81/101 and
their first pages each returned 20 entries. Canonical Ranking and Scenario
searches returned 22 and eight builds respectively.

Normal Top 3 returned Amy Rose, my usual build; Big the Cat with a different
setup; and Leonardo after work. None is in the controlled-key ledger. This
changes the displayed winners because deliberate teaching examples are now
excluded; the ranking formula itself is unchanged.

Live API checks independently calculated each individual effect from the shared
rule vector, verified the unsupported combined state, and confirmed the build
response was identical before and after scenario calculations. Invincible Finish
was inactive at 301 metres and active/non-stat at 300 metres.

Browser verification through localhost:5173 covered Explore, renamed ranking
details (40 upvotes/1 downvote unchanged), comment-page navigation from page 1
to page 2, the combined fixture's passive/individual/partial states and reset,
Compare against Invincible Finish, and authenticated Remix. Remix copied Amy,
all three Speedster Lightning parts and ordered Quick Starter/Sea Dog into a
valid 4/6-slot draft. It was not published; the isolated demo account was logged
out afterward. Existing unrelated frontend edits were left untouched.

67 controlled fixtures keep their existing IDs. Ordinary generated builds retain their titles.
The unmanaged `Remix of Knuckles, my weekend main [Top 3 Demo]` (ID
`0d255ecd-57af-44b5-a8d4-7b8ec20af106`) belongs to a normal user and is left untouched.

| Stable fixture key | Existing ID | Old title | New title |
| --- | --- | --- | --- |
| acceleration | d0098baa-9dd1-4915-982b-cd60dee60b26 | [Wilson Demo] Same score, divided opinion | [Demo · Ranking] Same score, divided opinion |
| amy-drift | 8e4d66a5-71dd-4042-979a-87aaaa1c27c7 | [Wilson Demo] Negative score with positive votes | [Demo · Ranking] Negative score with positive votes |
| boost | c838f75c-64d6-4631-945a-de93b638b696 | [Wilson Demo] Close confidence scores: thirty-eight approvals | [Demo · Ranking] Close confidence scores: thirty-eight approvals |
| community-amy-1 | 84c5fe4e-e342-4610-9524-e0e9207eaaf2 | [Wilson Demo] Zero-confidence tie: no votes | [Demo · Ranking] Zero-confidence tie: no votes |
| community-amy-2 | 00dbf30d-f187-4d38-9fa1-435c05828f98 | [Wilson Demo] Zero-confidence tie: one downvote | [Demo · Ranking] Zero-confidence tie: one downvote |
| community-amy-3 | 4c521279-e10d-4027-b3ff-be085400a5a7 | [Wilson Demo] Zero-confidence tie: five downvotes | [Demo · Ranking] Zero-confidence tie: five downvotes |
| community-blaze-1 | dd424660-3604-47e5-bd0a-c0fe197fb043 | Proto Man on Extreme Gear [Top 3 Demo] | [Demo · Ranking] Proto Man on Extreme Gear |
| community-cream-1 | 758aa5de-8acd-411a-a17d-cb39810dbbcd | [Passive and Conditional Demo] Knuckles the Dread - Panel Combo Kit | [Demo · Stats] Knuckles the Dread - Panel Combo Kit |
| community-knuckles-2 | 501dd4ec-70b4-427f-8fa4-93553300e863 | [Passive Handling Demo] Hatsune Miku - Drift Charge Kit | [Demo · Stats] Hatsune Miku - Drift Charge Kit |
| community-metalhead-1 | a6e7c37a-eeb5-4ab6-be68-7c79b1e09f41 | Knuckles, my weekend main [Top 3 Demo] | [Demo · Ranking] Knuckles, my weekend main |
| community-rosegrid-1 | 3980d300-6dd3-47d4-ad58-7cb6353fa68d | [Conditional Effects Demo] Espio - Quick Starter + Ring Evolution | [Demo · Stats] Espio - Quick Starter + Ring Evolution |
| community-rouge-1 | 99691872-f6c5-4513-a1c4-896afd331354 | [Tuner Penalty Demo] Wave the Swallow - Handling Tuner 1 | [Demo · Stats] Wave the Swallow - Handling Tuner 1 |
| community-rouge-2 | 468f58ae-2609-4a98-b677-fc40e42dc059 | [Machine Kit Demo] Tails Nine - Power Machine Kit | [Demo · Stats] Tails Nine - Power Machine Kit |
| community-shadow-1 | 7cdbeb85-2ba7-4cf0-91e6-47fa01b3fed9 | [DEMO] Last week's garage pick | [Demo · Ranking] Last week's garage pick |
| community-shadow-2 | 53f680c0-5408-42f9-b0b5-f8825e0594d2 | [Wilson Demo] Same score, unanimous approval | [Demo · Ranking] Same score, unanimous approval |
| community-shadow-3 | 527ad919-bae0-465b-aeaf-b927ef5e4a49 | [DEMO] This week's garage pick | [Demo · Ranking] This week's garage pick |
| community-sonic-1 | 15de0ac9-f872-45ba-935d-507de97aa6a1 | [DEMO] A morning garage experiment | [Demo · Ranking] A morning garage experiment |
| community-sonic-2 | 5cf387a2-5026-49f3-85d3-ace6be8a19ca | [DEMO] Another morning garage experiment | [Demo · Ranking] Another morning garage experiment |
| community-sonic-3 | 74b75b2f-cde8-4c99-8d70-8909ab31fa1d | [Base Stats Demo] PAC-MAN without gadgets | [Demo · Stats] PAC-MAN without gadgets |
| community-sonic-4 | 5316c17a-6d86-4638-9bff-26be9d03945a | [Verified Stacking Demo] Werehog - Speed Tuner 1 + Speed Tuner 2 | [Demo · Stats] Werehog - Speed Tuner 1 + Speed Tuner 2 |
| community-tails-1 | 032311da-3336-4470-b184-17346fd50dc3 | [Version Demo] Newer patch wins a confidence tie | [Demo · Ranking] Newer patch wins a confidence tie |
| community-tails-2 | ec87d28c-13ba-4797-813e-be5b0353c0c0 | [Version Demo] Older patch in a confidence tie | [Demo · Ranking] Older patch in a confidence tie |
| community-ultimatefan-2 | 670e1b6d-1d59-414d-8bd5-af8bf8af179e | [Stat Tradeoff Demo] Ichiban Kasuga - Double Down | [Demo · Stats] Ichiban Kasuga - Double Down |
| cornering | d7f2e7c4-f55e-424d-a177-fcdef3101fd4 | [Wilson Demo] Small sample, perfect approval | [Demo · Ranking] Small sample, perfect approval |
| feature-all-rounder-v1 | da7f377d-59d4-4593-a5b7-8dd0c543d9e0 | [DEMO] All-Rounder: either transformation | [Demo · Scenario] All-Rounder: either transformation |
| feature-flight-v1 | 36ec991d-80e6-433c-8203-510c04456fd8 | [DEMO] Ace Pilot: flight form | [Demo · Scenario] Ace Pilot: flight form |
| feature-landing-v1 | 788ba853-deb0-4bf2-8dcc-895b7a943f48 | [DEMO] Perfect Landing: active utility | [Demo · Scenario] Perfect Landing: active utility |
| feature-locks-v1 | d3cf41ec-9905-4935-91f9-8ee45eafac55 | [DEMO] Combined locks: keep several favourites | [Demo · Recommendation] Combined locks: keep several favourites |
| feature-ownership-v1 | c55d4a06-49a3-41e4-a03a-202634e150a9 | [DEMO] Collection exclusions: another starting point | [Demo · Ownership] Collection exclusions: another starting point |
| feature-quick-v1 | 8e5d36a4-9556-4350-aa3d-e37f17b00470 | [DEMO] Quick Starter: first lap | [Demo · Scenario] Quick Starter + Sea Dog |
| feature-rings-v1 | 07a2a48e-d48a-4e2b-87c1-3394554ddaf4 | [DEMO] Ring Engine: rings held now | [Demo · Scenario] Ring Engine: rings held now |
| feature-super-v1 | c251da67-f0fa-4f51-92bb-e120e3612f28 | [DEMO] Super Slow Starter: final lap | [Demo · Scenario] Super Slow Starter: final lap |
| feature-unsupported-v1 | e1275c4b-c185-488b-a18b-ffa57a3779b8 | [DEMO] Damage Evolution: honest unknown | [Demo · Scenario] Damage Evolution: honest unknown |
| feature-water-v1 | 039e0183-ab8a-44a6-aff8-78b72eac5a33 | [DEMO] Sea Dog: water form | [Demo · Scenario] Invincible Finish — Near the finish |
| finish | 5a7acce6-c022-4c1e-b071-456d7d2a5d01 | [Wilson Demo] Evenly split feedback | [Demo · Ranking] Evenly split feedback |
| generated-amy-4 | 735f113e-9890-497a-9060-cbd13405a3b4 | [Gadget Demo] Starting with an empty plate | [Demo · Stats] Starting with an empty plate |
| generated-chaotix-2 | 4a6aaa3d-a385-4e25-adee-ee1251a0a7d1 | [Map Demo] Keeping every route open | [Demo · Maps] Keeping every route open |
| generated-cloudnine-4 | dcc7e700-4dfb-4023-ac8e-fcddf87d15bc | [Machine Demo] My speed parts combination | [Demo · Machines] My speed parts combination |
| generated-copperline-2 | 10f2cfd3-cbac-4e1d-9973-eb5483699bc8 | [Gadget Demo] Starting with an empty plate | [Demo · Stats] Starting with an empty plate |
| generated-copperline-3 | 2c7adc86-dff9-428c-bbfd-1d0f1d9d19fd | [Machine Demo] My power parts combination | [Demo · Machines] My power parts combination |
| generated-crossover-2 | df25a9b4-3d52-42a2-9ade-9bb3ce14382a | [Remix Demo] A second take on a saved setup | [Demo · Remix] A second take on a saved setup |
| generated-eggman-5 | 124c78d7-a73c-4bc7-8e80-ef6d726a5914 | [Remix Demo] A second take on a saved setup | [Demo · Remix] A second take on a saved setup |
| generated-megafan-3 | 03a80501-3f1a-4b3b-b2ee-4bedc28b0c34 | [Gadget Demo] Starting with an empty plate | [Demo · Stats] Starting with an empty plate |
| generated-neonroute-1 | f5e01df0-f067-486a-b153-0cdc2ca6bdce | [Gadget Demo] Starting with an empty plate | [Demo · Stats] Starting with an empty plate |
| generated-nightowl-2 | 2b382c86-a851-4d72-8d71-74b89b62fa42 | [Machine Demo] My acceleration parts combination | [Demo · Machines] My acceleration parts combination |
| generated-nightowl-4 | 703fa70b-f3cb-43f1-867f-56e6e85b3a11 | [Remix Demo] A second take on a saved setup | [Demo · Remix] A second take on a saved setup |
| generated-peachpit-1 | 623b0fc4-1779-403e-93a3-3bf916fb1ba6 | [Remix Demo] A second take on a saved setup | [Demo · Remix] A second take on a saved setup |
| generated-quietgarage-3 | 57c62dd2-4016-4b8c-b055-b7da5dc4fc4c | [Remix Demo] A second take on a saved setup | [Demo · Remix] A second take on a saved setup |
| generated-raincheck-1 | 547d17a1-18af-41e9-944e-42764ee7bfc1 | [Machine Demo] My speed parts combination | [Demo · Machines] My speed parts combination |
| generated-rouge-5 | b375b87c-3f61-4fd0-8ca0-4338f4ac0cbd | [Map Demo] Keeping every route open | [Demo · Maps] Keeping every route open |
| generated-silver-3 | c990a2a7-2d39-4ac3-a4d9-1cf02268dea6 | [Remix Demo] A second take on a saved setup | [Demo · Remix] A second take on a saved setup |
| generated-silver-4 | 89c38f6f-a4a3-44d3-9fe9-517b71e2fef5 | [Map Demo] Keeping every route open | [Demo · Maps] Keeping every route open |
| generated-ultimatefan-3 | 6fb67da7-f44b-456f-85dd-586a740155af | [Map Demo] Keeping every route open | [Demo · Maps] Keeping every route open |
| generated-vector-4 | 73faa0da-c776-4155-99f2-1d268dbde911 | [Gadget Demo] Starting with an empty plate | [Demo · Stats] Starting with an empty plate |
| items | aef4b3ae-f9c1-4768-ac3c-98f82be5e4be | [Wilson Demo] Large sample, strong approval | [Demo · Ranking] Large sample, strong approval |
| knuckles-endurance | faa05773-a1c0-44da-a809-f53c33a10351 | [Comment Demo] 81 comments — five-page example | [Demo · Comments] 81 comments — five-page example |
| knuckles-power | ba405e64-ac7b-49ae-a8e7-edbe263d9e74 | [Comment Demo] 61 comments — four-page example | [Demo · Comments] 61 comments — four-page example |
| knuckles-ring | ca131005-7c7e-44ba-955e-0e4b71658b41 | [Comment Demo] 101 comments — long discussion | [Demo · Comments] 101 comments — long discussion |
| optimizer-miku-balanced-v1 | 20d86571-17e8-47c0-b544-107a15dfb8e8 | [Optimizer Demo] Miku — Boost trade-off baseline | [Demo · Recommendation] Miku — Boost trade-off baseline |
| recovery | 70cb581a-3359-4fc2-aec8-467d18cac9e1 | [Wilson Demo] Close confidence scores: sixteen clean votes | [Demo · Ranking] Close confidence scores: sixteen clean votes |
| route | e594fc71-6a23-45b6-9bd3-e83b59c9686d | [Racer Kit Demo] E-123 Omega - Power Character Kit | [Demo · Stats] E-123 Omega - Power Character Kit |
| shadow | 35b071de-7e2f-4fe8-ad6c-b8f0ddbd05f8 | [Wilson Demo] Unrated build | [Demo · Ranking] Unrated build |
| shadow-laps | 5ac7a5a9-14a1-4c92-8eed-da8fb0c4d078 | [Comment Demo] 21 comments — first overflow | [Demo · Comments] 21 comments — first overflow |
| sonic-boost | 3ff692e5-bacd-4319-89e6-7d64699880c2 | [Comment Demo] 40 comments — two full pages | [Demo · Comments] 40 comments — two full pages |
| sonic-route | d474b6d5-3ac8-40e7-909a-50c02c87de02 | [Comment Demo] 41 comments — third-page overflow | [Demo · Comments] 41 comments — third-page overflow |
| sonic-speed | 2c6c789c-9c32-4a73-867c-92c4f0fd0162 | Sonic and the handling setup [Top 3 Demo] | [Demo · Ranking] Sonic and the handling setup |
| tails-grid | d9afda31-9b28-465c-b747-99b7b013618a | [Wilson Demo] Downvotes only | [Demo · Ranking] Downvotes only |
