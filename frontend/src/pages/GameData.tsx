import { useState } from "react";
import { Artwork, ErrorNotice, date, racingTypeClass } from "../components";
import { useLoad } from "../useLoad";
import type { Gadget, GameVersion, Machine, Racer } from "../types";
import { GearIcon, HistoryIcon, RacerIcon, SteeringWheelIcon } from "../icons";

const collections = [
  { key: "racers", label: "Racers", Icon: RacerIcon },
  { key: "machines", label: "Stock Machines", Icon: SteeringWheelIcon },
  { key: "gadgets", label: "Gadgets", Icon: GearIcon },
  { key: "game-versions", label: "Versions / Patches", Icon: HistoryIcon },
] as const;

type CollectionKey = (typeof collections)[number]["key"];
type CollectionItem = Racer | Machine | Gadget | GameVersion;

const patchNotesByVersion: Record<string, string> = {
  "1.4.1": "https://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/1836506165544896",
  "1.3.1": "https://steamstore-a.akamaihd.net/news/externalpost/steam_community_announcements/1827626365751701",
  "1.2.2": "https://steamcommunity.com/games/2486820/announcements/detail/493837645658456833",
  "1.2.0": "https://steamcommunity.com/games/2486820/announcements/detail/493836377652200018",
};

export const patchNotesUrl = (version: string) => patchNotesByVersion[version] ?? null;

function RacingTypeBadge({ item }: { item: Racer | Machine }) {
  return (
    <span className={`collection-type racing-type ${racingTypeClass(item.racingType)}`}>
      {item.racingType ?? "Unknown"}
    </span>
  );
}

function RacerCard({ racer }: { racer: Racer }) {
  return (
    <article className="panel collection-item racer-collection-item">
      <Artwork item={racer} compact portrait />
      <div className="collection-copy">
        <h2>{racer.name}</h2>
        <RacingTypeBadge item={racer} />
      </div>
    </article>
  );
}

function MachineCard({ machine }: { machine: Machine }) {
  return (
    <article className="panel collection-item machine-collection-item">
      <Artwork item={machine} compact />
      <div className="collection-copy">
        <span className="eyebrow">STOCK MACHINE</span>
        <h2>{machine.name}</h2>
        <RacingTypeBadge item={machine} />
      </div>
    </article>
  );
}

function GadgetCard({ gadget }: { gadget: Gadget }) {
  return (
    <article className="panel collection-item gadget-collection-item">
      <Artwork item={gadget} compact />
      <div className="collection-copy">
        <div className="gadget-collection-heading">
          <h2>{gadget.name}</h2>
          {gadget.slotCost !== null && (
            <span className="gadget-slot-cost">
              {gadget.slotCost} {gadget.slotCost === 1 ? "slot" : "slots"}
            </span>
          )}
        </div>
        {gadget.description && <p>{gadget.description}</p>}
      </div>
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

function CollectionCard({ item, tab }: { item: CollectionItem; tab: CollectionKey }) {
  if ("version" in item) return <VersionCard gameVersion={item} />;
  if ("slotCost" in item) return <GadgetCard gadget={item} />;
  if (tab === "machines") return <MachineCard machine={item} />;
  return <RacerCard racer={item} />;
}

export function GameData() {
  const [tab, setTab] = useState<CollectionKey>("racers");
  const items = useLoad<CollectionItem[]>(`/${tab}`);
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
              Choose one racer, one front, one rear and one tire part, and optional gadgets in the order you
              want them shown.
            </p>
          </section>
        </div>
      </div>
      <div className="tabs">
        {collections.map(({ key, label, Icon }) => (
          <button
            key={key}
            className={key === tab ? "selected" : ""}
            aria-pressed={key === tab}
            onClick={() => setTab(key)}
          >
            <Icon /> {label}
          </button>
        ))}
      </div>
      <ErrorNotice message={items.error} />
      {items.loading && <p role="status">Loading collection…</p>}
      <div className={`collection collection-${tab}`}>
        {items.data?.map((item) => <CollectionCard item={item} tab={tab} key={item.id} />)}
      </div>
    </>
  );
}
