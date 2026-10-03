import { useId, useState } from "react";
import { racingTypeLabel } from "../../shared/ui/RacingTypeBadge";
import { draftSelection, signedStatChange, recommendationSlots as slots, recommendationStatValue as stat } from "./recommendation";
import { SelectionItem } from "./RecommendationSelections";
import type { RecommendationCatalog, RecommendationResult, RecommendationSelection } from "./recommendation";
import type { BuildDraft, RacingType } from "../../shared/types";

function RecommendationStatBar({ label, before, after, difference, scale }: {
  label: string; before: number | null; after: number | null; difference: number | null; scale: number;
}) {
  const tooltipId = useId();
  const [open, setOpen] = useState(false);
  const change = difference === null ? "Unavailable" : difference === 0 ? "No change" : `${difference > 0 ? "+" : ""}${difference} points`;
  const bar = <span className={`card-stat-track recommendation-stat-track${after === null ? " unknown" : ""}`} role="img" aria-label={before === null ? `${label}: ${after ?? "Unavailable"}` : `${label}: ${before} to ${after ?? "Unavailable"}`}>
    {before === null && after !== null && <span className="recommendation-stat-base" style={{ width: `${Math.max(0,after) / scale * 100}%` }} />}
    {before !== null && after !== null && <>
      <span className="recommendation-stat-base" style={{ width: `${Math.max(0, Math.min(before, after)) / scale * 100}%` }} />
      {difference !== null && difference !== 0 && <span className={`card-stat-gadget-segment ${difference > 0 ? "bonus" : "penalty"}`}
        style={{ left: `${Math.max(0, Math.min(before, after)) / scale * 100}%`, width: `${Math.abs(Math.max(0, after) - Math.max(0, before)) / scale * 100}%` }} />}
      <span className="recommendation-stat-marker" style={{ left: `${Math.max(0, before) / scale * 100}%` }} />
    </>}
  </span>;
  if (difference === null) return bar;
  return <div className="recommendation-stat-meter" onMouseEnter={() => setOpen(true)} onMouseLeave={() => setOpen(false)}>
    <button type="button" className="recommendation-stat-hit-area" aria-label={`${label} change: ${change}`}
      aria-describedby={open ? tooltipId : undefined} onFocus={() => setOpen(true)} onBlur={() => setOpen(false)}
      onClick={() => setOpen(true)} onKeyDown={event => {
        if (event.key === "Escape") { event.preventDefault(); event.stopPropagation(); setOpen(false); }
      }}>{bar}</button>
    {open && <span className="recommendation-stat-tooltip" id={tooltipId} role="tooltip">
      <strong>{label} · {change}</strong>
      <span>Starting {before} → Recommended {after}</span>
      <span>{difference > 0 ? "Outlined extension: increase." : difference < 0 ? "Hatched reduction: decrease." : "The value stays the same."} Marker: starting value.</span>
    </span>}
  </div>;
}

function BalancedSelectionChanges({ reference, selected, locks, catalog, machineType }: {
  reference: BuildDraft; selected: RecommendationSelection; locks: RecommendationSelection;
  catalog: RecommendationCatalog; machineType: RacingType | null;
}) {
  const current = draftSelection(reference);
  const sameGadgets = reference.gadgetIds.length === selected.gadgetIds.length
    && reference.gadgetIds.every((id, index) => id === selected.gadgetIds[index]);
  return <section className="balanced-selection-changes" aria-label="Setup comparison">
    <h3>Setup comparison</h3>
    <div className="balanced-comparison-head" aria-hidden="true"><span>Selection</span><span>Current</span><span>Recommended</span></div>
    <dl>{slots.filter(slot => !(slot.key === "tirePartId" && machineType === "BOOST")).map(slot =>
      <div key={slot.key}>
        <dt>{slot.label}{locks[slot.key] && <small>Locked</small>}</dt>
        <dd aria-label={`Current ${slot.label}`}><SelectionItem id={current[slot.key]} slot={slot.key} catalog={catalog} showType /></dd>
        <dd aria-label={`Recommended ${slot.label}`}>{current[slot.key] === selected[slot.key]
          ? <span className="recommendation-unchanged">No change</span>
          : <SelectionItem id={selected[slot.key]} slot={slot.key} catalog={catalog} showType />}</dd>
      </div>)}
      <div><dt>Gadgets</dt>
        <dd aria-label="Current gadgets">{reference.gadgetIds.length
          ? reference.gadgetIds.map(id => <SelectionItem key={id} id={id} catalog={catalog} />) : "None"}</dd>
        <dd aria-label="Recommended gadgets">{sameGadgets ? <span className="recommendation-unchanged">No change</span>
          : selected.gadgetIds.length ? selected.gadgetIds.map(id => <SelectionItem key={id} id={id} catalog={catalog} />) : "None"}</dd>
      </div>
    </dl>
  </section>;
}

export function RecommendationComparison({ result, reference, locks, catalog, machineType, mode,
  priorities, ignored }: {
  result: RecommendationResult; reference: BuildDraft; locks: RecommendationSelection; catalog: RecommendationCatalog;
  machineType: RacingType | null; mode: "STRICT" | "BALANCED";
  priorities: RacingType[]; ignored: RacingType[];
}) {
  const selected = result.selection;
  const comparisonScale = Math.max(100, ...Object.values(result.currentStats ?? {}), ...Object.values(result.recommendedStats ?? {}));
  const ExplanationContainer = mode === "BALANCED" ? "details" : "section";
  return <>
    {selected && <><h3>{result.alreadyBest ? "Current setup retained" : "Proposed changes"}</h3>
      {mode === "STRICT" && <dl className="recommendation-changes">{slots.filter(slot => !(slot.key === "tirePartId" && machineType === "BOOST")).map(slot =>
        <div key={slot.key}><dt>{slot.label}{locks[slot.key] ? " · locked" : ""}</dt><dd>
          {draftSelection(reference)[slot.key] === selected[slot.key] ? <><SelectionItem id={selected[slot.key]} slot={slot.key} catalog={catalog} /><small>Kept</small></>
            : <><SelectionItem id={draftSelection(reference)[slot.key]} slot={slot.key} catalog={catalog} /><span aria-label="changes to">→</span><SelectionItem id={selected[slot.key]} slot={slot.key} catalog={catalog} /></>}
        </dd></div>)}
        <div><dt>Gadgets</dt><dd><span>{reference.gadgetIds.length ? reference.gadgetIds.map(id => <SelectionItem key={id} id={id} catalog={catalog} />) : "None"}</span>
          <span aria-label="changes to">→</span><span>{selected.gadgetIds.length ? selected.gadgetIds.map(id => <SelectionItem key={id} id={id} catalog={catalog} />) : "None"}</span></dd></div>
      </dl>}{machineType === "BOOST" && <p className="recommendation-setup-note">Boost uses Front and Rear only. The proposed setup has no tire.</p>}
      <section className="recommendation-stat-comparison" aria-label="Stat comparison">
        <h3>Passive-adjusted stats · same selected patch</h3>
        {result.currentStats ? <p className="recommendation-stat-legend">Starting → recommended · Outlined: gain · Hatched: loss. Hover, focus or tap a bar for details.</p>
          : <p>Recommended values</p>}
        <dl className="card-stats recommendation-stat-list">{priorities.map(priority => {
          const before = stat(result.currentStats, priority), after = stat(result.recommendedStats, priority);
          const difference = before === null || after === null ? null : Number((after - before).toFixed(8));
          const changeClass = difference === null || difference === 0 ? "recommendation-neutral" : difference > 0 ? "recommendation-gain" : "recommendation-loss";
          return <div key={priority} className={`card-stat recommendation-stat-row stat-${priority.toLowerCase()}`}>
            <div className="recommendation-stat-label"><dt>{racingTypeLabel(priority)}</dt>
              <dd>{result.currentStats && <span className="recommendation-stat-before">{before ?? "—"} → </span>}{after ?? "—"}</dd>
            </div>
            <RecommendationStatBar label={racingTypeLabel(priority)} before={before} after={after} difference={difference}
              scale={comparisonScale} />
            {result.currentStats && <span className={`recommendation-stat-delta ${changeClass}`}>{difference === null ? "Unavailable" : difference === 0 ? "No change" : `${difference > 0 ? "+" : ""}${difference}`}
              {mode === "BALANCED" && difference !== null && difference !== 0 && <small> ({signedStatChange(before, after)})</small>}</span>}
            {mode === "BALANCED" && ignored.includes(priority) && <small className="recommendation-stat-limit">Ignored in the recommendation</small>}
          </div>;
        })}</dl></section>
      {mode === "BALANCED" && <BalancedSelectionChanges reference={reference} selected={selected} locks={locks} catalog={catalog} machineType={machineType} />}</>}
    {mode === "BALANCED" && result.balanced && <details className="balanced-explanation"><summary>Balanced filtering stages</summary>
      {!result.balanced.proven ? <p>The search limit prevented proof. Stage thresholds are withheld for this best-found result.</p>
        : result.balanced.stages.length === 0 ? <p>All stats were ignored. Convenience ordering selected this setup.</p>
        : <div className="balanced-priorities-scroll"><table className="balanced-stage-table" aria-label="Balanced filtering stages">
          <thead><tr><th>Stat</th><th>Allowed loss</th><th>Best among survivors</th><th>Minimum</th><th>Builds remaining</th></tr></thead>
          <tbody>{result.balanced.stages.map(stage => <tr key={stage.stat}><th>{racingTypeLabel(stage.stat)}</th>
            <td>{stage.lossPercent}%</td><td>{stage.best}</td><td>{stage.threshold}</td><td>{stage.candidatesBefore} → {stage.candidatesAfter}</td></tr>)}</tbody>
        </table></div>}
    </details>}
    <ExplanationContainer aria-label="Recommendation explanation" className={mode === "BALANCED" ? "balanced-explanation" : undefined}>
      {mode === "BALANCED" ? <summary>Why this setup</summary> : <h3>Reason</h3>}<p>{result.reason}</p>
      <p>Machine type: {racingTypeLabel(machineType)}. All locked selections are hard constraints.</p>
      <ul>{result.restrictions.map(restriction => <li key={restriction}>{restriction}</li>)}</ul>
      <p>{result.note}</p><small>Ruleset: {result.ruleset} · {result.work} search steps · {result.elapsedMillis} ms</small></ExplanationContainer>
  </>;
}
