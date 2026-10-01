import { useState, type DragEvent } from "react";
import { racingTypeClass, racingTypeLabel } from "../../shared/ui/RacingTypeBadge";
import { movePriority, movePriorityTo, recommendationStatValue as stat } from "./recommendation";
import type { useRecommendationConfiguration } from "./useRecommendationConfiguration";
import type { RacingType } from "../../shared/types";

type Configuration = ReturnType<typeof useRecommendationConfiguration>;

export function RecommendationModeControls({ configuration, hasResult, onModeChange }: {
  configuration: Configuration; hasResult: boolean; onModeChange: (mode: "STRICT" | "BALANCED") => void;
}) {
  const { mode, machineType, setMachineType } = configuration;
  return (
    <div className="recommendation-toolbar">
    <div className="recommendation-modes" role="group" aria-label="Recommendation mode">
      {(["STRICT", "BALANCED"] as const).map(value => <button type="button" key={value} aria-pressed={mode === value}
        onClick={() => onModeChange(value)}>{value === "STRICT" ? "Strict" : "Balanced"}</button>)}
    </div>
    {!hasResult && <label className="recommendation-machine-type">Recommendation machine type<select value={machineType ?? ""} onChange={event => setMachineType((event.target.value || null) as RacingType | null)}>
      <option value="">Choose a machine type</option>{["SPEED", "ACCELERATION", "HANDLING", "POWER", "BOOST"].map(type =>
        <option key={type} value={type}>{racingTypeLabel(type as RacingType)}</option>)}</select></label>}
    </div>
  );
}

export function RecommendationPriorityControls({ configuration, headingId }: {
  configuration: Configuration; headingId: string;
}) {
  const { mode, priorities, setPriorities, activePriorities, secondary, setSecondary,
    losses, setLosses, referenceVersion, referenceValues, referenceAvailable } = configuration;
  const [draggedPriority, setDraggedPriority] = useState<RacingType | null>(null);
  function priorityDragEvents(priority: RacingType) {
    return {
      onDragStart: (event: DragEvent<HTMLElement>) => {
        event.dataTransfer.setData("text/plain", priority);
        event.dataTransfer.effectAllowed = "move";
        setDraggedPriority(priority);
      },
      onDragOver: (event: DragEvent<HTMLElement>) => {
        event.preventDefault();
        event.dataTransfer.dropEffect = "move";
      },
      onDrop: (event: DragEvent<HTMLElement>) => {
        event.preventDefault();
        const source = event.dataTransfer.getData("text/plain") as RacingType;
        setPriorities(current => movePriorityTo(current, source, priority));
        setDraggedPriority(null);
      },
      onDragEnd: () => setDraggedPriority(null),
    };
  }
  return <>
    <h3>Stat priorities</h3>{mode === "STRICT" ? <p>Maximizes your first stat. Lower priorities break ties.</p>
      : <><p>Rank your stats and set how much each may decrease. Every minimum is enforced.</p>
        <details className="balanced-scoring-details"><summary>How balanced scoring works</summary>
          <p>Rank weights are {activePriorities.map((_, index) => activePriorities.length - index).join(", ")}. Weighted relative gains may compensate for allowed losses, but every minimum is mandatory. Loss limits do not set weights. A zero reference uses one stat point for normalization and shows point changes.</p>
        </details></>}
    <p className="muted">Drag the stats to reorder, or use the arrow buttons.</p>
    {mode === "STRICT" ? <ol className="recommendation-priorities">{activePriorities.map((priority, index) => <li key={priority}
      draggable className={draggedPriority === priority ? "dragging" : undefined}
      {...priorityDragEvents(priority)}>
      <span className="priority-rank" aria-hidden="true">{index + 1}.</span>
      <span className="priority-drag-handle" aria-hidden="true">⠿</span>
      <span className={`type-badge racing-type ${racingTypeClass(priority)}`}>{racingTypeLabel(priority)}</span>
      <span className="recommendation-priority-current" aria-label={`${racingTypeLabel(priority)} current: ${stat(referenceValues, priority) ?? "Unavailable"}`}>
        <small>Current</small><strong>{stat(referenceValues, priority) ?? "Unavailable"}</strong></span>
      <div className="recommendation-priority-actions">
        <button type="button" aria-label={`Move ${racingTypeLabel(priority)} up`} disabled={index === 0}
          onClick={() => setPriorities(movePriority(priorities, index, -1))}>↑</button>
        <button type="button" aria-label={`Move ${racingTypeLabel(priority)} down`} disabled={index === activePriorities.length - 1}
          onClick={() => setPriorities(movePriority(priorities, index, 1))}>↓</button></div>
    </li>)}</ol> : <div className="balanced-priorities-scroll"><table className="balanced-priorities-table" aria-label="Stat priorities">
      <thead><tr><th scope="col"><span className="sr-only">Drag</span></th><th scope="col">Stat</th><th scope="col">Move</th>
        <th scope="col">Current</th><th scope="col">Maximum loss</th><th scope="col">Minimum allowed</th><th scope="col">Tie-break / Ignore</th></tr></thead>
      <tbody>{activePriorities.map((priority, index) => {
        const minimum = referenceAvailable && losses[priority] !== "" && Number.isFinite(Number(losses[priority]))
          ? Number((stat(referenceValues, priority)! * (1 - Number(losses[priority]) / 100)).toFixed(8)) : "Unavailable";
        return <tr key={priority} draggable className={draggedPriority === priority ? "dragging" : undefined}
          {...priorityDragEvents(priority)}>
          <td><span className="priority-drag-handle" aria-hidden="true">⠿</span></td>
          <th scope="row"><span className={`type-badge racing-type ${racingTypeClass(priority)}`}>{racingTypeLabel(priority)}</span></th>
          <td><div className="priority-reorder"><button type="button" aria-label={`Move ${racingTypeLabel(priority)} up`} disabled={index === 0}
            onClick={() => setPriorities(movePriorityTo(priorities, priority, activePriorities[index - 1]))}>↑</button>
            <button type="button" aria-label={`Move ${racingTypeLabel(priority)} down`} disabled={index === activePriorities.length - 1}
              onClick={() => setPriorities(movePriorityTo(priorities, priority, activePriorities[index + 1]))}>↓</button></div></td>
          <td className="balanced-current" aria-label={`${racingTypeLabel(priority)} current: ${stat(referenceValues, priority) ?? "Unavailable"}`}>
            {stat(referenceValues, priority) ?? "Unavailable"}</td>
          <td><label className="sr-only" htmlFor={`${headingId}-${priority}-loss`}>{racingTypeLabel(priority)} maximum loss (%)</label>
            <div className="balanced-loss-input"><input id={`${headingId}-${priority}-loss`} type="number" min="0" max="100" step="any"
              value={losses[priority]} onChange={event => setLosses({ ...losses, [priority]: event.target.value })} /><span aria-hidden="true">%</span></div></td>
          <td className="balanced-minimum" aria-label={`${racingTypeLabel(priority)} minimum allowed: ${minimum}`}>{minimum}</td>
          <td><button type="button" className="balanced-demote" disabled={activePriorities.length === 1}
            aria-label={`Move ${racingTypeLabel(priority)} to tie-break / ignore`} onClick={() => setSecondary([...secondary, priority])}>Move</button></td>
        </tr>;
      })}</tbody>
    </table></div>}
    {mode === "BALANCED" && <section aria-label="Secondary stats"><h3 title="No minimum required. Higher values count only when your prioritized stats are equal. Multiple secondary stats are compared by their combined total.">Tie-break / Ignore</h3>
      <p>No minimum required. Higher values count only when your prioritized stats are equal. Multiple secondary stats are compared by their combined total.</p>
      {secondary.length > 0 && <div className="balanced-priorities-scroll"><table className="balanced-secondary-table" aria-label="Tie-break / Ignore stats"><thead><tr>
        <th scope="col">Stat</th><th scope="col">Current</th><th scope="col"><span className="sr-only">Action</span></th></tr></thead>
        <tbody>{secondary.map(priority => <tr key={priority}><th scope="row"><span className={`type-badge racing-type ${racingTypeClass(priority)}`}>{racingTypeLabel(priority)}</span></th>
          <td className="balanced-current">{stat(referenceValues, priority) ?? "Unavailable"}</td><td><button type="button" aria-label={`Restore ${racingTypeLabel(priority)}`}
            onClick={() => {
              setPriorities(current => [...current.filter(stat => stat !== priority), priority]);
              setSecondary(current => current.filter(stat => stat !== priority));
            }}>Restore</button></td></tr>)}</tbody></table></div>}
    </section>}
    {referenceVersion && referenceVersion.version !== "1.4.1" && <p role="alert">Recommendation is unavailable for this patch. The selected draft patch is preserved.</p>}
  </>;
}
