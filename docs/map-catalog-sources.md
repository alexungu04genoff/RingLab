# Recommended maps source ledger

Checked **2026-09-27**, using the owner-approved Sonic Wiki catalog/artwork policy.
The inventory verified before import is 24 base main courses, 15 CrossWorld destinations,
and 5 released collaboration main courses. These are observed counts, not application limits.

`MAIN_COURSE` and `CROSSWORLD` describe course type. A separate nullable `contentPack`
records collaboration membership; DLC is not a third gameplay course type. Magma Planet
is a base CrossWorld despite its Galaxy Force inspiration. Categories describe the catalog,
not restrictions on modes in which a course can be raced.

Every row's source page is the [CrossWorlds racetrack table][wiki], cross-checked with
[SEGA's course presentation][courses]. IDs below are the final three digits of
`a0000000-0000-4000-8000-000000000NNN`; they are assigned identities, independent of names.
Artwork paths in the Source column resolve under
`https://static.wikia.nocookie.net/sonic/images/` (append `/revision/latest`). They were read
from the CrossWorlds table's image links, not inferred from older games. Local files resolve
under `frontend/public/assets/maps/`. Images are served locally with their aspect ratio intact.

| ID | Canonical name | Course type | Content pack / release evidence | Image source | Local file |
| --- | --- | --- | --- | --- | --- |
| 001 | E-Stadium | MAIN_COURSE | Base / [launch] | a/a3/SRC_UI_StageBG1016_EStadium.png | e-stadium.png |
| 002 | Rainbow Garden | MAIN_COURSE | Base / [launch] | a/a7/SRC_UI_StageBG1017_RainbowGarden.png | rainbow-garden.png |
| 003 | Water Palace | MAIN_COURSE | Base / [launch] | 2/2e/SRC_UI_StageBG1018_WaterPalace.png | water-palace.png |
| 004 | Metal Harbor | MAIN_COURSE | Base / [launch] | b/bd/SRC_UI_StageBG1022_MetalHarbor.png | metal-harbor.png |
| 005 | Sand Road | MAIN_COURSE | Base / [launch] | 9/95/SRC_UI_StageBG1003_SandRoad.png | sand-road.png |
| 006 | Colorful Mall | MAIN_COURSE | Base / [launch] | d/d5/SRC_UI_StageBG1030_ColorfulMall.png | colorful-mall.png |
| 007 | Mystic Jungle | MAIN_COURSE | Base / [launch] | 1/1d/SRC_UI_StageBG1025_MysticJungle.png | mystic-jungle.png |
| 008 | Apotos | MAIN_COURSE | Base / [launch] | 3/35/SRC_UI_StageBG1032_Apotos.png | apotos.png |
| 009 | Wonder Museum | MAIN_COURSE | Base / [launch] | e/e7/SRC_UI_StageBG1024_WonderMuseum.png | wonder-museum.png |
| 010 | Crystal Mine | MAIN_COURSE | Base / [launch] | 7/7f/SRC_UI_StageBG1029_CrystalMine.png | crystal-mine.png |
| 011 | Ocean View | MAIN_COURSE | Base / [launch] | 5/51/SRC_UI_StageBG1001_OceanView.png | ocean-view.png |
| 012 | Pumpkin Mansion | MAIN_COURSE | Base / [launch] | 6/6b/SRC_UI_StageBG1033_PumpkinMansion.png | pumpkin-mansion.png |
| 013 | Urban Canyon | MAIN_COURSE | Base / [launch] | 6/62/SRC_UI_StageBG1034_UrbanCanyon.png | urban-canyon.png |
| 014 | Market Street | MAIN_COURSE | Base / [launch] | 7/70/SRC_UI_StageBG1005_MarketStreet.png | market-street.png |
| 015 | Coral Town | MAIN_COURSE | Base / [launch] | 9/9c/SRC_UI_StageBG1027_CoralTown.png | coral-town.png |
| 016 | Blizzard Valley | MAIN_COURSE | Base / [launch] | c/cf/SRC_UI_StageBG1023_BlizzardValley.png | blizzard-valley.png |
| 017 | Radical Highway | MAIN_COURSE | Base / [launch] | c/c8/SRC_UI_StageBG1021_RadicalHighway.png | radical-highway.png |
| 018 | Chao Park | MAIN_COURSE | Base / [launch] | e/ef/SRC_UI_StageBG1020_ChaoPark.png | chao-park.png |
| 019 | Donpa Factory | MAIN_COURSE | Base / [launch] | 8/8d/SRC_UI_StageBG1026_DonpaFactory.png | donpa-factory.png |
| 020 | Aqua Forest | MAIN_COURSE | Base / [launch] | a/a4/SRC_UI_StageBG1028_AquaForest.png | aqua-forest.png |
| 021 | Eggman Expo | MAIN_COURSE | Base / [launch] | b/b7/SRC_UI_StageBG1031_EggmanExpo.png | eggman-expo.png |
| 022 | Kronos Island | MAIN_COURSE | Base / [launch] | 9/99/SRC_UI_StageBG1035_KronosIsland.png | kronos-island.png |
| 023 | Northstar Islands | MAIN_COURSE | Base / [launch] | 8/8c/SRC_UI_StageBG1036_NorthstarIslands.png | northstar-islands.png |
| 024 | White Space | MAIN_COURSE | Base / [launch] | 7/7e/SRC_UI_StageBG1037_WhiteSpace.png | white-space.png |
| 025 | Minecraft World | MAIN_COURSE | Minecraft Pack / [minecraft] | c/c0/SRC_UI_StageBG1503_MinecraftWorld.png | minecraft-world.png |
| 026 | BiKiNi BOTTOM | MAIN_COURSE | SpongeBob SquarePants Pack / [spongebob] | 0/09/SRC_UI_StageBG1506_BikiniBottom.png | bikini-bottom.png |
| 027 | PAC-Village & Maze | MAIN_COURSE | PAC-MAN Pack / [pacman] | 5/5e/UI_StageBG1502_Extnd06.png | pac-village-and-maze.png |
| 028 | Wily Castle | MAIN_COURSE | Mega Man Pack / [megaman] | 7/74/UI_StageBG1501_Extnd09.png | wily-castle.png |
| 029 | New York City | MAIN_COURSE | Teenage Mutant Ninja Turtles Pack / [tmnt] | 9/92/UI_StageBG1505_Extnd08.png | new-york-city.png |
| 030 | Sky Road | CROSSWORLD | Base / [launch] | 4/4c/SRC_UI_StageBG2001_SkyRoad.png | sky-road.png |
| 031 | Roulette Road | CROSSWORLD | Base / [launch] | 1/17/SRC_UI_StageBG2002_RouletteRoad.png | roulette-road.png |
| 032 | Kraken Bay | CROSSWORLD | Base / [launch] | 7/73/SRC_UI_StageBG2003_KrakenBay.png | kraken-bay.png |
| 033 | Golden Temple | CROSSWORLD | Base / [launch] | 9/93/SRC_UI_StageBG2004_GoldTemple.png | golden-temple.png |
| 034 | Magma Planet | CROSSWORLD | Base / [launch] | 8/80/SRC_UI_StageBG2005_MagmaPlanet.png | magma-planet.png |
| 035 | Hidden World | CROSSWORLD | Base / [launch] | c/ce/SRC_UI_StageBG2007_HiddenWorld.png | hidden-world.png |
| 036 | Steampunk City | CROSSWORLD | Base / [launch] | 7/79/SRC_UI_StageBG2009_SteampunkCity.png | steampunk-city.png |
| 037 | Dragon Road | CROSSWORLD | Base / [launch] | 6/67/SRC_UI_StageBG2010_DragonRoad.png | dragon-road.png |
| 038 | Holoska | CROSSWORLD | Base / [launch] | 0/05/SRC_UI_StageBG2011_Holoska.png | holoska.png |
| 039 | Galactic Parade | CROSSWORLD | Base / [launch] | 7/74/SRC_UI_StageBG2012_GalacticParade.png | galactic-parade.png |
| 040 | Dinosaur Jungle | CROSSWORLD | Base / [launch] | 8/85/SRC_UI_StageBG2014_DinosaurJungle.png | dinosaur-jungle.png |
| 041 | Sweet Mountain | CROSSWORLD | Base / [launch] | 8/82/SRC_UI_StageBG2015_SweetMountain.png | sweet-mountain.png |
| 042 | White Cave | CROSSWORLD | Base / [launch] | 3/3b/SRC_UI_StageBG2016_WhiteCave.png | white-cave.png |
| 043 | Cyber Space | CROSSWORLD | Base / [launch] | a/a6/SRC_UI_StageBG2017_Cyberspace.png | cyber-space.png |
| 044 | Digital Circuit | CROSSWORLD | Base / [launch] | e/e4/SRC_UI_StageBG2019_DigitalCircuit.png | digital-circuit.png |

## Evidence and exclusions

Base identities/categories come from the Wiki racetrack table; SEGA's launch notice establishes
release. Minecraft's official notice is dated October 9, 2025. PAC-MAN's January 6 notice
explicitly says available January 8, 2026. Mega Man's official April 2, 2026 video says the pack
is out and names Wily Castle. SpongeBob and TMNT have explicit SEGA "released" notices naming
their courses. No release date is inferred from an image upload or the availability of a pass.

**Excluded pending release evidence:** Lion Turtle Island (Avatar Legends; publisher's Steam
listing says October 2026), Godzilla Escape and Tokyo-III (Wiki marks upcoming Season Pass 2).
No other candidate in the focused racetrack table remains unclassified. Cups, menus, modes,
and the Time Trial entry/exit oval are excluded. No inferred patch availability is stored.

Artwork download verification is recorded in the feature handoff. A missing/broken image uses
a labelled fallback and does not affect selection validity. Original artwork remains copyright
SEGA and the respective collaborators (Mojang, Viacom/Nickelodeon, Bandai Namco, CAPCOM).
Wiki contributors are credited through the existing footer; Wiki text is CC-BY-SA unless noted,
which does not relicense game artwork. Only names, classification facts and image references
are imported, not article descriptions.

[wiki]: https://sonic.fandom.com/wiki/Sonic_Racing:_CrossWorlds#Racetracks
[courses]: https://asia.sega.com/SonicRacingCrossWorlds/en/courses.html
[launch]: https://sega.prezly.com/sonic-racing-crossworlds--now-available
[minecraft]: https://asia.sega.com/SonicRacingCrossWorlds/en/news/20251009minecraft.html
[spongebob]: https://sega.prezly.com/sonic-racing-crossworlds-spongebob-squarepants-pack-officially-available
[pacman]: https://asia.sega.com/SonicRacingCrossWorlds/en/news/20260106pacman.html
[megaman]: https://www.youtube.com/watch?v=Yu7-XwOp198
[tmnt]: https://sega.prezly.com/sonic-racing-crossworlds-teenage-mutant-ninja-turtles-pack-available-now
