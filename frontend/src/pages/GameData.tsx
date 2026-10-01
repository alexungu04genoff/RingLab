import { useState } from "react";
import { CollectionStatus, OwnershipCheckbox, useCollection } from "../Collection";
import { Link } from "react-router-dom";
import type { ReactNode } from "react";
import { MapThumbnail } from "../MapRecommendations";
import { mapCategoryLabel } from "../mapSelection";
import type { RaceMap } from "../types";
import { CompassIcon } from "../icons";
import { Artwork, ErrorNotice, date } from "../components";
import { RacingTypeBadge } from "../RacingTypeBadge";
import { useLoad } from "../useLoad";
import { StatsBlock } from "../BaseStats";
import type { StatsCatalog } from "../types";
import type { Gadget, GameVersion, Machine, MachinePart, Racer } from "../types";
import { GearIcon, HistoryIcon, RacerIcon, SteeringWheelIcon } from "../icons";
import { StockMachineCard } from "../StockMachineCard";
import { GadgetRuleDetails } from "../PassiveStats";
import type { GadgetRulesCatalog } from "../types";
import type { ScenarioRulesCatalog } from "../types";
import { ScenarioBadge } from "../ScenarioBadge";

const collections = [
  { key: "racers", label: "Racers", Icon: RacerIcon },
  { key: "machines", label: "Stock Machines", Icon: SteeringWheelIcon },
  { key: "gadgets", label: "Gadgets", Icon: GearIcon },
  { key: "maps", label: "Maps", Icon: CompassIcon },
  { key: "game-versions", label: "Versions / Patches", Icon: HistoryIcon },
] as const;

type CollectionKey = (typeof collections)[number]["key"];
type CollectionItem = Racer | Machine | Gadget | GameVersion | RaceMap;

const patchNotesByVersion: Record<string, string> = {
  "1.4.1": "https://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/1836506165544896",
  "1.3.1": "https://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/1827626365751701",
  "1.2.2": "https://steamcommunity.com/games/2486820/announcements/detail/493837645658456833",
  "1.2.0": "https://steamcommunity.com/games/2486820/announcements/detail/493836377652200018",
};

export const patchNotesUrl = (version: string) => patchNotesByVersion[version] ?? null;

export function newestGameVersion(versions: GameVersion[] | undefined) {
  return versions?.reduce<GameVersion | undefined>((newest, version) =>
    !newest || version.releasedAt > newest.releasedAt ? version : newest, undefined);
}

function RacerCard({ racer, ownership }: { racer: Racer; ownership?: ReactNode }) {
  return (
    <article className="panel collection-item racer-collection-item">
      <Artwork item={racer} compact portrait />
      <div className="collection-copy">
        <h2>{racer.name}</h2>
        <RacingTypeBadge kind="racer" type={racer.racingType} className="collection-type" />
      </div>
      {ownership}
    </article>
  );
}

function GadgetCard({ gadget, rules, scenarios, ownership }: { gadget: Gadget; rules?: GadgetRulesCatalog; scenarios?: ScenarioRulesCatalog; ownership?: ReactNode }) {
  return (
    <article className="panel collection-item gadget-collection-item">
      <Artwork item={gadget} compact />
      <div className="collection-copy">
        <h2>{gadget.name}</h2>
        <div className="gadget-collection-badges">
          {gadget.slotCost !== null && (
            <span className="gadget-slot-cost">
              {gadget.slotCost} {gadget.slotCost === 1 ? "slot" : "slots"}
            </span>
          )}
          <ScenarioBadge gadgetId={gadget.id} catalog={scenarios} />
        </div>
      </div>
      {gadget.description && <p className="gadget-collection-description">{gadget.description}</p>}
      <GadgetRuleDetails id={gadget.id} catalog={rules} />
      {ownership}
    </article>
  );
}

function VersionCard({ gameVersion }: { gameVersion: GameVersion }) {
  const patchNotes = patchNotesUrl(gameVersion.version);
  return (
    <article className="panel version-collection-item">
      <span className="eyebrow">GAME VERSION</span>
      <h2>Ver. {gameVersion.version}</h2>
      <p>Released {date(`${gameVersion.releasedAt}T00:00:00`)}</p>
      {patchNotes && (
        <a href={patchNotes} target="_blank" rel="noreferrer">
          Read patch notes on Steam ↗
        </a>
      )}
    </article>
  );
}

function CollectionCard({ item, tab, machineParts, stats, version, loading, error, rules, scenarios, ownership }: {
  item: CollectionItem; tab: CollectionKey; machineParts: MachinePart[];
  stats: StatsCatalog | undefined; version: string | null; loading: boolean; error: string;
  rules?: GadgetRulesCatalog; scenarios?: ScenarioRulesCatalog; ownership?: ReactNode;
}) {
  if ("version" in item) return <VersionCard gameVersion={item} />;
  if ("category" in item) return <article className="panel collection-item map-collection-item">
    <MapThumbnail map={item} /><div className="collection-copy"><h2>{item.name}</h2>
    <p>{mapCategoryLabel(item.category)}{item.contentPack && ` · ${item.contentPack}`}</p>
    <Link to={`/?mapId=${encodeURIComponent(item.id)}&includeAllMaps=false`}>Find recommended builds →</Link></div>
  </article>;
  if ("slotCost" in item) return <GadgetCard gadget={item} rules={rules} scenarios={scenarios} ownership={ownership} />;
  if (tab === "machines") return <StockMachineCard machine={item}
    parts={machineParts.filter((part) => part.sourceMachineId === item.id)}
    catalog={stats} version={version} loading={loading} error={error} ownership={ownership} />;
  return <RacerCard racer={item} ownership={ownership} />;
}

export function GameData() {
  const collection = useCollection();
  const [tab, setTab] = useState<CollectionKey>("racers");
  const [search, setSearch] = useState("");
  const items = useLoad<CollectionItem[]>(`/${tab}`);
  const rules = useLoad<GadgetRulesCatalog>(tab === "gadgets" ? "/stats/gadget-rules" : "");
  const scenarios = useLoad<ScenarioRulesCatalog>(tab === "gadgets" ? "/stats/scenario-rules" : "");
  const versions = useLoad<GameVersion[]>("/game-versions");
  const machineParts = useLoad<MachinePart[]>("/machine-parts");
  const latestVersion = newestGameVersion(versions.data);
  const stats = useLoad<StatsCatalog>(latestVersion ? `/stats/catalog?gameVersionId=${latestVersion.id}` : "");
  const searchLabel = collections.find(collection => collection.key === tab)!.label.toLowerCase();
  const visibleItems = items.data?.filter(item => ("version" in item ? item.version : item.name)
    .toLocaleLowerCase().includes(search.trim().toLocaleLowerCase()));
  return (
    <>
      <div className="page-heading collection-heading">
        <div className="collection-heading-content">
          <div className="collection-title-copy">
          <div className="eyebrow accent">CROSSWORLDS COLLECTION</div>
          <h1>Know your setup.</h1>
            <p>Browse the pieces available for every RingLab build.</p>
          </div>
          <section className="collection-intro" aria-label="How a RingLab build works">
            <strong>How a build comes together</strong>
            <p>
              Choose one racer, front and rear parts, plus a tire for machines other than Boost / Extreme Gear.
              Add optional gadgets in the order you want them shown.
            </p>
          </section>
        </div>
      </div>
      <div className="tabs collection-tabs">
        {collections.map(({ key, label, Icon }) => (
          <button
            key={key}
            className={key === tab ? "selected" : ""}
            aria-pressed={key === tab}
            onClick={() => { setTab(key); setSearch(""); }}
          >
            <Icon /> {label}
          </button>
        ))}
      </div>
      {collection.status === "anonymous" && <aside className="collection-notice"><p><strong>Log in to manage your collection.</strong> Mark items you don't own to exclude them from recommendations. While logged out, everything is treated as available. <Link to="/guide#your-collection">Collection guide →</Link></p>
        <Link className="button" to="/login" state={{ from: "/game-data" }}>Log in</Link></aside>}
      {collection.status === "ready" && <aside className="collection-explanation"><p>Everything is owned by default. Uncheck items you don't own to exclude them from recommendations. <Link to="/guide#your-collection">Collection guide →</Link></p></aside>}
      <CollectionStatus />
      <ErrorNotice message={items.error} />
      <ErrorNotice message={versions.error || stats.error || machineParts.error} />
      {items.loading && <p role="status">Loading collection…</p>}
      <label className="collection-search">Search {searchLabel}
        <input type="search" value={search} onChange={event => setSearch(event.target.value)} /></label>
      {!items.loading && !items.error && visibleItems?.length === 0
        && <p>No {searchLabel} match your search. Try another name.</p>}
      <div className={`collection collection-${tab}`}>
        {visibleItems?.map((item) => <div key={item.id}>
          <CollectionCard item={item} tab={tab} machineParts={machineParts.data ?? []}
            stats={stats.data} version={latestVersion?.version ?? null} rules={rules.data} scenarios={scenarios.data}
            loading={versions.loading || machineParts.loading || stats.loading}
            error={versions.error || machineParts.error || stats.error}
            ownership={(tab === "racers" || tab === "machines" || tab === "gadgets") && "name" in item
              ? <OwnershipCheckbox category={tab === "racers" ? "RACER" : tab === "machines" ? "MACHINE" : "GADGET"}
                id={item.id} name={item.name} /> : undefined} />
          {"racingType" in item && <>
            {versions.loading || stats.loading ? <p role="status">Loading base stats…</p>
              : !versions.error && !stats.error && <StatsBlock
                stats={(tab === "machines" ? stats.data?.machines : stats.data?.racers)?.[item.id]}
                version={latestVersion?.version ?? null}
                title={tab === "machines" ? "Stock base stats (parts total)" : "Racer base stats"} />}
          </>}
        </div>)}
      </div>
    </>
  );
}
