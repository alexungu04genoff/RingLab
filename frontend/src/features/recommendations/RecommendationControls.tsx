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
  const { mode, priorities, setPriorities, activePriorities, ignored, setIgnored,
    losses, setLosses, referenceVersion, referenceValues } = configuration;
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
      : <><p>Balanced considers your priorities in order. For each stat, it keeps builds within the loss you allow, then evaluates the next priority.</p>
        <p className="muted">0% keeps only the best value at that stage. Ignore removes a stat from the decision entirely. Your current setup is for comparison only.</p></>}
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
        <th scope="col">Maximum sacrifice</th><th scope="col">Ignore</th></tr></thead>
      <tbody>{priorities.map((priority, index) => {
        return <tr key={priority} draggable className={draggedPriority === priority ? "dragging" : undefined}
          {...priorityDragEvents(priority)}>
          <td><span className="priority-drag-handle" aria-hidden="true">⠿</span></td>
          <th scope="row"><span className={`type-badge racing-type ${racingTypeClass(priority)}`}>{racingTypeLabel(priority)}</span></th>
          <td><div className="priority-reorder"><button type="button" aria-label={`Move ${racingTypeLabel(priority)} up`} disabled={index === 0}
            onClick={() => setPriorities(movePriority(priorities,index,-1))}>↑</button>
            <button type="button" aria-label={`Move ${racingTypeLabel(priority)} down`} disabled={index === priorities.length - 1}
              onClick={() => setPriorities(movePriority(priorities,index,1))}>↓</button></div></td>
          <td><label className="sr-only" htmlFor={`${headingId}-${priority}-loss`}>{racingTypeLabel(priority)} maximum sacrifice (%)</label>
            <div className="balanced-loss-input"><input id={`${headingId}-${priority}-loss`} type="number" min="0" max="100" step="any"
              disabled={ignored.includes(priority)}
              value={losses[priority]} onChange={event => setLosses({ ...losses, [priority]: event.target.value })} /><span aria-hidden="true">%</span></div></td>
          <td><input type="checkbox" aria-label={`Ignore ${racingTypeLabel(priority)}`} checked={ignored.includes(priority)}
            onChange={event => setIgnored(event.target.checked ? [...ignored,priority] : ignored.filter(stat => stat !== priority))} /></td>
        </tr>;
      })}</tbody>
    </table></div>}
    {mode === "BALANCED" && activePriorities.length === 0 && <p>All stats are ignored. The recommendation will prefer fewer changes, fewer added gadgets, then lower slot cost.</p>}
    {referenceVersion && referenceVersion.version !== "1.4.1" && <p role="alert">Recommendation is unavailable for this patch. The selected draft patch is preserved.</p>}
  </>;
}
