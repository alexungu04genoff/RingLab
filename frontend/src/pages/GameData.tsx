import { useState } from "react";
import { Artwork, ErrorNotice } from "../components";
import { useLoad } from "../useLoad";
import type { Gadget, Machine, Racer } from "../types";

const collections = [
  { key: "racers", label: "Racers" },
  { key: "machines", label: "Stock Machines" },
  { key: "gadgets", label: "Gadgets" },
] as const;

export function GameData() {
  const [tab, setTab] = useState<(typeof collections)[number]["key"]>("racers");
  const items = useLoad<Array<Racer | Machine | Gadget>>(`/${tab}`);
  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow accent">CROSSWORLDS COLLECTION</div>
          <h1>Know your setup.</h1>
          <p>Browse the pieces available for every RingLab build.</p>
        </div>
      </div>
      <section className="collection-intro" aria-label="How a RingLab build works">
        <strong>How a build comes together</strong>
        <p>
          Choose one racer, one complete stock machine, and optional gadgets in the order you
          want them shown.
        </p>
      </section>
      <div className="tabs">
        {collections.map(({ key, label }) => (
          <button
            key={key}
            className={key === tab ? "selected" : ""}
            aria-pressed={key === tab}
            onClick={() => setTab(key)}
          >
            {label}
          </button>
        ))}
      </div>
      <ErrorNotice message={items.error} />
      {items.loading && <p role="status">Loading collection…</p>}
      <div className="collection">
        {items.data?.map((i) => (
          <article className="panel collection-item" key={i.id}>
            <Artwork item={i} compact />
            <div>
              <h2>{i.name}</h2>
              {"racingType" in i && <span className="eyebrow">{i.racingType}</span>}
              {"description" in i && i.description && <p>{i.description}</p>}
            </div>
          </article>
        ))}
      </div>
    </>
  );
}
