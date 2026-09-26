import { useId, useRef, useState } from "react";
import { Artwork } from "./components";
import { RacingTypeBadge } from "./RacingTypeBadge";
import { StatsBlock } from "./BaseStats";
import { statNames } from "./stats";
import type { BaseStats, Machine, MachinePart, StatsCatalog } from "./types";

const slotOrder = ["FRONT", "REAR", "TIRE"];
const slotLabel = (type: MachinePart["type"]) => type[0] + type.slice(1).toLowerCase();

export function StockMachineCard({ machine, parts, catalog, version, loading, error }: {
  machine: Machine; parts: MachinePart[]; catalog?: StatsCatalog; version: string | null;
  loading: boolean; error: string;
}) {
  const [open, setOpen] = useState(false);
  const id = useId();
  const trigger = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const orderedParts = parts.filter((part) => machine.racingType !== "BOOST" || part.type !== "TIRE")
    .sort((left, right) => slotOrder.indexOf(left.type) - slotOrder.indexOf(right.type));
  const knownParts = orderedParts.some((part) => statNames.some((stat) => catalog?.machineParts[part.id]?.[stat] != null));
  function close() {
    if (panel.current?.contains(document.activeElement)) trigger.current?.focus();
    setOpen(false);
  }
  return <div className="stock-machine-card" onKeyDown={(event) => {
    if (open && event.key === "Escape") { event.stopPropagation(); close(); }
  }}>
    <article className="panel collection-item machine-collection-item">
      <Artwork item={machine} compact />
      <div className="collection-copy"><span className="eyebrow">Machine</span><h2>{machine.name}</h2>
        <RacingTypeBadge kind="machine" type={machine.racingType} className="collection-type" />
        <button type="button" className="part-stats-trigger" ref={trigger} aria-expanded={open} aria-controls={id}
          onClick={() => open ? close() : setOpen(true)}>{open ? "Hide part stats" : "View part stats"}</button>
      </div>
    </article>
    {open && <div className="machine-stats-disclosure" id={id} ref={panel} role="region" aria-label={`${machine.name} part stats`}>
      <div className="machine-stats-disclosure-heading"><Artwork item={machine} compact />
        <div><h3>{machine.name}</h3><span>{version ? `Ver. ${version}` : "Patch unavailable"}</span></div>
      </div>
      <p className="muted">Stock machine parts only. Racer not included.</p>
      {loading ? <p role="status">Loading part stats…</p> : error
        ? <p role="alert">Couldn’t load part stats. {error}</p> : <>
          {!knownParts && <p>Part stats unavailable{version ? ` for Ver. ${version}` : ""}.</p>}
          <div className="machine-part-stats-grid">{orderedParts.map((part) =>
            <PartStats key={part.id} part={part} stats={catalog?.machineParts[part.id]} />)}</div>
          <StatsBlock title="Machine contribution" stats={catalog?.machines[machine.id]} version={version}
            missingVersionMessage="Stats unavailable because the patch could not be determined." />
        </>}
      <button type="button" onClick={close}>Close part stats</button>
    </div>}
  </div>;
}

function PartStats({ part, stats }: { part: MachinePart; stats?: BaseStats }) {
  return <section className="machine-part-stats" aria-label={`${slotLabel(part.type)} part stats`}>
    <h3>{slotLabel(part.type)}</h3>
    <dl>{statNames.map((stat) => <div key={stat}><dt>{stat[0].toUpperCase() + stat.slice(1)}</dt>
      <dd>{stats?.[stat] ?? "—"}</dd></div>)}</dl>
  </section>;
}
