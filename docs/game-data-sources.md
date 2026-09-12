# Sonic Racing: CrossWorlds catalog ledger

Cutoff and date checked for **every row below: 2026-09-12** (Europe/Bucharest).
This ledger accompanies `V6__released_racer_and_machine_catalog.sql` and
`V7__verified_gadget_catalog.sql`, `V8__community_artwork_for_catalog_gaps.sql`, and
`V9__wiki_artwork_for_catalog_presentation.sql`. Earlier migrations are unchanged.

## Coverage and limits

The resulting catalog contains **53 racers, 27 machines and 81 machine parts**. Racers comprise
23 MAIN, 13 FREE_UPDATE, 16 DLC (13 collaboration racers plus 3 Sonic Prime racers), and 1 BONUS.
No skin or announced future racer is inserted. Every machine has exactly one FRONT, REAR and TIRE
catalog part. These parts implement RingLab's existing composition model; their presence does not
assert that every crossover vehicle can be mixed freely in the actual game.

**Racer coverage is complete against the official roster and release notices checked. Machine
coverage is not complete against SEGA's advertised 45 original vehicles.** The official machine
pages expose five illustrated types and selected crossover vehicles, not an exhaustive named
launch catalog. V6 adds every separately named, released machine established by the sources below;
it retains the ten supplied V2 machines. The other launch machines and the unnamed Werehog bonus
vehicle remain an evidence gap, not an assertion that they are unreleased. Do not use the count of
27 as the game's total machine count. A complete named first-party launch inventory is still needed.

Research checked the Asian and western official sites, both official news feeds, SEGA's publisher
Steam announcements, individual Steam DLC listings, SEGA's press newsroom, and the official Steam
customization trailer. The trailer shows customization and mixed-part names, so those names were
not treated as proof of additional stock machines. The Sonic Wiki is used only for the separately
user-approved presentation artwork policy described below, never as evidence for catalog facts.
The western SEGA site required a normal browser visit; its Features tab also advertises 45 vehicles
without listing them individually. No guessed names or types were added to fill that gap.

Existing V2 IDs, names, types, V4 part IDs, and community data are preserved. Existing racing types
are inherited supplied data, **not newly independently verified** by this research. New racing
types are null because release/roster pages do not establish them. The user approved this nullable
contract; the UI shows Unknown. `RacingType` remains an enum with its existing five values.

## Source conventions

Each row supplies the RingLab canonical name, category, release status/evidence, original artwork
evidence, local path and ambiguity. All rows share the date checked above. Release dates are the
dates stated by the linked publication/store and can differ by one day across time zones; this
does not affect the September 12 cutoff. A pass being on sale is not proof that all its packs shipped.

Artwork source paths beginning **A/** resolve under the first-party asset root
<https://asia.sega.com/SonicRacingCrossWorlds/assets/images/common/>; paths beginning **E/** resolve
under <https://asia.sega.com/SonicRacingCrossWorlds/assets/images/en/>. These are exact source paths,
not local paths or inferred filenames. Other artwork sources are full links. Local paths are
served from `frontend/public`; none are remote hotlinks.

V9 replaces every local racer, stock-machine and gadget image with the corresponding image from the
[Sonic Wiki CrossWorlds character][r-fandom], [machine][m-fandom] or [gadget][g-fandom] catalog.
This is a user-selected presentation policy and applies even where the original evidence column
names a first-party asset. Images are local PNG downloads, never hotlinks; no AI artwork, redraw or
restyling was used. The catalog now has 53 racer, 27 machine and 71 gadget image paths.

## RACERS

| RingLab canonical name | Category | Release status / official data source | Official artwork source | Local artwork path | Ambiguity / decision |
| --- | --- | --- | --- | --- | --- |
| Sonic the Hedgehog | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/sonic.png | /assets/racers/sonic.png | Official roster label SONIC; existing identity retained. |
| Miles "Tails" Prower | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/tails.png | /assets/racers/tails.png | Official label TAILS; existing identity retained. |
| Knuckles the Echidna | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/knuckles.png | /assets/racers/knuckles.png | Official label KNUCKLES; existing identity retained. |
| Amy Rose | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/amy.png | /assets/racers/amy.png | Official label AMY; existing identity retained. |
| Cream & Cheese | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/cream.png | /assets/racers/cream-and-cheese.png | One selectable racer entry, not two. |
| Big the Cat | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/big.png | /assets/racers/big.png | Official label BIG; existing identity retained. |
| Silver the Hedgehog | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/silver.png | /assets/racers/silver.png | Official short label SILVER. |
| Blaze the Cat | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/blaze.png | /assets/racers/blaze.png | Official short label BLAZE. |
| Shadow the Hedgehog | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/shadow.png | /assets/racers/shadow.png | Existing identity retained. |
| Rouge the Bat | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/rouge.png | /assets/racers/rouge.png | Official short label ROUGE. |
| E-123 Omega | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/omega.png | /assets/racers/omega.png | Official short label OMEGA. |
| Vector the Crocodile | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/vector.png | /assets/racers/vector.png | Official short label VECTOR. |
| Espio the Chameleon | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/espio.png | /assets/racers/espio.png | Official short label ESPIO. |
| Charmy Bee | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/charmy.png | /assets/racers/charmy.png | Official short label CHARMY. |
| Zavok | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/zavok.png | /assets/racers/zavok.png | No additional ambiguity. |
| Zazz | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/zazz.png | /assets/racers/zazz.png | No additional ambiguity. |
| Dr. Eggman | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/eggman.png | /assets/racers/eggman.png | No additional ambiguity. |
| Metal Sonic | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/metalsonic.png | /assets/racers/metal-sonic.png | Separate racer. |
| Egg Pawn | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/eggpawn.png | /assets/racers/egg-pawn.png | Separate racer. |
| Sage | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/sage.png | /assets/racers/sage.png | No additional ambiguity. |
| Jet the Hawk | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/jet.png | /assets/racers/jet.png | Official short label JET. |
| Wave the Swallow | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/wave.png | /assets/racers/wave.png | Official short label WAVE. |
| Storm the Albatross | MAIN | RELEASED — [roster][roster], [launch][launch] | A/racers/pc/storm.png | /assets/racers/storm.png | Official short label STORM. |
| Hatsune Miku | FREE_UPDATE | RELEASED 2025-09-22 — [available notice][miku] | A/racers/pc/miku.png | /assets/racers/hatsune-miku.png | Racing outfit is not an additional racer. |
| Joker | FREE_UPDATE | RELEASED 2025-10-23 — [available notice][joker] | A/racers/pc/joker.png | /assets/racers/joker.png | Earlier network-test availability is not used as the release date. |
| Ichiban Kasuga | FREE_UPDATE | RELEASED 2025-11-06 — [available notice][ichiban] | A/racers/pc/kasuga.png | /assets/racers/ichiban-kasuga.png | No additional ambiguity. |
| NiGHTS | FREE_UPDATE | RELEASED 2025-12-25 — [available notice][nights] | A/news/251112update/freechara01.png | /assets/racers/nights.png | Artwork announcement predates release; release independently checked. |
| AiAi | FREE_UPDATE | RELEASED 2026-02-12 — [available notice][aiai] | A/news/251112update/freechara02.png | /assets/racers/aiai.png | Earlier January roadmap superseded by released notice. |
| Tangle | FREE_UPDATE | RELEASED by 2026-02-26 — [released statement][tangle] | A/news/251112update/freechara03.png | /assets/racers/tangle.png | Asian notice has a February 5 heading but February 25 publication/February 27 festival; do not infer a February 5 release. |
| Whisper | FREE_UPDATE | RELEASED by 2026-02-26 — [released statement][tangle] | A/news/251112update/freechara04.png | /assets/racers/whisper.png | Same publication-date ambiguity as Tangle. |
| Red | FREE_UPDATE | RELEASED 2026-04-15 — [available notice][red] | A/news/260317update/freechara05.webp | /assets/racers/red.png | Asian notice uses April 16. |
| Goro Majima (Captain Majima) | FREE_UPDATE | RELEASED 2026-04-29 — [Steam released notice][majima] | A/news/260317update/freechara06.webp | /assets/racers/captain-majima.png | Official released racer identity includes Captain Majima; no second Goro row. |
| Arle | FREE_UPDATE | RELEASED 2026-05-27 — [available notice][arle] | A/news/260317update/freechara07.webp | /assets/racers/arle.png | Asian notice uses May 28. |
| Classic Sonic | FREE_UPDATE | RELEASED by 2026-06-24 — [available notice][classic] | A/racers/pc/classicsonic.webp | /assets/racers/classic-sonic.png | Explicitly added playable racer, not a Sonic skin. |
| Axel | FREE_UPDATE | RELEASED 2026-08-25 — [Steam released notice][axel] | A/racers/pc/axel.webp | /assets/racers/axel.png | Asian notice uses August 26. |
| Amigo | FREE_UPDATE | RELEASED 2026-09-08 — [Steam released notice][amigo] | A/racers/pc/amigo.webp | /assets/racers/amigo.png | Asian notice uses September 9. Amigo (Classic) excluded as a skin. |
| Steve | DLC | RELEASED 2025-10-08 — [Steam pack][minecraft] | A/racers/pc/mnc01.png | /assets/racers/steve.png | Selectable racer, not an Alex skin. |
| Alex | DLC | RELEASED 2025-10-08 — [Steam pack][minecraft] | A/racers/pc/mnc02.png | /assets/racers/alex.png | Explicit separate racer. |
| Creeper | DLC | RELEASED 2025-10-08 — [Steam pack][minecraft] | A/racers/pc/mnc03.png | /assets/racers/creeper.png | Explicit separate racer. |
| SpongeBob SquarePants | DLC | RELEASED 2025-11-19 — [Steam pack][sponge] | A/news/250622/spgchara01.png | /assets/racers/spongebob.png | Official short roster label SpongeBob. |
| Patrick Star | DLC | RELEASED 2025-11-19 — [Steam pack][sponge] | A/news/250622/spgchara02.png | /assets/racers/patrick.png | Official short roster label Patrick. |
| PAC-MAN | DLC | RELEASED 2026-01-07 — [Steam pack][pac-store], [racer/skin breakdown][pac] | A/news/250820pac/pcmchara02.png | /assets/racers/pac-man.png | Separate from Blinky. |
| Blinky | DLC | RELEASED 2026-01-07 — [Steam pack][pac-store], [racer/skin breakdown][pac] | A/news/250820pac/pcmchara01.png and A/racers/pc/ghost.png inspected; both group all four ghosts | null | No isolated official Blinky asset obtained. Group art would visually conflate racer and skins; initials fallback retained. |
| Mega Man | DLC | RELEASED 2026-03-25 — [Steam pack][mega] | A/news/250925rockman/rcmchara01.png | /assets/racers/mega-man.png | No additional ambiguity. |
| Proto Man | DLC | RELEASED 2026-03-25 — [Steam pack][mega] | A/news/250925rockman/rcmchara02.png | /assets/racers/proto-man.png | Explicit separate racer. |
| Leonardo | DLC | RELEASED 2026-07-28 — [Steam pack][tmnt] | A/racers/pc/leonardo.webp | /assets/racers/leonardo.png | One of four selectable turtles. |
| Raphael | DLC | RELEASED 2026-07-28 — [Steam pack][tmnt] | A/racers/pc/raphael.webp | /assets/racers/raphael.png | One of four selectable turtles. |
| Donatello | DLC | RELEASED 2026-07-28 — [Steam pack][tmnt] | A/racers/pc/donatello.webp | /assets/racers/donatello.png | One of four selectable turtles. |
| Michelangelo | DLC | RELEASED 2026-07-28 — [Steam pack][tmnt] | A/racers/pc/michelangelo.webp | /assets/racers/michelangelo.png | One of four selectable turtles. |
| Tails Nine | DLC | RELEASED at launch — [launch notice][launch] | A/racers/pc/dde01.png | /assets/racers/tails-nine.png | Sonic Prime character; official roster short label NINE; not a Tails costume. |
| Knuckles the Dread | DLC | RELEASED at launch — [launch notice][launch], [SEGA release][launch-press] | A/racers/pc/dde02.png | /assets/racers/knuckles-the-dread.png | Sonic Prime character; DREAD / Knuckles Dread are aliases. |
| Rusty Rose | DLC | RELEASED at launch — [launch notice][launch] | A/racers/pc/dde03.png | /assets/racers/rusty-rose.png | Sonic Prime character; not an Amy costume. |
| Werehog | BONUS | RELEASED with launch preorder bonus — [roster][roster], [purchase guide][guide], [launch][launch] | A/racers/pc/bns01.png | /assets/racers/werehog.png | Official bonus racer, despite being a form of Sonic; not treated as a skin. |

## MACHINES

The ten MAIN rows predate this research (V2). Their exact names/types remain supplied data where
the public official sources do not independently name the machine. The five matched type-card
renders below are visual identifications, not textual name confirmations. This distinction is
recorded instead of presenting a generic marketing page as a complete named inventory.

| RingLab canonical name | Category | Release status / official data source | Official artwork source | Local artwork path | Ambiguity / decision |
| --- | --- | --- | --- | --- | --- |
| Speedster Lightning | MAIN | Existing launch seed; [official machine illustrations][machines] | A/machines/type01.png | /assets/machines/speedster-lightning.png | Supplied name/type retained; visually matched speed illustration. |
| Dark Reaper | MAIN | Existing launch seed; [official machine page][machines] does not name it | [Community machine icon][m-fandom] → /assets/machines/dark-reaper.png | /assets/machines/dark-reaper.png | User-approved community artwork supplement; independent first-party name verification remains open. |
| Whirlwind Sport | MAIN | Existing launch seed; [official machine illustrations][machines] | A/machines/type02.png | /assets/machines/whirlwind-sport.png | Supplied name/type retained; visually matched acceleration illustration. |
| Jumble Rage | MAIN | Existing launch seed; [official machine page][machines] does not name it | [Community machine artwork][m-fandom] → /assets/machines/jumble-rage.png | /assets/machines/jumble-rage.png | User-approved community artwork supplement; independent first-party name verification remains open. |
| Pink Cabriolet | MAIN | Existing launch seed; [official machine illustrations][machines] | A/machines/type03.png | /assets/machines/pink-cabriolet.png | Supplied name/type retained; visually matched handling illustration. |
| Neo Lightron | MAIN | Existing launch seed; [official machine page][machines] does not name it | [Community machine icon][m-fandom] → /assets/machines/neo-lightron.png | /assets/machines/neo-lightron.png | User-approved community artwork supplement; independent first-party name verification remains open. |
| Land Smasher | MAIN | Existing launch seed; [official machine illustrations][machines] | A/machines/type04.png | /assets/machines/land-smasher.png | Supplied name/type retained; visually matched power illustration. |
| Road Dragoon | MAIN | Existing launch seed; [official machine page][machines] does not name it | [Community machine icon][m-fandom] → /assets/machines/road-dragoon.png | /assets/machines/road-dragoon.png | User-approved community artwork supplement; independent first-party name verification remains open. |
| TYPE-J Iota | MAIN | Existing launch seed; [official machine illustrations][machines] | A/machines/type05.png | /assets/machines/type-j-iota.png | Supplied name/type retained; visually matched boost illustration. |
| TYPE-S Stream | MAIN | Existing launch seed; [official machine page][machines] does not name it | [Community machine artwork][m-fandom] → /assets/machines/type-s-stream.png | /assets/machines/type-s-stream.png | User-approved community artwork supplement; independent first-party name verification remains open. |
| Diva Macchina | FREE_UPDATE | RELEASED 2025-09-22 — [available notice][miku] | A/machines/addmachine02.png | /assets/machines/diva-macchina.png | Static machine page's future wording is stale. |
| Arsene Wing | FREE_UPDATE | RELEASED 2025-10-23 — [available notice][joker] | A/machines/addmachine04.png | /assets/machines/arsene-wing.png | Arsène Wing is an accented alias; use release notice spelling, one row. |
| Dragon Brave | FREE_UPDATE | RELEASED 2025-11-06 — [available notice][ichiban] | A/machines/addmachine03.png | /assets/machines/dragon-brave.png | Static machine page's future wording is stale. |
| Dream Sleeper | FREE_UPDATE | RELEASED 2025-12-25 — [available notice][nights] | A/news/251112update/freemachine01.png | /assets/machines/dream-sleeper.png | Separate release evidence from artwork announcement. |
| Banana Cruiser | FREE_UPDATE | RELEASED 2026-02-12 — [available notice][aiai] | A/news/251112update/freemachine02.png | /assets/machines/banana-cruiser.png | Earlier January roadmap is superseded. |
| Super Roaster | FREE_UPDATE | RELEASED 2026-04-15 — [available notice][red] | A/news/260317update/freemachine05.webp | /assets/machines/super-roaster.png | Asian notice uses April 16. |
| Goromaru | FREE_UPDATE | RELEASED 2026-04-29 — [Steam released notice][majima] | A/news/260317update/freemachine06.webp | /assets/machines/goromaru.png | Asian notice uses April 30. |
| Twinkle Bayoen | FREE_UPDATE | RELEASED 2026-05-27 — [available notice][arle] | A/news/260317update/freemachine07.webp | /assets/machines/twinkle-bayoen.png | Asian notice uses May 28. |
| Mach Cyclone | FREE_UPDATE | RELEASED by 2026-06-24 — [available notice][classic], [patch details][classic-patch] | E/news/classicsonic/img01.webp | /assets/machines/mach-cyclone.png | Official promotional screenshot includes driver; isolated render not obtained. |
| Devolada Yellow Jack | FREE_UPDATE | RELEASED 2026-08-25 — [Steam released notice][axel] | [Official release banner](https://sonicracing.sega.com/upload_images/5d6c2bbdee6e4d79d9d0460f637e0a97b80e7033.jpg) | /assets/machines/devolada-yellow-jack.png | Official promotional screenshot includes Axel; isolated render not obtained. |
| Locomotive de Amigo | FREE_UPDATE | RELEASED 2026-09-08 — [Steam released notice][amigo] | [Official release screenshot](https://sonicracing.sega.com/upload_images/35dae289c210cb065d9a395809ba7822d3804cb9.jpg) | /assets/machines/locomotive-de-amigo.png | Official screenshot includes Amigo; isolated render not obtained. |
| Minecart | DLC | RELEASED 2025-10-08 — [Steam pack][minecraft] | A/machines/addmachine01.png | /assets/machines/minecart.png | One machine shared by the three Minecraft racers. |
| Patty Wagon | DLC | RELEASED 2025-11-19 — [Steam pack][sponge] | A/news/250622/spgmachine01.png | /assets/machines/patty-wagon.png | One machine shared by the SpongeBob pack. |
| PAC-MAN Mobile | DLC | RELEASED 2026-01-07 — [Steam pack][pac-store], [available notice][pac] | A/news/250820pac/pcmmachine01.png | /assets/machines/pac-man-mobile.png | One machine; no skin-specific duplicates. |
| Rush Roadstar | DLC | RELEASED 2026-03-25 — [Steam pack][mega] | A/news/250925rockman/rcmmachine01.png | /assets/machines/rush-roadstar.png | Earlier press copy sometimes says Roadster; use released Steam spelling. |
| Pizzafire Van | DLC | RELEASED 2026-07-28 — [Steam pack][tmnt] | A/guide/mttpack.webp | /assets/machines/pizzafire-van.png | Cropped machine region of official pack composite; no turtle pixels retained. |
| Blue Star | BONUS | RELEASED — [Steam DLC][blue-store], [general availability 2026-01-13][blue] | A/news/250715/board.webp | /assets/machines/blue-star.png | Originally SEGA Account bonus, subsequently freely available; not omitted when promotion expired. |

## ANNOUNCED BUT NOT YET RELEASED

No production rows or machine parts are created for these entries. Checked 2026-09-12.

| Entity / content | Category | Status | Official evidence | Artwork / local path | Decision |
| --- | --- | --- | --- | --- | --- |
| Aang | DLC | ANNOUNCED, NOT RELEASED | [Season Pass Steam listing][season] explicitly says Avatar Legends releases October 2026; [roster][roster] alone is not release proof | A/racers/pc/aang.webp listed by roster; not downloaded / null | Excluded. |
| Katara | DLC | ANNOUNCED, NOT RELEASED | [Season Pass Steam listing][season], [roster][roster] | A/racers/pc/katara.webp listed by roster; not downloaded / null | Excluded. |
| Gliding Air | DLC | ANNOUNCED, NOT RELEASED | [Season Pass Steam listing][season], [purchase guide][guide] | A/guide/avtpack.webp is a future pack composite; not downloaded / null | Excluded. |
| Season Pass 2: Godzilla and EVANGELION collaborations; four unrevealed packs | DLC | ANNOUNCED for fall 2026; no release confirmation found | [SEGA announcement][season2] | E/news/season2pass/img01.webp; not downloaded / null | No invented racer or machine names. Courses Godzilla Escape and Tokyo-III do not establish selectable racer identities. |

## SKINS / NOT SEPARATE RACERS

Checked 2026-09-12. These are deliberately absent from `racers`.

| Apparent character / label | Decision | Official evidence |
| --- | --- | --- |
| Inky | Released skin of Blinky, not a separate racer | [PAC-MAN release breakdown][pac] lists racers PAC-MAN/Blinky and skins Inky/Pinky/Clyde. |
| Pinky | Released skin of Blinky, not a separate racer | [PAC-MAN release breakdown][pac]. |
| Clyde | Released skin of Blinky, not a separate racer | [PAC-MAN release breakdown][pac]. |
| GHOSTS / Team Ghost | Marketing grouping, not an additional racer row | [roster][roster], [Steam pack][pac-store], resolved by [explicit release breakdown][pac]. |
| Amigo (Classic) | Released skin of Amigo, not a separate racer | [Amigo released notice][amigo], [SEGA Asian breakdown][amigo-asia]. |
| Hatsune Miku racing outfit | Appearance of the released Hatsune Miku entry, not another row | [Miku released notice][miku]. |
| CPU/AI teams and online robot icons | Not evidence of new selectable characters | [patch notes][classic-patch] describe CPU icon changes; no new racer is established by an icon. |

Conversely, Classic Sonic, Werehog and the three Sonic Prime characters **are** explicitly marketed
as playable racer entries. Similarity to an existing Sonic character is not a reason to merge them.

## GADGETS

Checked **2026-09-12**, including the active Amigo Festival. V7 expands 20 rows to **71 rows**:
**51 newly verified released identities**, **16 substantiated existing identities**, and **4 retained
seed identities requiring review**. V7 provides **49 official local images**; V8 fills the remaining
**22 local paths from user-approved Sonic Wiki artwork**. There are **10 descriptions**,
and **9 latest verified slot costs**. The remaining **62 costs are NULL**. These counts describe
RingLab, **not a complete game roster**. The [official features page][g-features] advertises 70+
launch gadgets; updates explicitly added 6, 8 and 8, alongside Festival kits. Reaching 71 database
rows does not reconcile that launch roster or establish completeness.

Research covered the retail online manual, the archived Closed Network Test (CNT) manual, Asian
and western official Features pages, both official news feeds, all publisher Steam announcements
returned by the 100-entry feed through the cutoff, every published update in the Asian feed,
and named Festival reward graphics from Miku through Amigo. The western Features page lists
items (race pickups), not an exhaustive gadget inventory; those items were not inserted as gadgets.
The retail manual gives six sample gadgets, not a complete table. The CNT manual contains a
pre-release list with old names and costs; it is **not proof of current release or current cost**.
No fan database, community numerical data, search snippet, or AI-generated content supplies values.

**Strict six-slot validation is not supportable from this evidence set.** Obtaining an authoritative
current in-game inventory and costs, or explicitly approving another source policy, remains necessary.
Even known current costs must not be retroactively applied to older GameVersion builds. V7 changes
only `gadgets`; there is no capacity enforcement, mode restriction, version table, or schema change.
All existing IDs, ordered `build_gadgets`, machine parts, patches and social rows survive unchanged.
New UUIDs are explicit allocations `70000000-0000-4000-8000-000000000NNN`; N below is that suffix.
They do not depend on list order, runtime generation, or names changing later.

Names on released English graphics take precedence over inconsistent patch translations. Thus
Champion Bounty, Route Planner Bounty, Ring Doubler, Less is More and Summon Item Box follow
[the 1.3.1 graphic][g-art131], also embedded in Steam, rather than Steam prose aliases Champion
Bonus, Travel Choice Bonus, Double Rings, Low-Ring Speed Up and Item Summon. These are not
additional identities. Speed Character Kit is corroborated by the Red Festival and Asian 1.4.1
notes; Steam's “Speed Vehicle Kit” under Character Type Kit is not a new machine kit.

The two existing CNT labels Item Stock Swap and Ultimate Drift Charge are updated to the released
patch labels Inventory Swap and Ultimate Charge. The matching swap-button and fourth-charge
mechanics support this reconciliation; their UUIDs remain unchanged. The demo seeder's four name
lookups are updated accordingly, without changing compositions or running the seeder.

### Existing V2 audit

Every row shares the date above. `—` means no current effect/artwork evidence was obtained and
the field remains NULL. `G/` means `/assets/gadgets/`, served from `frontend/public`. Artwork
references link directly to the first-party image; numbered grid positions are row-major.
All twenty existing costs remain UNKNOWN: neither the released graphics nor the current manual
gives explicit individual costs for them. Historical CNT costs are deliberately not imported.

| RingLab name | Release / identity evidence | Effect source | Current cost / source | Artwork source → local path | Audit notes |
| --- | --- | --- | --- | --- | --- |
| Inventory Swap | Released: [1.3.1][g131]; old label [CNT][g-cnt] | [1.3.1 button description][g131] | UNKNOWN | — | Existing Item Stock Swap UUID retained; no unverified five-second cooldown imported. |
| Item Stock Plus | Released-site example: [Features][g-features] | — | UNKNOWN | [Features icon][g-stock-art] → G/item-stock-plus.png | CNT effect is not treated as current effect proof. |
| Attack Item Chance UP | **Retail status unsubstantiated**; [CNT][g-cnt] only | — | UNKNOWN | — | Retained for review, not counted as verified released. |
| Defense Item Chance UP | **Retail status unsubstantiated**; [CNT][g-cnt] only | — | UNKNOWN | — | Retained for review, not counted as verified released. |
| Boost Item Chance UP | **Retail status unsubstantiated**; [CNT][g-cnt] only | — | UNKNOWN | — | Possible relationship to Wisp Chance UP is unresolved; do not merge IDs. |
| Hazard Item Chance UP | **Retail status unsubstantiated**; [CNT][g-cnt] only | — | UNKNOWN | — | Retained for review, not counted as verified released. |
| Giant Rocket Punch | Released-site example: [Features][g-features] | — | UNKNOWN | [Features icon][g-punch-art] → G/giant-rocket-punch.png | No current numerical effect verified. |
| Ultimate Charge | Released: [1.3.1][g131]; old label [CNT][g-cnt] | [1.3.1][g131] | UNKNOWN | — | Existing Ultimate Drift Charge UUID retained; 1.5-second invincibility is patch-dependent. |
| Lucky Pair | Released in [1.2.0][g120] | — | UNKNOWN | [1.2.0 grid][g-art120], 5 → G/lucky-pair.png | Icon's number alone does not establish complete effect conditions. |
| Double Down | Released in [1.2.0][g120] | — | UNKNOWN | [1.2.0 grid][g-art120], 4 → G/double-down.png | Same evidence limitation. |
| Ring Mercy | Released in [1.2.0][g120] | — | UNKNOWN | [1.2.0 grid][g-art120], 2 → G/ring-mercy.png | Effect not inferred from icon. |
| Damage Mercy | Released in [1.2.0][g120] | — | UNKNOWN | [1.2.0 grid][g-art120], 1 → G/damage-mercy.png | Effect not inferred from icon. |
| Item Mercy | Released in [1.2.0][g120] | — | UNKNOWN | [1.2.0 grid][g-art120], 3 → G/item-mercy.png | Effect not inferred from icon. |
| Strong Finish | Released in [1.2.0][g120] | — | UNKNOWN | [1.2.0 grid][g-art120], 6 → G/strong-finish.png | Duration and triggering conditions unverified. |
| Champion Bounty | Released in [1.3.1][g131] | — | UNKNOWN | [1.3.1 grid][g-art131], 3 → G/champion-bounty.png | Steam prose alias Champion Bonus. |
| Route Planner Bounty | Released in [1.3.1][g131] | — | UNKNOWN | [1.3.1 grid][g-art131], 4 → G/route-planner-bounty.png | Steam prose alias Travel Choice Bonus. |
| Ring Doubler | Released in [1.3.1][g131] | — | UNKNOWN | [1.3.1 grid][g-art131], 6 → G/ring-doubler.png | Steam prose alias Double Rings. |
| Less is More | Released in [1.3.1][g131] | — | UNKNOWN | [1.3.1 grid][g-art131], 5 → G/less-is-more.png | Steam prose alias Low-Ring Speed Up; exact conditions unknown. |
| Ring Engine | Released in [1.3.1][g131] | — | UNKNOWN | [1.3.1 grid][g-art131], 7 → G/ring-engine.png | Partial balance facts belong in history, not an invented complete description. |
| Hyper Ring Engine | Released in [1.3.1][g131] | — | UNKNOWN | [1.3.1 grid][g-art131], 8 → G/hyper-ring-engine.png | Mode-dependent 1.4.1 changes documented below. |

### Newly verified released identities

`UNKNOWN` is always a NULL cost, never a guessed zero. A dash in Effect means NULL description.
Known costs of 3 come from the **printed numeral above the specific gadget** in [the official
1.2.0 screen][g-cost120], cross-checked against later notes with no later cost change found. No
cost is inferred from icon width, number of occupied hexagons, a kit's strength, or another kit.
In particular the retail manual's two-/six-slot sample plates do not supply individual costs here.
The observed supported cost range is 1–3 (including historical Crash Pads at 2); this is not an
assertion that all unresearched gadgets fall within it and is not a production validation rule.

| N | RingLab name | Released identity source | Effect source | Current cost / source | Artwork source → local path |
| --- | --- | --- | --- | --- | --- |
| 001 | Ultimate Air Trick | [1.4.1][g141] | — | UNKNOWN | [1.4.1 grid][g-art141], 1 → G/ultimate-air-trick.png |
| 002 | Perfect Landing | [1.4.1][g141] | — | UNKNOWN | [1.4.1 grid][g-art141], 2 → G/perfect-landing.png |
| 003 | Substitute Item | [1.4.1][g141] | — | UNKNOWN | [1.4.1 grid][g-art141], 3 → G/substitute-item.png |
| 004 | Invincible Finish | [1.4.1][g141] | — | UNKNOWN | [1.4.1 grid][g-art141], 4 → G/invincible-finish.png |
| 005 | Collision Evolution | [1.4.1][g141] | — | UNKNOWN | [1.4.1 grid][g-art141], 5 → G/collision-evolution.png |
| 006 | Ring Evolution | [1.4.1][g141] | — | UNKNOWN | [1.4.1 grid][g-art141], 6 → G/ring-evolution.png |
| 007 | Damage Evolution | [1.4.1][g141] | — | UNKNOWN | [1.4.1 grid][g-art141], 7 → G/damage-evolution.png |
| 008 | Item Hit Evolution | [1.4.1][g141] | — | UNKNOWN | [1.4.1 grid][g-art141], 8 → G/item-hit-evolution.png |
| 009 | Summon Item Box | [1.3.1][g131] | — | UNKNOWN | [1.3.1 grid][g-art131], 1 → G/summon-item-box.png |
| 010 | Go Go Omochao | [1.3.1][g131] | — | UNKNOWN | [1.3.1 grid][g-art131], 2 → G/go-go-omochao.png |
| 011 | Boost Character Kit | [1.2.0][g120], [Miku][miku] | — | 3 / [screen][g-cost120] | [Miku reward][g-miku-art] → G/boost-character-kit.png |
| 012 | Power Character Kit | [1.2.0][g120], [Minecraft Festival][g-minecraft] | — | 3 / [screen][g-cost120] | [reward][g-minecraft-art] → G/power-character-kit.png |
| 013 | Handling Character Kit | [1.2.0][g120], [Joker][joker] | [screen][g-cost120] | 3 / [screen][g-cost120] | [Joker reward][g-joker-art] → G/handling-character-kit.png |
| 014 | Comeback Kit | [1.2.0][g120], [Ichiban][ichiban] | — | 3 / [screen][g-cost120] | [reward][g-ichiban-art] → G/comeback-kit.png |
| 015 | Sea Dog Kit | [1.2.0][g120], [SpongeBob Festival][g-sponge] | — | 3 / [screen][g-cost120] | [reward][g-sponge-art] → G/sea-dog-kit.png |
| 016 | Acceleration Character Kit | [1.3.1][g131], [PAC-MAN Festival][g-pac] | — | UNKNOWN | [reward][g-pac-art] → G/acceleration-character-kit.png |
| 017 | Ace Pilot Kit | [1.3.1][g131], [NiGHTS][nights] | — | UNKNOWN | [reward][g-nights-art] → G/ace-pilot-kit.png |
| 018 | All-Rounder Kit | [1.3.1][g131], [AiAi][aiai] | — | UNKNOWN | [reward][g-aiai-art] → G/all-rounder-kit.png |
| 019 | Wisp Hoarder Kit | [1.3.1][g131], [Tangle/Whisper][g-tangle] | — | UNKNOWN | [reward][g-tangle-art] → G/wisp-hoarder-kit.png |
| 020 | Speed Character Kit | [1.4.1][g141], [Red][g-red] | — | UNKNOWN | [reward][g-red-art] → G/speed-character-kit.png |
| 021 | Item Buster Kit | [1.4.1][g141], [Mega Man Festival][g-mega] | — | UNKNOWN | [reward][g-mega-art] → G/item-buster-kit.png |
| 022 | Drift Spinner Kit | [1.4.1][g141], [Majima][majima] | — | UNKNOWN | [reward][g-majima-art] → G/drift-spinner-kit.png |
| 023 | Panel Combo Kit | [1.4.1][g141], [Arle][g-arle] | — | UNKNOWN | [reward][g-arle-art] → G/panel-combo-kit.png |
| 024 | 4th Stage Charge Kit | [Amigo Festival][amigo], September 10–13 ET; active at cutoff | — | UNKNOWN | [reward][g-amigo-art] → G/4th-stage-charge-kit.png |
| 025 | Perfect Charge Kit | [Axel Festival][axel], August 27–30 ET | — | UNKNOWN | [reward][g-axel-art] → G/perfect-charge-kit.png |
| 026 | Air Trick Action Kit | [TMNT Festival][g-tmnt], July 30–August 2 ET | — | UNKNOWN | [reward][g-tmnt-art] → G/air-trick-action-kit.png |
| 027 | Starting Boost Bounty | [Retail manual][g-manual] | [Manual][g-manual] | UNKNOWN | [Manual icon 1][g-manual1] → G/starting-boost-bounty.png |
| 028 | 130 Ring Limit | [Retail manual][g-manual] | [Manual][g-manual] | UNKNOWN | [Manual icon 2][g-manual2] → G/130-ring-limit.png |
| 029 | Collision Boost | [Retail manual][g-manual], [Steam 1.3.1][g-steam131] | [Manual][g-manual] | UNKNOWN | [Manual icon 3][g-manual3] → G/collision-boost.png |
| 030 | Extended Slipstream | [Retail manual][g-manual] | [Manual][g-manual] | UNKNOWN | [Manual icon 4][g-manual4] → G/extended-slipstream.png |
| 031 | Ring Thief | [Retail manual][g-manual] | [Manual][g-manual] | UNKNOWN | [Manual icon 5][g-manual5] → G/ring-thief.png |
| 032 | Mini Ring Thief | [Retail manual][g-manual] | [Manual][g-manual] | UNKNOWN | [Manual icon 6][g-manual6] → G/mini-ring-thief.png |
| 033 | Quick Recovery | [1.2.0][g120] | [1.2.0][g120]; no numeric timing | UNKNOWN | — |
| 034 | Acceleration Machine Kit | [1.2.0][g120] | — | 3 / [screen][g-cost120] | [screen][g-cost120] → G/acceleration-machine-kit.png |
| 035 | Wisp Chance UP | [1.2.0][g120] | — | UNKNOWN | — |
| 036 | Invincible Start | [1.2.0][g120] | — | UNKNOWN | — |
| 037 | Handling Machine Kit | [1.3.1][g131] | — | 3 / [screen][g-cost120] | —; screen clips the icon at its right edge |
| 038 | Power Machine Kit | [Steam 1.3.1][g-steam131] | — | UNKNOWN | — |
| 039 | Spin Drift | [1.3.1][g131], [1.4.1][g141] | — | UNKNOWN | — |
| 040 | Crash Pads | [1.3.1][g131], [1.4.1][g141] | — | 1 / [1.4.1][g141] | — |
| 041 | Warp Ring Specialist | [1.1.2][g112], [1.3.1][g131] | — | UNKNOWN | —; released but restricted in specified online modes |
| 042 | Item Keeper | [1.4.1][g141] | — | UNKNOWN | — |
| 043 | Perfect Charge Boost | [1.4.1][g141] | — | UNKNOWN | — |
| 044 | Friction Drift | [1.4.1][g141] | — | UNKNOWN | — |
| 045 | Quick Starter | [1.4.1][g141] | — | UNKNOWN | — |
| 046 | Slow Starter | [1.4.1][g141] | — | UNKNOWN | — |
| 047 | Super Quick Starter | [1.4.1][g141] | — | UNKNOWN | — |
| 048 | Super Slow Starter | [1.4.1][g141] | — | UNKNOWN | — |
| 049 | Speed Machine Kit | [Released screen][g-cost120], Machine Type Kit row | — | 3 / [screen][g-cost120] | [screen][g-cost120] → G/speed-machine-kit.png |
| 050 | Drift Charge Kit | [Released screen][g-cost120], fully visible name above Machine Type Kit | — | UNKNOWN | —; icon above screen crop |
| 051 | Spin Dash Kit | [Classic Sonic Festival][classic], June 26–29 UTC+8 | — | UNKNOWN | [reward][g-classic-art] → G/spin-dash-kit.png |

Speed/Acceleration/Handling Machine Kit captions in the cost screen are clipped after the type;
the visible Machine Type Kit heading identifies the concept, and the latter two names are also
spelled out in patch prose. Drift Charge Kit's visible name has no visible corresponding cost.
No truncated “Item Hoarder …” or “Damage S…” entry was completed by guessing its name.

### Artwork and unresolved coverage

The first 49 PNGs are obtained from the named first-party assets above. Retail manual icons are alpha-trimmed;
Features icons retain their original backgrounds. Patch/Festival icons are rectangular crops with
their background retained, not redrawn, masked into a new design, or AI-generated. Images are bounded
to 512×512 without upscaling and preserve aspect ratio/transparency. Coordinates use original pixels:

- 1.4.1/1.3.1 grids (1280×720): x = 132 + column×258, y = 141 + row×264, width 242, height 207.
- 1.2.0 grid (1920×1080): x = 350/775/1202, y = 205/612, width 365, height 302.
- Festival crops: x = 0.8–0.91 of width, y = 0.297–0.445 of height, rounded to pixels.
  Sea Dog Kit and All-Rounder Kit start at 0.281 of height to preserve the entire icon.
- Machine screen (2560×1440): Speed (1695,353)–(1905,517); Acceleration (1975,353)–(2185,517).

The same first-party sources were checked for each remaining image; none supplied an unambiguous current
isolated icon or usable named crop for those rows. The project owner subsequently approved using the
community-hosted artwork in [the Sonic Wiki gadget catalog][g-fandom] and
[machine catalog][m-fandom]. V8 stores 22 gadget icons and five machine icons locally from those
pages. This exception supplies presentation artwork only: it does not establish release status,
names, effects, costs, or compatibility rules. CNT sprites were not substituted for unverified retail art.
V8 gadget paths are Inventory Swap; Attack, Defense, Boost and Hazard Item Chance UP; Ultimate
Charge; Quick Recovery; Wisp Chance UP; Invincible Start; Handling and Power Machine Kit; Spin
Drift; Crash Pads; Warp Ring Specialist; Item Keeper; Perfect Charge Boost; Friction Drift; Quick,
Slow, Super Quick and Super Slow Starter; and Drift Charge Kit. The machine paths are Dark Reaper,
Jumble Rage, Neo Lightron, Road Dragoon and TYPE-S Stream. Their exact local paths are declared in
the migration, so the database remains the single mapping source.
The four retained Chance UP seed entries, the remainder of the named launch roster, and the following
CNT labels not independently inserted under those names still need retail corroboration:
Add Warp Ring, Speedy Air Trick, Technical Drift, Perfect Drift Boost, Lv1 Quick Charge,
Switching Quick Charge, Slipstream Enhancement, Ring Boost Effect UP, Low Rings Top Speed UP,
Double Rings, Ring Limit UP, Ring Steal, Ring Guard, Dash Panel Bonus, Runoff Bonus,
Ring Range UP and Item Attack Ring Bonus. Some may be renamed retail gadgets; this is not evidence
of seventeen additional distinct identities.
“Quick Charge” on Features is ambiguous about tier; it is not inserted as a potentially duplicate
generic entry. Add Warp Ring / Perfect Drift Boost / Slipstream Enhancement in CNT are historical
labels, not automatic extra retail rows. Unnamed tuner families in 1.4.1 do not justify inventing
individual tuner names. Full effect bundles, triggering conditions and most costs remain unknown.

No **specifically named announced-but-unreleased gadget** was found in the checked sources through
the cutoff. Future live-service data and future DLC announcements do not establish gadget identities.
Festival availability ending is not removal of an already released gadget. Amigo's kit is included
because its event had already begun, not merely because the announcement existed.

## GADGET PATCH / BALANCE HISTORY

This is an evidence ledger, not executable rules. Dates follow the linked Asian notices (Steam can
be one calendar day earlier); existing GameVersion rows are not edited. Unknown magnitudes remain
unknown. Current descriptions deliberately omit incomplete effect bundles and disputed changes.

| Update | Gadget | Old → new value / behavior | Official evidence / qualification |
| --- | --- | --- | --- |
| 1.1.2, 2025-09-22 | Warp Ring Specialist | ONT behavior → restricted in World Match/Festival | [Notice][g112]; Warp Ring bug. |
| 1.2.0, 2025-12-04 | Quick Recovery; Acceleration Machine Kit | Recovery time shortened; amounts unspecified | [Notice][g120]. |
| 1.2.0 | Wisp Chance UP | Boosted item drop rates reduced; amounts unspecified | [Notice][g120]. |
| 1.2.0 | Invincible Start | Adds flashing before expiry | [Notice][g120]; visual cue, not a verified duration change. |
| 1.2.0 | Ultimate Charge | Network synchronization improved for invincibility | [Notice][g120]; no cost change stated. |
| 1.2.0 | Boost/Power/Handling Character Kit; Comeback/Sea Dog Kit | Festival rewards → also purchasable using Donpa Tickets | [Notice][g120]. |
| 1.3.1, 2026-03-19 | Collision Boost; Power Machine Kit; Power Character Kit | Boost duration increased, magnitude unspecified | [Steam][g-steam131]. **Conflict:** [Asian page][g131] instead labels this bullet Ace Pilot/All-Rounder/Wisp Hoarder Kit while explaining Collision Boost. No disputed bundle change enters descriptions. |
| 1.3.1 | Crash Pads | Ring Thief hits now trigger ring-loss halving | [Notice][g131]; exact full gadget effect not supplied. |
| 1.3.1 | Ultimate Charge; Handling Machine Kit | Level-4 invincibility: 3 s → 1.5 s | [Notice][g131], [Steam][g-steam131]. |
| 1.3.1 | Acceleration Character Kit | Mini Ring Thief removed from kit | [Notice][g131]. |
| 1.3.1 | Inventory Swap | Button grays out when swapping unavailable | [Notice][g131]. |
| 1.3.1 | Warp Ring Specialist | Shortcut bug fixed; still prohibited in World Match/Festival/Legend Competition | [Notice][g131]; usable against CPUs and in Friend Matches. |
| 1.3.1 | Acceleration Character Kit; Ace Pilot/All-Rounder/Wisp Hoarder Kit | Festival rewards → also Donpa Ticket purchase | [Notice][g131]. |
| 1.4.1, 2026-06-24 | Crash Pads | **2 slots → 1 slot** | [Notice][g141], [Steam][g-steam141]; V7 stores 1. |
| 1.4.1 | Hyper Ring Engine alone | Missing speed bonus fixed → +4 km/h | [Steam][g-steam141]; Time Trial excluded. |
| 1.4.1 | Ring Engine + Hyper Ring Engine | Combined bonus +11 → +9 km/h; excessive low-Ring stat gain fixed | [Steam][g-steam141]; Time Trial excluded. |
| 1.4.1 | Both Ring Engines | Festival restriction lifted | [Steam][g-steam141]; introduction date of restriction not established. |
| 1.4.1 | Ring Engine; Less is More | Non-Time-Trial starting Rings standardized to 20 | [Notice][g141]; race-rule interaction, not gadget cost. |
| 1.4.1 | Item Keeper | Protects against Dark Chao; only empty item slots rerolled; prevents red Warp Ring item loss | [Steam][g-steam141]; Boost Frenzy excluded. Asian wording instead says locked-on attacks; no broader immunity inferred. |
| 1.4.1 | Spin Drift; Drift Spinner Kit | Knockback increased, especially at low Power | [Notice][g141]. |
| 1.4.1 | Perfect Charge Boost | Perfect-charge boost lasts slightly longer | [Notice][g141]; no exact duration. |
| 1.4.1 | Friction Drift | Unintended non-drifting slowdown fixed | [Notice][g141]. |
| 1.4.1 | Quick Starter; Slow Starter | Stat bonus +10 → +20 | [Notice][g141]. |
| 1.4.1 | Super Quick Starter; Super Slow Starter | Stat bonus +20 → +60 | [Notice][g141]. |
| 1.4.1 | Tuner family (individual names unspecified) | Nonmatching +4 → +8; matching +10 → +20; penalties unchanged | [Notice][g141]. |
| 1.4.1 | Machine Kit family | Matching bonus stays +20; −16 penalty removed | [Notice][g141]. |
| 1.4.1 | Ace Pilot Kit; Sea Dog Kit | Flight/water bonuses respectively +10 → +20 | [Notice][g141]. |
| 1.4.1 | Panel Combo Kit | Power/Handling bonuses +4 → +8 | [Notice][g141]. |
| 1.4.1 | Speed Character Kit | Incorrect “Speed Character Kit 2” label fixed in some languages | [Notice][g141]; effects unchanged. |
| 1.4.1 | Speed Character/Item Buster/Drift Spinner/Panel Combo Kit | Festival rewards → also Donpa Ticket purchase | [Notice][g141]. |

The 1.2.2, Xbox 1.3.2 and Steam/Epic 1.3.2 notices were checked; they specify no additional gadget
cost/effect changes. No later gadget balance notice appeared in either official feed or the publisher
Steam feed by the cutoff. This does not prove absence of undocumented changes. Nine current costs
are the latest explicit evidence found, not a guarantee of a fully verified rule system.

### Gadget source index

[g-features]: https://asia.sega.com/SonicRacingCrossWorlds/en/about.html
[r-fandom]: https://sonic.fandom.com/wiki/Characters_in_Sonic_Racing:_CrossWorlds
[g-fandom]: https://sonic.fandom.com/wiki/Gadget_(Sonic_Racing:_CrossWorlds)
[m-fandom]: https://sonic.fandom.com/wiki/Machines_in_Sonic_Racing:_CrossWorlds
[g-manual]: https://manual.sega.jp/sonicracingcrossworlds/en/index.html
[g-cnt]: https://manual.sega.jp/sonicracingcrossworlds/cnt/en/index.html?pid=4
[g112]: https://asia.sega.com/SonicRacingCrossWorlds/en/update/v-1-1-2.html
[g120]: https://asia.sega.com/SonicRacingCrossWorlds/en/update/v-1-2-0.html
[g131]: https://asia.sega.com/SonicRacingCrossWorlds/en/update/v-1-3-1.html
[g141]: https://asia.sega.com/SonicRacingCrossWorlds/en/update/v-1-4-1.html
[g-steam131]: https://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/1827626365751701
[g-steam141]: https://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/1836506165544896
[g-art120]: https://asia.sega.com/SonicRacingCrossWorlds/assets/images/en/update/update120/update02.webp
[g-cost120]: https://asia.sega.com/SonicRacingCrossWorlds/assets/images/en/update/update120/update03.webp
[g-art131]: https://asia.sega.com/SonicRacingCrossWorlds/assets/images/en/update/update131/add_gudget01.webp
[g-art141]: https://asia.sega.com/SonicRacingCrossWorlds/assets/images/en/update/update141/gadget01.webp
[g-stock-art]: https://asia.sega.com/SonicRacingCrossWorlds/assets/images/common/about/gadgetimg01.png
[g-punch-art]: https://asia.sega.com/SonicRacingCrossWorlds/assets/images/common/about/gadgetimg03.png
[g-manual1]: https://manual.sega.jp/sonicracingcrossworlds/en/rsc/img/gadget_icons/Gadget_Icon01.webp
[g-manual2]: https://manual.sega.jp/sonicracingcrossworlds/en/rsc/img/gadget_icons/Gadget_Icon02.webp
[g-manual3]: https://manual.sega.jp/sonicracingcrossworlds/en/rsc/img/gadget_icons/Gadget_Icon03.webp
[g-manual4]: https://manual.sega.jp/sonicracingcrossworlds/en/rsc/img/gadget_icons/Gadget_Icon04.webp
[g-manual5]: https://manual.sega.jp/sonicracingcrossworlds/en/rsc/img/gadget_icons/Gadget_Icon05.webp
[g-manual6]: https://manual.sega.jp/sonicracingcrossworlds/en/rsc/img/gadget_icons/Gadget_Icon06.webp
[g-miku-art]: https://asia.sega.com/SonicRacingCrossWorlds/assets/images/en/news/250922miku/miku03.webp
[g-joker-art]: https://asia.sega.com/SonicRacingCrossWorlds/assets/images/en/news/251021p5/img-p5-03.webp
[g-ichiban-art]: https://asia.sega.com/SonicRacingCrossWorlds/assets/images/en/news/251106ryu/img-ryu-03.webp
[g-nights-art]: https://asia.sega.com/SonicRacingCrossWorlds/assets/images/en/news/251224nights/img03.webp
[g-aiai-art]: https://asia.sega.com/SonicRacingCrossWorlds/assets/images/en/news/260212aiai/img03.webp
[g-tangle]: https://asia.sega.com/SonicRacingCrossWorlds/en/news/20260225available.html
[g-tangle-art]: https://asia.sega.com/SonicRacingCrossWorlds/assets/images/en/news/260225available/img03.webp
[g-red]: https://asia.sega.com/SonicRacingCrossWorlds/en/news/20260415red.html
[g-red-art]: https://asia.sega.com/SonicRacingCrossWorlds/assets/images/en/news/260415red/img03.webp
[g-arle]: https://asia.sega.com/SonicRacingCrossWorlds/en/news/20260527arle.html
[g-arle-art]: https://asia.sega.com/SonicRacingCrossWorlds/assets/images/en/news/260527arle/img03.webp
[g-classic-art]: https://asia.sega.com/SonicRacingCrossWorlds/assets/images/en/news/classicsonic/img03.webp
[g-minecraft]: https://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/1813041031190413
[g-minecraft-art]: https://clan.akamai.steamstatic.com/images/45493126/0a61157b9889bccf1f346dc9cb64f861c44b3b0f.png
[g-sponge]: https://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/1816849002009315
[g-sponge-art]: https://clan.akamai.steamstatic.com/images/45493126/e5609ab44b6ac46dc53b84ddf1e8a848f501a2fa.png
[g-pac]: https://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/1821288646577788
[g-pac-art]: https://clan.akamai.steamstatic.com/images/45493126/4e808fec09fbe782b990d2bc27ce6e169fed6f83.png
[g-mega]: https://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/1827626365769753
[g-mega-art]: https://clan.akamai.steamstatic.com/images/45493126/15d3c48e7909eb85e322989111a1064fa8eccbab.png
[g-majima-art]: https://clan.akamai.steamstatic.com/images/45493126/5f38e077f56b469210d1d7a4c6377ce8100147b1.png
[g-tmnt]: https://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/1839676055881252
[g-tmnt-art]: https://clan.akamai.steamstatic.com/images/45493126/1eae82f37783f5acbf01fb7a8c636187d73935ab.png
[g-amigo-art]: https://clan.akamai.steamstatic.com/images/45493126/671c714b58ed6b8928f823b40042003c9639ff0c.png
[g-axel-art]: https://clan.akamai.steamstatic.com/images/45493126/f43cf66cbbae5c6c824ff9f777567ff06fd0341f.png

## Remaining evidence work

- Obtain an exhaustive, named first-party inventory of the 45 original vehicles, reconcile it with
  the ten existing V2 names, and verify the canonical name of the Werehog bonus machine. The
  [official launch description][store] and [purchase guide][guide] establish scope but do not supply
  those individual names. Do not guess or copy a fan list into a production migration.
- Obtain isolated official artwork for Blinky. Its fallback behavior is intentional. Promotional screenshots for the three most recent free
  machines are usable official assets but can be replaced by isolated renders in a later migration.
- Verify racing types from a first-party source before replacing any new null value. Do not infer
  types from character appearance, vehicle shape, franchise, or the promotional background color.

## V7 gadget verification handoff

On 2026-09-12, the explicitly requested focused verification passed:

- `GadgetCatalogIntegrationTest`, `GadgetCatalogMigrationIntegrationTest` and
  `GameDataRepositoryIntegrationTest`: four tests, zero failures/errors, using a separate disposable
  PostgreSQL database. The migration test compares V6→V7 with a fresh V7 installation and proves
  existing references, ordering and community rows are preserved. Create/read/edit also preserves
  order for old/new gadget IDs even when known costs exceed six slots.
- `npm test -- --run src/pages/GameData.test.tsx`: one test passed, including image, description,
  singular/plural costs and null metadata.
- `npm run build`: TypeScript and Vite production build passed.
- All 49 new PNGs decode, all populated gadget image paths exist locally, and source-index links
  and asset provenance entries were checked. `git diff --check` passed.

The existing PostgreSQL and Vite services were reused; Quarkus was restarted and applied V7.
Through `http://localhost:5173/api/gadgets`, the live catalog returns 71 rows, 49 images,
10 descriptions and 9 costs. Before/after whole-row hashes match for users (61), builds (20),
build_gadgets (51), votes (304), comments (30), machine_parts (81) and game_versions (4).
No demo reseeding was performed. The browser renders the collection's icons, known costs and
null fallbacks, existing demo cards and Steam news. Ranked browsing and the patch-filter endpoint
respond through the frontend proxy. Build ordering was exercised by the focused integration test.

To repeat the focused backend checks, use IntelliJ's gutter on the three classes above or run
from `backend`, against an existing **disposable test database**:

```powershell
mvn "-Dtest=GadgetCatalogIntegrationTest,GadgetCatalogMigrationIntegrationTest,GameDataRepositoryIntegrationTest" "-Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/ringlab_gadget_catalog_test" "-Dquarkus.datasource.username=ringlab" "-Dquarkus.datasource.password=ringlab" "-Dquarkus.datasource.devservices.enabled=false" test
```

The agent-created test database was dropped after the successful run; recreate a disposable test
database before repeating that command. The complete acceptance/integration suites, packaged tests
and JaCoCo analysis were not run. `BuildService` was unchanged. For optional broader manual API
coverage, run `AcceptanceTest` in IntelliJ against the documented disposable test database.
Frontend commands above run from `frontend`.

## V8 community-artwork verification handoff

On 2026-09-12, `GadgetCatalogIntegrationTest` (two tests) and
`GadgetCatalogMigrationIntegrationTest` (one V6→V8/fresh-V8 test) passed with zero failures on a
separate disposable PostgreSQL database, which was dropped afterwards. The checks assert 71 gadget
paths, 27 machine paths, local-file existence, API mapping, and preservation of IDs, ordered build
gadgets, user/build/vote/comment data, machine names and racing types. All 98 gadget/machine PNGs
decoded successfully. After a local Quarkus restart applied V8, the frontend proxy returned 71/71
gadget and 27/27 machine image paths; the browser rendered the Gadget and Stock Machines tabs.

No frontend source changed for V8, so the prior frontend rendering test/build were not repeated.
The complete backend suite, acceptance tests, packaged checks and JaCoCo analysis remain manual
follow-up work under the narrow-verification policy.

## V9 unified wiki-artwork verification handoff

On 2026-09-12, the focused catalog tests passed with two `GadgetCatalogIntegrationTest` tests and
one V6→V9/fresh-V9 `GadgetCatalogMigrationIntegrationTest`, all against a disposable PostgreSQL
database. All 151 racer/machine/gadget PNGs decoded successfully. After the local backend restart
applied V9, the frontend proxy returned image paths for 53/53 racers, 27/27 machines and 71/71
gadgets; the Game Collection rendered each catalog tab, including Blinky.

## V6 verification handoff (previous catalog work)

Pure response-conversion tests cover null types and preservation of all five known type values.
`ReleasedCatalogMigrationIntegrationTest` covers upgrade/fresh catalog invariants and existing
references; it requires the real disposable PostgreSQL test database and is provided for manual
execution. It is not a pure unit test and is not part of the agent's default test run.

Agent verification on 2026-09-12: `GameDataResponseTest` passed (2 tests), and
`src/components.test.tsx` passed (3 tests). All 74 PNG files decoded successfully and every concrete
migration asset path exists locally and appears in this ledger. The existing local PostgreSQL and
Vite services were reused; Quarkus was restarted and applied V6 successfully. Through Vite's `/api`
proxy the live catalog returned 53 racers, 27 machines and 81 parts, with three distinct part types
for every machine. The browser rendered both collections and Unknown labels successfully.
Integration/acceptance suites, packaged verification, a full frontend build and JaCoCo analysis
were intentionally not run under the project's narrow-verification policy.

From `backend`, run the new unit test with `mvn -Dtest=GameDataResponseTest test`.
For manual database verification use IntelliJ's gutter on `ReleasedCatalogMigrationIntegrationTest`
or `mvn -Dtest=ReleasedCatalogMigrationIntegrationTest test` with the documented test database
configuration. The integration test uses an isolated schema. Frontend null-type/fallback checks
can be run from `frontend` with `npm test -- --run src/components.test.tsx`.
The existing catalog boundary tests were also updated for the larger inventory; run
`mvn "-Dtest=GameDataRepositoryIntegrationTest,AcceptanceTest" test` manually against the disposable
test database. From `frontend`, `npm run build` remains the manual TypeScript/production build check.

## Official source index

[roster]: https://asia.sega.com/SonicRacingCrossWorlds/en/racers.html
[machines]: https://asia.sega.com/SonicRacingCrossWorlds/en/machines.html
[guide]: https://asia.sega.com/SonicRacingCrossWorlds/en/guide.html
[store]: https://store.steampowered.com/app/2486820/
[launch]: https://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/1811772772248801
[launch-press]: https://sega.prezly.com/sonic-racing-crossworlds--now-available
[miku]: https://asia.sega.com/SonicRacingCrossWorlds/en/news/20250922miku.html
[joker]: https://asia.sega.com/SonicRacingCrossWorlds/en/news/20251021p5.html
[ichiban]: https://asia.sega.com/SonicRacingCrossWorlds/en/news/20251106ryu.html
[nights]: https://asia.sega.com/SonicRacingCrossWorlds/en/news/20251224nights.html
[aiai]: https://asia.sega.com/SonicRacingCrossWorlds/en/news/20260212aiai.html
[tangle]: https://sega.prezly.com/sonic-racing-crossworlds-free-tangle--whisper-content-drop-officially-available
[red]: https://www.sega.com/news/red-is-now-available
[majima]: https://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/1831432155566538
[arle]: https://www.sega.com/news/arle-is-now-available
[classic]: https://asia.sega.com/SonicRacingCrossWorlds/en/news/classicsonic.html
[classic-patch]: https://asia.sega.com/SonicRacingCrossWorlds/en/update/v-1-4-1.html
[axel]: https://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/1842212951298098
[amigo]: https://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/1843481262687681
[amigo-asia]: https://sonicracing.sega.com/en/details/000118wbfzajpy.html
[minecraft]: https://store.steampowered.com/app/3565190/
[sponge]: https://store.steampowered.com/app/3601430/
[pac-store]: https://store.steampowered.com/app/3601440/
[pac]: https://asia.sega.com/SonicRacingCrossWorlds/en/news/20260106pacman.html
[mega]: https://store.steampowered.com/app/3601480/
[tmnt]: https://store.steampowered.com/app/3601470/
[blue-store]: https://store.steampowered.com/app/3601510/
[blue]: https://asia.sega.com/SonicRacingCrossWorlds/en/news/20260113freedlc.html
[season]: https://store.steampowered.com/app/3592250/
[season2]: https://asia.sega.com/SonicRacingCrossWorlds/en/news/season2pass.html

Roster data loaded by the official page was also checked directly:
[main](https://asia.sega.com/SonicRacingCrossWorlds/assets/data/racersMain.json),
[free updates](https://asia.sega.com/SonicRacingCrossWorlds/assets/data/racersUpdate.json),
[collaborations](https://asia.sega.com/SonicRacingCrossWorlds/assets/data/racersCollabo.json),
[Sonic Prime](https://asia.sega.com/SonicRacingCrossWorlds/assets/data/racersPrime.json),
[bonus](https://asia.sega.com/SonicRacingCrossWorlds/assets/data/racersBonus.json).
Recency cross-checks: [new official news feed](https://sonicracing.sega.com/en/data/news.json),
[older official news feed](https://sonic.sega.jp/SonicRacingCrossWorlds/assets/data/en/news.json),
[publisher Steam news](https://store.steampowered.com/news/app/2486820).
