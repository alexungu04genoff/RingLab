import { useId, useState } from "react";
import { racingTypeLabel } from "../../shared/ui/RacingTypeBadge";
import { signedStatChange, recommendationStatValue as stat } from "./recommendation";
import { RecommendationSelectionComparison } from "./RecommendationSelectionComparison";
import { RecommendationExplanation } from "./RecommendationExplanation";
import type { RecommendationCatalog, RecommendationResult, RecommendationSelection, RecommendationRequest } from "./recommendation";
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

export function RecommendationComparison({ result, reference, locks, catalog, machineType, mode,
  priorities, ignored, losses, gadgetScope }: {
  result: RecommendationResult; reference: BuildDraft; locks: RecommendationSelection; catalog: RecommendationCatalog;
  machineType: RacingType | null; mode: "STRICT" | "BALANCED";
  priorities: RacingType[]; ignored: RacingType[]; losses: Record<RacingType, string>; gadgetScope: NonNullable<RecommendationRequest["gadgetScope"]>;
}) {
  const selected = result.selection;
  const comparisonScale = Math.max(100, ...Object.values(result.currentStats ?? {}), ...Object.values(result.recommendedStats ?? {}));

  return <>
    {selected && <><h3>{result.alreadyBest ? "Current setup retained" : "Proposed changes"}</h3>
      <RecommendationSelectionComparison reference={reference} selected={selected} locks={locks} catalog={catalog} machineType={machineType} showCurrent={!!result.currentStats} gadgetScope={gadgetScope} />{machineType === "BOOST" && <p className="recommendation-setup-note">Boost uses Front and Rear only. The proposed setup has no tire.</p>}
      <section className="recommendation-stat-comparison" aria-label="Stat comparison">
        <h3>Passive-adjusted stats · same selected patch</h3>
        {result.currentStats ? <p className="recommendation-stat-legend">Current → Recommended · Outlined: gain · Hatched: loss. Hover, focus or tap a bar for details.</p>
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
      </>}
    <RecommendationExplanation result={result} machineType={machineType} mode={mode} priorities={priorities} ignored={ignored} losses={losses} />
  </>;
}
