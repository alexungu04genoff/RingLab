# Sonic Racing: CrossWorlds catalog ledger

Cutoff and date checked for **every row below: 2026-09-12** (Europe/Bucharest).
This ledger accompanies `V6__released_racer_and_machine_catalog.sql`. Earlier migrations are unchanged.

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
not treated as proof of additional stock machines. No community wiki or fan artwork is a source.
The western SEGA site required a normal browser visit; its Features tab also advertises 45 vehicles
without listing them individually. No guessed names or types were added to fill that gap.

Existing V2 IDs, names, types, V4 part IDs, and community data are preserved. Existing racing types
are inherited supplied data, **not newly independently verified** by this research. New racing
types are null because release/roster pages do not establish them. The user approved this nullable
contract; the UI shows Unknown. `RacingType` remains an enum with its existing five values.

## Source conventions

Each row supplies the RingLab canonical name, category, release status/evidence, official artwork
source, local path and ambiguity. All rows share the date checked above. Release dates are the
dates stated by the linked publication/store and can differ by one day across time zones; this
does not affect the September 12 cutoff. A pass being on sale is not proof that all its packs shipped.

Artwork source paths beginning **A/** resolve under the first-party asset root
<https://asia.sega.com/SonicRacingCrossWorlds/assets/images/common/>; paths beginning **E/** resolve
under <https://asia.sega.com/SonicRacingCrossWorlds/assets/images/en/>. These are exact source paths,
not local paths or inferred filenames. Other artwork sources are full links. Local paths are
served from `frontend/public`; none are remote hotlinks.

Transparent renders were preferred where the official news pages provide them. Otherwise official
framed portrait cards or promotional screenshots were retained. Normalization only trims empty
alpha bounds, preserves aspect ratio/transparency, converts to PNG and bounds dimensions to
640 x 640 without upscaling. The Pizzafire Van is a rectangular crop of its official pack artwork
(original coordinates 700,325–1200,720), retaining its decorative background. No AI artwork,
background synthesis, redraw or restyling was used. There are 52 racer images and 22 machine images.

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
| Dark Reaper | MAIN | Existing launch seed; [official machine page][machines] does not name it | No isolated asset obtained from machine page, western Features gallery or customization trailer | null | Supplied name/type retained; independent named first-party verification remains open. |
| Whirlwind Sport | MAIN | Existing launch seed; [official machine illustrations][machines] | A/machines/type02.png | /assets/machines/whirlwind-sport.png | Supplied name/type retained; visually matched acceleration illustration. |
| Jumble Rage | MAIN | Existing launch seed; [official machine page][machines] does not name it | No isolated asset obtained from machine page, western Features gallery or customization trailer | null | Supplied name/type retained; independent named first-party verification remains open. |
| Pink Cabriolet | MAIN | Existing launch seed; [official machine illustrations][machines] | A/machines/type03.png | /assets/machines/pink-cabriolet.png | Supplied name/type retained; visually matched handling illustration. |
| Neo Lightron | MAIN | Existing launch seed; [official machine page][machines] does not name it | No isolated asset obtained from machine page, western Features gallery or customization trailer | null | Supplied name/type retained; independent named first-party verification remains open. |
| Land Smasher | MAIN | Existing launch seed; [official machine illustrations][machines] | A/machines/type04.png | /assets/machines/land-smasher.png | Supplied name/type retained; visually matched power illustration. |
| Road Dragoon | MAIN | Existing launch seed; [official machine page][machines] does not name it | No isolated asset obtained from machine page, western Features gallery or customization trailer | null | Supplied name/type retained; independent named first-party verification remains open. |
| TYPE-J Iota | MAIN | Existing launch seed; [official machine illustrations][machines] | A/machines/type05.png | /assets/machines/type-j-iota.png | Supplied name/type retained; visually matched boost illustration. |
| TYPE-S Stream | MAIN | Existing launch seed; [official machine page][machines] does not name it | No isolated asset obtained from machine page, western Features gallery or customization trailer | null | Supplied name/type retained; independent named first-party verification remains open. |
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

## Remaining evidence work

- Obtain an exhaustive, named first-party inventory of the 45 original vehicles, reconcile it with
  the ten existing V2 names, and verify the canonical name of the Werehog bonus machine. The
  [official launch description][store] and [purchase guide][guide] establish scope but do not supply
  those individual names. Do not guess or copy a fan list into a production migration.
- Obtain isolated official artwork for Blinky and the five retained machines with null image paths.
  Their fallback behavior is intentional. Promotional screenshots for the three most recent free
  machines are usable official assets but can be replaced by isolated renders in a later migration.
- Verify racing types from a first-party source before replacing any new null value. Do not infer
  types from character appearance, vehicle shape, franchise, or the promotional background color.

## Verification handoff

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
