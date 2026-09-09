import { useState } from "react";
import { Artwork, ErrorNotice } from "../components";
import { useLoad } from "../useLoad";
import type { GameItem } from "../types";
export function GameData() {
  const [tab, setTab] = useState("racers");
  const items = useLoad<GameItem[]>(`/${tab}`);
  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow accent">CROSSWORLDS COLLECTION</div>
          <h1>Know your setup.</h1>
          <p>Racers, stock machines and gadgets available in RingLab.</p>
        </div>
      </div>
      <div className="tabs">
        {["racers", "machines", "gadgets"].map((t) => (
          <button
            key={t}
            className={t === tab ? "selected" : ""}
            aria-pressed={t === tab}
            onClick={() => setTab(t)}
          >
            {t}
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
              {i.racingType && <span className="eyebrow">{i.racingType}</span>}
              {i.description && <p>{i.description}</p>}
            </div>
          </article>
        ))}
      </div>
    </>
  );
}
