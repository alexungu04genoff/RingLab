import type { BaseStats, GadgetRulesCatalog, PassiveStatsResult, StatsMode } from "./types";
import { statNames } from "./stats";

export function StatsModeControl({ mode, onChange }: { mode: StatsMode; onChange: (mode: StatsMode) => void }) {
  return <div className="stats-mode" role="group" aria-label="Statistics mode" onClick={event => event.stopPropagation()}>
    <button type="button" aria-pressed={mode === "base"} onClick={() => onChange("base")}>Base</button>
    <button type="button" aria-pressed={mode === "gadgets"} onClick={() => onChange("gadgets")}>With gadgets</button>
  </div>;
}

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
  return <section className={`passive-stats${compact ? " passive-stats-compact" : ""}`} aria-label="Passive gadget stats">
    <p className="passive-coverage" role="note">{coverageLabel(value)}</p>
    <dl className="passive-stat-grid" aria-label="Base plus gadget adjustment equals result">
      {statNames.map(name => <div key={name} className={`stat-${name}`}>
        <dt>{name[0].toUpperCase() + name.slice(1)}</dt>
        <dd><span aria-label={`Base ${name}`}>{value.base[name] ?? "—"}</span>
          <span className="passive-delta" aria-label={`Gadget ${name} adjustment`}>{unavailable ? "Unknown" : signedPoints(value.adjustments[name])}</span>
          <strong aria-label={`${name} result`}>= {unavailable ? "—" : value.adjusted[name] ?? "—"}</strong></dd>
      </div>)}
    </dl>
    <p className="muted passive-note">{value.coverage === "PARTIAL" ? "Known subtotal only. " : ""}Passive effects only · Ver. {value.supportedVersion}</p>
    <details className="passive-effects" onClick={event => event.stopPropagation()}>
      <summary>Gadget effect details</summary>
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
