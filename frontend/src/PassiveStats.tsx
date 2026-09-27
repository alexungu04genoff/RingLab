import type { BaseStats, GadgetRulesCatalog, PassiveStatsResult } from "./types";
import { statNames } from "./stats";
import { GearIcon, RacerIcon, SteeringWheelIcon } from "./icons";


export function signedPoints(value: number | null) { return value == null ? "Unknown" : `${value >= 0 ? "+" : "−"}${Math.abs(value)}`; }
export function adjustmentSummary(stats: BaseStats) {
  return statNames.filter(name => stats[name] !== 0 && stats[name] != null)
    .map(name => `${name[0].toUpperCase() + name.slice(1)} ${signedPoints(stats[name])}`).join(", ") || "No stat-point adjustment";
}
export const coverageLabel = (value: PassiveStatsResult) => ({
  CALCULATED: "Reviewed passive effects calculated",
  PARTIAL: "Partial calculation",
  UNSUPPORTED_VERSION: "Gadget rules unavailable for this patch",
  INVALID_LOADOUT: "Gadget calculation unavailable for this loadout",
})[value.coverage];

const groups = [
  { title: "Applied adjustments", statuses: ["APPLIED"] },
  { title: "Not active for this setup", statuses: ["NOT_MATCHED", "REQUIRES_SELECTION"] },
  { title: "Conditional effects — not included", statuses: ["CONDITIONAL"] },
  { title: "Other effects", statuses: ["NON_STAT"] },
  { title: "Unverified / not supported", statuses: ["UNSUPPORTED"] },
] as const;

export function PassiveStatsPanel({ value, compact = false }: { value?: PassiveStatsResult; compact?: boolean }) {
  if (!value) return <p className="muted">Passive gadget information is unavailable. Base stats remain available.</p>;
  const unavailable = value.coverage === "UNSUPPORTED_VERSION" || value.coverage === "INVALID_LOADOUT";
  const calculations = <dl className="passive-stat-grid" aria-label="Base plus gadget adjustment equals result">
    {statNames.map(name => <div key={name} className={`stat-${name}`}>
      <dt>{name[0].toUpperCase() + name.slice(1)}</dt>
      <dd><span aria-label={`Base ${name}`}>{value.base[name] ?? "—"}</span>
        <span className="passive-delta" aria-label={`Gadget ${name} adjustment`}>{unavailable ? "Unknown" : signedPoints(value.adjustments[name])}</span>
        <strong aria-label={`${name} result`}>= {unavailable ? "—" : value.adjusted[name] ?? "—"}</strong></dd>
    </div>)}
  </dl>;
  return <section className={`passive-stats${compact ? " passive-stats-compact" : ""}`} aria-label="Passive gadget stats">
    {!compact && <><h3>Stats</h3>
      <div className="stat-legend" aria-label="Stat bar breakdown">
        <span><RacerIcon /><i className="character-swatch" />Racer</span>
        <span><SteeringWheelIcon /><i className="machine-swatch" />Machine</span>
        <span><GearIcon /><i className="gadget-swatch" />Gadget</span>
      </div>
      <dl className="passive-stat-bars">{statNames.map(name => {
        const label = name[0].toUpperCase() + name.slice(1);
        const base = value.base[name];
        const total = unavailable ? base : value.adjusted[name];
        const delta = unavailable ? null : value.adjustments[name];
        const scale = 100 / Math.max(100, base ?? 0, total ?? 0);
        const racer = Math.min(100, Math.max(0, value.base.character?.[name] ?? 0) * scale);
        const machine = Math.min(100 - racer, Math.max(0, value.base.machine?.[name] ?? 0) * scale);
        const explanation = `${label}: base ${base ?? "unknown"}; gadget ${signedPoints(delta)}; ${unavailable ? "base shown" : "result"} ${total ?? "unknown"}`;
        return <div className={`stat-row stat-${name}`} key={name}>
          <div className="stat-label"><dt>{label}</dt><dd>{total ?? "—"}</dd></div>
          <div className={`card-stat-track${total == null ? " unknown" : ""}`} role="img" aria-label={explanation} title={explanation}>
            <span className="card-stat-character-fill" style={{width:`${racer}%`}} />
            <span className="card-stat-machine-fill" style={{width:`${machine}%`}} />
            {base != null && total != null && delta != null && delta !== 0 && <span className={`card-stat-gadget-segment ${delta > 0 ? "bonus" : "penalty"}`}
              style={{left:`${Math.max(0, Math.min(base,total))*scale}%`,width:`${Math.abs(Math.max(0,total)-Math.max(0,base))*scale}%`}} />}
          </div>
        </div>;
      })}</dl></>}
    <p className="passive-coverage" role="note">{coverageLabel(value)}</p>
    {compact && calculations}
    <p className="muted passive-note">{value.coverage === "PARTIAL" ? "Known subtotal only. " : ""}Passive effects only · Ver. {value.supportedVersion}</p>
    <details className="passive-effects" onClick={event => event.stopPropagation()}>
      <summary>Gadget effect details</summary>
      {!compact && calculations}
      <p>{value.note}</p>
      {groups.map(group => {
        const entries = value.effects.filter(effect => (group.statuses as readonly string[]).includes(effect.status));
        return entries.length > 0 && <section key={group.title}><h4>{group.title}</h4><ul>{entries.map(effect =>
          <li key={`${effect.gadgetId}-${effect.effectId}`}><strong>{effect.gadgetName} · {effect.label}</strong>
            {effect.status === "APPLIED" && <span>{adjustmentSummary(effect.adjustment)}</span>}
            <p>{effect.explanation}</p>
            {effect.sources.length > 0 && <a href={effect.sources[0]} target="_blank" rel="noreferrer">Rule source ↗</a>}
          </li>)}</ul></section>;
      })}
      {value.effects.length === 0 && <p>No gadgets selected.</p>}
      <small>Ruleset: {value.ruleset}</small>
    </details>
  </section>;
}

export function GadgetRuleDetails({ id, catalog }: { id: string; catalog?: GadgetRulesCatalog }) {
  const rules = catalog?.gadgets.find(gadget => gadget.gadgetId === id)?.effects;
  return <details className="gadget-rule-details"><summary>Verified effects & conditions</summary>
    {!catalog ? <p>Rule metadata unavailable.</p> : <><p>Ver. {catalog.supportedVersion} · Passive stat points</p>
      {!rules?.length ? <p>Effects have not been verified for calculation; this does not mean no effect.</p>
        : <ul>{rules.map(rule => <li key={rule.effectId}><strong>{rule.label}</strong>
          {rule.kind === "PASSIVE" ? <><p>{rule.subject === "ANY" ? "Always" : `${rule.subject === "RACER" ? "Racer" : "Machine"} type ${rule.requiredType}`}: {adjustmentSummary(rule.matching)}</p>
            {rule.subject !== "ANY" && <p>Other known types: {adjustmentSummary(rule.nonMatching)}</p>}</>
            : <p>{rule.kind === "CONDITIONAL" ? "Race condition — excluded" : rule.kind === "NON_STAT" ? "Separate from stat points" : "Unverified"}</p>}
          <p>{rule.explanation}</p><a href={rule.sources[0]} target="_blank" rel="noreferrer">Rule source ↗</a>
        </li>)}</ul>}
      <p>{catalog.note}</p><p>Stacking is supported for a single passive modifier or a same-type Tuner 1 + Tuner 2 pair. Other combinations are reported as unresolved.</p></>}
  </details>;
}
