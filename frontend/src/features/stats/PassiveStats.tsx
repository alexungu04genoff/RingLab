import { useId, useState } from "react";
import type { BaseStats, GadgetRulesCatalog, PassiveStatsResult, RacingType } from "../../shared/types";
import { statNames, type StatName } from "./stats";
import { StatBar, StatLegend } from "../../shared/ui/StatBar";
import { InfoPopover } from "../../shared/ui/InfoPopover";
import { gadgetTypeConditions } from "../gadgets/gadgetPresentation";


export function signedPoints(value: number | null) { return value == null ? "Unknown" : `${value >= 0 ? "+" : "−"}${Math.abs(value)}`; }

type GadgetTypeSelection = { racerType: RacingType | null; machineType: RacingType | null };
type GadgetRule = GadgetRulesCatalog["gadgets"][number]["effects"][number];
function unmetTypeCondition(rule: GadgetRule, selection?: GadgetTypeSelection) {
  if (!selection || rule.subject === "ANY" || !rule.requiredType) return false;
  return (rule.subject === "RACER" ? selection.racerType : selection.machineType) !== rule.requiredType;
}

export function GadgetAdjustmentBadges({ gadgetId, value, catalog, selection }: {
  gadgetId: string; value?: PassiveStatsResult; catalog?: GadgetRulesCatalog; selection?: GadgetTypeSelection;
}) {
  if (!value) return <GadgetCatalogAdjustmentBadges gadgetId={gadgetId} catalog={catalog} selection={selection} />;
  const effects = value.effects.filter(effect => effect.gadgetId === gadgetId && effect.status === "APPLIED");
  const adjustments = statNames.map(name => ({name,
    points: effects.reduce((total, effect) => total + (effect.adjustment[name] ?? 0), 0),
  })).filter(({points}) => points !== 0);
  if (!adjustments.length) {
    const inactiveEffect = value.effects.find(effect => effect.gadgetId === gadgetId && effect.status === "UNSUPPORTED")
      ?? value.effects.find(effect => effect.gadgetId === gadgetId && effect.effectId === "stats");
    const status = inactiveEffect?.status;
    const message = value.coverage === "UNSUPPORTED_VERSION" ? "Unavailable for this patch"
      : value.coverage === "INVALID_LOADOUT" ? "Not calculated · invalid loadout"
      : status === "UNSUPPORTED" ? "Not added · effect unsupported"
      : status === "REQUIRES_SELECTION" ? "Choose the required racer or machine"
      : status === "NOT_MATCHED" ? "Stat adjustment inactive · type does not match" : null;
    return message ? <span className="gadget-adjustments gadget-selection-feedback" aria-label="Gadget adjustment not included">
      <GadgetCatalogAdjustmentBadges gadgetId={gadgetId} catalog={catalog} selection={selection} />
      <span className="gadget-adjustment unresolved" title={inactiveEffect?.explanation}>
        {message}
      </span>
    </span> : <GadgetCatalogAdjustmentBadges gadgetId={gadgetId} catalog={catalog} selection={selection} />;
  }
  return <span className="gadget-adjustments" aria-label="Applied gadget adjustments">
    {adjustments.map(({name,points}) => <span key={name}
      className={`gadget-adjustment stat-row stat-${name} ${points > 0 ? "bonus" : "penalty"}`}
      title={`Applied to this setup${value.coverage === "PARTIAL" ? "; known subtotal only" : ""}`}>
      {name[0].toUpperCase() + name.slice(1)} {signedPoints(points)}
    </span>)}
  </span>;
}

/** Shows reviewed passive values before selection; type-dependent values retain their condition. */
export function GadgetCatalogAdjustmentBadges({ gadgetId, catalog, selection }: {
  gadgetId: string; catalog?: GadgetRulesCatalog; selection?: GadgetTypeSelection;
}) {
  const effects = (catalog?.gadgets ?? []).find(gadget => gadget.gadgetId === gadgetId)?.effects
    .filter(effect => effect.kind === "PASSIVE") ?? [];
  const groups = effects.map(effect => {
    const actualType = effect.subject === "RACER" ? selection?.racerType : selection?.machineType;
    const unmet = unmetTypeCondition(effect, selection);
    const useNonMatching = unmet && actualType != null && statNames.some(name => (effect.nonMatching[name] ?? 0) !== 0);
    const values = useNonMatching ? effect.nonMatching : effect.matching;
    const condition = useNonMatching ? ` for the selected ${actualType!.toLowerCase()} ${effect.subject.toLowerCase()}`
      : effect.subject === "ANY" ? "" : ` for a ${effect.requiredType?.toLowerCase()} ${effect.subject.toLowerCase()}`;
    return { effect, inactive: unmet && !useNonMatching, condition, adjustments: statNames
      .map(name => ({ name, points: values[name] }))
      .filter(({ points }) => points != null && points !== 0) };
  });
  if (!groups.some(group => group.adjustments.length)) return null;
  return <span className="gadget-adjustments gadget-catalog-adjustments" aria-label="Reviewed gadget stat adjustments">
    {groups.map(({ effect, adjustments, inactive, condition }) => adjustments.length > 0 && <span className={`gadget-adjustment-group${inactive ? " gadget-condition-unmet" : ""}`} key={effect.effectId}>
      {adjustments.map(({ name, points }) => <span key={name}
        className={`gadget-adjustment stat-row stat-${name} ${points! > 0 ? "bonus" : "penalty"}`}
        title={`Reviewed passive effect${condition}; inclusion depends on the selected combination.`}>
        {name[0].toUpperCase() + name.slice(1)} {signedPoints(points)}
      </span>)}
    </span>)}
  </span>;
}

export function GadgetCatalogTypeLabels({ gadgetId, catalog, selection }: {
  gadgetId: string; catalog?: GadgetRulesCatalog; selection?: GadgetTypeSelection;
}) {
  const labels = gadgetTypeConditions(gadgetId, catalog)
    .filter(effect => effect.kind === "PASSIVE")
    .map(effect => ({ type: effect.requiredType!, unmet: unmetTypeCondition(effect, selection), label: `${effect.requiredType![0] + effect.requiredType!.slice(1).toLowerCase()} ${effect.subject.toLowerCase()}` }))
    .filter((entry, index, entries) => entries.findIndex(candidate => candidate.label === entry.label) === index) ?? [];
  return labels.length > 0 ? <span className="gadget-type-labels" aria-label="Gadget type conditions">
    {labels.map(({ type, label, unmet }) => <span className={`gadget-type-badge racing-type racing-type-${type.toLowerCase()}${unmet ? " gadget-condition-unmet" : ""}`}
      title={selection ? `${unmet ? "Not met" : "Met"}: requires ${label}` : undefined} key={label}>{label}</span>)}
  </span> : null;
}
export function adjustmentSummary(stats: BaseStats) {
  return statNames.filter(name => stats[name] !== 0 && stats[name] != null)
    .map(name => `${name[0].toUpperCase() + name.slice(1)} ${signedPoints(stats[name])}`).join(", ") || "No stat-point adjustment";
}
const hasKnownAdjustments = (value: PassiveStatsResult) => value.effects.some(effect => effect.status === "APPLIED"
  && statNames.some(name => effect.adjustment[name] != null && effect.adjustment[name] !== 0));
export const coverageLabel = (value: PassiveStatsResult) => ({
  CALCULATED: "Reviewed passive effects calculated",
  PARTIAL: hasKnownAdjustments(value) ? "Known subtotal — this setup is not fully calculated."
    : "Base stats shown — this gadget combination is not fully calculated.",
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
  const partial = value.coverage === "PARTIAL";
  const adjustmentLabel = (name: StatName) => unavailable ? "Unknown"
    : partial ? (value.effects.some(effect => effect.status === "APPLIED" && (effect.adjustment[name] ?? 0) !== 0)
      ? `Known ${signedPoints(value.adjustments[name])}` : "Not calculated") : signedPoints(value.adjustments[name]);
  const calculations = <dl className="passive-stat-grid" aria-label="Base plus gadget adjustment equals result">
    {statNames.map(name => <div key={name} className={`stat-row stat-${name}`}>
      <dt>{name[0].toUpperCase() + name.slice(1)}</dt>
      <dd><span aria-label={`Base ${name}`}>{value.base[name] ?? "—"}</span>
        <span className="passive-delta" aria-label={`Gadget ${name} adjustment`}>{adjustmentLabel(name)}</span>
        <strong aria-label={`${name} result`}>= {unavailable ? "—" : value.adjusted[name] ?? "—"}</strong></dd>
    </div>)}
  </dl>;
  return <section className={`passive-stats${compact ? " passive-stats-compact" : ""}`} aria-label="Passive gadget stats">
    {!compact && <><h3>Stats</h3>
      <StatLegend />
      <dl className="passive-stat-bars">{statNames.map(name => {
        const label = name[0].toUpperCase() + name.slice(1);
        const base = value.base[name];
        const total = unavailable ? base : value.adjusted[name];
        const delta = unavailable ? null : value.adjustments[name];
        const hasAppliedEffect = !unavailable && value.effects.some(effect => effect.status === "APPLIED"
          && effect.adjustment[name] != null && effect.adjustment[name] !== 0);
        const explanation = `${label}: base ${base ?? "unknown"}; gadget ${adjustmentLabel(name)}; ${unavailable ? "base shown" : partial ? "known subtotal" : "result"} ${total ?? "unknown"}`;
        return <div className={`stat-row stat-${name}`} key={name}>
          <div className="stat-label"><dt>{label}</dt><dd>{total ?? "—"}</dd></div>
          <div className="card-stat-meter">
          <StatBar base={base} racer={value.base.character?.[name] ?? null} machine={value.base.machine?.[name] ?? null}
            passive={total} gadgetDelta={hasAppliedEffect ? delta : null} description={explanation} />
          {hasAppliedEffect && <GadgetStatImpact name={name} value={value} />}
          </div>
        </div>;
      })}</dl></>}
    <p className="passive-coverage" role="note">{coverageLabel(value)}</p>
    {compact && calculations}
    <p className="muted passive-note">Passive effects only · Ver. {value.supportedVersion}</p>
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
    {!catalog ? <p>Rule metadata unavailable.</p> : <><div className="gadget-rule-metadata"><p>Ver. {catalog.supportedVersion} · Passive stat points</p>
      <CalculationNotes /></div>
      {!rules?.length ? <p>Effects have not been verified for calculation; this does not mean no effect.</p>
        : <ul>{rules.map(rule => <li key={rule.effectId}><strong>{rule.label}</strong>
          {rule.kind === "PASSIVE" ? <><p>{rule.subject === "ANY" ? "Always" : `${rule.subject === "RACER" ? "Racer" : "Machine"} type ${rule.requiredType}`}: {adjustmentSummary(rule.matching)}</p>
            {rule.subject !== "ANY" && <p>Other known types: {adjustmentSummary(rule.nonMatching)}</p>}</>
            : <p>{rule.kind === "CONDITIONAL" ? "Race condition — excluded" : rule.kind === "NON_STAT" ? "Separate from stat points" : "Unverified"}</p>}
          <p>{rule.explanation}</p>
        </li>)}</ul>}
      </>}
  </details>;
}

function CalculationNotes() {
  return <InfoPopover label="Calculation notes" hint="About stat calculations">
    <p>RingLab shows reviewed passive stat effects using its documented calculation model. Race-dependent effects are handled separately by Scenario Preview. Reviewed numerical modifiers are combined; unknown effects are shown as incomplete instead of being guessed.</p>
    <p className="calculation-notes-technical">The 23 reviewed numerical passive effects combine through signed addition, including penalties. This stacking model is supported by the community calculator, not independently verified official in-game behavior. Unknown effects remain unsupported.</p>
  </InfoPopover>;
}

export function GadgetStatImpact({ name, value }: { name: StatName; value: PassiveStatsResult }) {
  const tooltipId = useId();
  const [open, setOpen] = useState(false);
  const effects = value.effects.filter(effect => effect.status === "APPLIED"
    && effect.adjustment[name] != null && effect.adjustment[name] !== 0);
  if (!effects.length) return null;
  const label = name[0].toUpperCase() + name.slice(1);
  return <span className="card-stat-impact" onMouseEnter={() => setOpen(true)} onMouseLeave={() => setOpen(false)}
    onBlur={() => setOpen(false)} onKeyDown={event => {
      if (event.key === "Escape") { event.stopPropagation(); setOpen(false); }
    }}>
    <button type="button" aria-label={`${label} gadget adjustment ${signedPoints(value.adjustments[name])}`}
      aria-describedby={open ? tooltipId : undefined} onFocus={() => setOpen(true)}
      onClick={event => { event.stopPropagation(); setOpen(true); }} />
    {open && <span className="card-stat-impact-copy" id={tooltipId} role="tooltip">
      {effects.map(effect => <span key={`${effect.gadgetId}-${effect.effectId}`}>
        <strong>{effect.gadgetName}</strong> · {label} {signedPoints(effect.adjustment[name])}
      </span>)}
      <span>Base {value.base[name] ?? "—"} {signedPoints(value.adjustments[name])} = {value.adjusted[name] ?? "—"}.</span>
      <span>Included in the passive value.
        {value.coverage === "PARTIAL" && " Known subtotal only."}</span>
    </span>}
  </span>;
}
