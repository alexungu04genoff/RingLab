import { useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Artwork, ErrorNotice, racingTypeClass, racingTypeLabel } from "./components";
import { useBuildRecommendation } from "./useBuildRecommendation";
import { defaultPriorities, draftSelection, lockTypeConflict, movePriority, movePriorityTo, validPriorities } from "./recommendation";
import type { ComponentKey, RecommendationCatalog, RecommendationSelection } from "./recommendation";
import type { BaseStats, BuildDraft, GameVersion, RacingType } from "./types";

export function SelectionLock({ label, locked, disabled, onClick }: {
  label: string; locked: boolean | "mixed"; disabled?: boolean; onClick: () => void;
}) {
  const hint = locked === true ? "This selection will not be replaced. Unlock it to allow changes."
    : "Keep this selection when recommending a build.";
  return <button type="button" className="selection-lock" aria-label={`${locked === true ? "Unlock" : "Lock"} ${label}`}
    aria-pressed={locked} disabled={disabled} title={hint} onClick={event => { event.preventDefault(); event.stopPropagation(); onClick(); }}>
    <svg viewBox="0 0 20 20" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
      <rect x="4" y="9" width="12" height="9" rx="2" />
      {locked === true ? <path d="M6 9V6a4 4 0 0 1 8 0v3" /> : <path d="M6 9V6a4 4 0 0 1 7.5-2" />}
      {locked === "mixed" ? <path d="M7 13.5h6" /> : <path d="M10 12v3" />}
    </svg>
    <span className="lock-focus-hint">{hint}</span>
  </button>;
}

const slots: { key: ComponentKey; label: string }[] = [
  { key: "racerId", label: "Racer" }, { key: "frontPartId", label: "Front" },
  { key: "rearPartId", label: "Rear" }, { key: "tirePartId", label: "Tire" },
];
function SelectionItem({ id, slot, catalog }: { id: string | null; slot?: ComponentKey; catalog: RecommendationCatalog }) {
  const racer = slot === "racerId" ? catalog.racers.find(item => item.id === id) : undefined;
  const part = slot && slot !== "racerId" ? catalog.parts.find(item => item.id === id) : undefined;
  const gadget = !slot ? catalog.gadgets.find(item => item.id === id) : undefined;
  const item = racer ?? gadget ?? (part ? { id: part.id, name: part.sourceMachineName, racingType: part.racingType, imagePath: part.sourceMachineImagePath } : null);
  return <span className="recommendation-item">{item && <Artwork item={item} compact />}<span>{item?.name ?? (id ? "Unknown selection" : "Not selected")}</span></span>;
}

export function BuildRecommendationDialog({ draft, locks, context, catalog, version, returnFocus, onClose, onApply }: {
  draft: BuildDraft; locks: RecommendationSelection; context: string; catalog: RecommendationCatalog;
  version: GameVersion | null; returnFocus: HTMLButtonElement | null; onClose: () => void; onApply: (draft: BuildDraft) => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const headingId = useId();
  const [machineType, setMachineType] = useState<RacingType | null>(draft.machineType);
  const [priorities, setPriorities] = useState([...defaultPriorities]);
  const [draggedPriority, setDraggedPriority] = useState<RacingType | null>(null);
  const recommendation = useBuildRecommendation(draft, locks, context, catalog, onApply);
  const result = recommendation.result;
  useEffect(() => {
    const element = dialog.current!;
    const previousOverflow = document.body.style.overflow;
    element.showModal(); heading.current?.focus(); document.body.style.overflow = "hidden";
    return () => { element.close(); document.body.style.overflow = previousOverflow; returnFocus?.focus(); };
  }, [returnFocus]);
  useEffect(() => { if (result) heading.current?.focus(); }, [result]);
  const conflict = lockTypeConflict(machineType, locks, catalog.parts);
  const lockedSlots = slots.filter(slot => locks[slot.key]);
  const selected = result?.selection;
  const close = () => { recommendation.cancel(); onClose(); };
  const stat = (stats: BaseStats | null | undefined, priority: RacingType) => stats?.[priority.toLowerCase() as keyof BaseStats] ?? null;
  return createPortal(<dialog className="build-recommendation-dialog" ref={dialog} aria-labelledby={headingId}
    onKeyDown={event => {
      if (event.key !== "Tab" || event.altKey || event.ctrlKey || event.metaKey) return;
      const controls = [...event.currentTarget.querySelectorAll<HTMLElement>("button, a[href], input, select, textarea, [tabindex]")]
        .filter(control => control.tabIndex >= 0 && !control.matches(":disabled") && !control.closest("[hidden]"));
      const first = controls[0], last = controls.at(-1);
      if (!first || !last) return;
      const current = controls.indexOf(document.activeElement as HTMLElement);
      if (current === -1 || (event.shiftKey ? current === 0 : current === controls.length - 1)) {
        event.preventDefault();
        (event.shiftKey ? last : first).focus();
      }
    }}
    onCancel={event => { event.preventDefault(); close(); }}>
    <div className="recommendation-heading"><h2 id={headingId} ref={heading} tabIndex={-1}>
      {result ? selected ? "Recommended setup" : "Recommendation unavailable" : "Recommend a build"}</h2>
      <button type="button" aria-label="Close recommendation" onClick={close}>×</button></div>
    <div className="recommendation-body">
      <p className="recommendation-patch">Selected patch: {version ? `Ver. ${version.version}` : "Not selected"} · Reviewed rules: Ver. 1.4.1</p>
      <section aria-label="Locked selections"><h3>{result ? "Kept · locked selections" : "Locked selections"}</h3>
        {!lockedSlots.length && !locks.gadgetIds.length ? <p className="muted">Nothing is locked. All selections may change.</p>
          : <ul className="recommendation-locks">{lockedSlots.map(slot => <li key={slot.key}><strong>{slot.label}</strong>
            <SelectionItem id={locks[slot.key]} slot={slot.key} catalog={catalog} /><span>Locked</span></li>)}
            {locks.gadgetIds.map(id => <li key={id}><SelectionItem id={id} catalog={catalog} /><span>Locked gadget</span></li>)}</ul>}
        {!result && <p className="muted">Cancel to return to the editor and change locks.</p>}
      </section>
      <ErrorNotice message={recommendation.error} />
      {recommendation.busy ? <p className="recommendation-activity" role="status">Calculating recommendation…</p> : !result ? <>
        <label>Recommendation machine type<select value={machineType ?? ""} onChange={event => setMachineType((event.target.value || null) as RacingType | null)}>
          <option value="">Choose a machine type</option>{["SPEED", "ACCELERATION", "HANDLING", "POWER", "BOOST"].map(type =>
            <option key={type} value={type}>{racingTypeLabel(type as RacingType)}</option>)}</select></label>
        {conflict && <p role="alert" className="field-warning">{conflict}</p>}
        <h3>Stat priorities</h3><p>Maximizes your first stat. Lower priorities break ties.</p>
        <p className="muted">Drag the badges to reorder, or use the arrow buttons.</p>
        <ol className="recommendation-priorities">{priorities.map((priority, index) => <li key={priority}
          draggable className={draggedPriority === priority ? "dragging" : undefined}
          onDragStart={event => { event.dataTransfer.setData("text/plain", priority); event.dataTransfer.effectAllowed = "move"; setDraggedPriority(priority); }}
          onDragOver={event => { event.preventDefault(); event.dataTransfer.dropEffect = "move"; }}
          onDrop={event => { event.preventDefault(); const source = event.dataTransfer.getData("text/plain") as RacingType;
            setPriorities(current => movePriorityTo(current, source, priority)); setDraggedPriority(null); }}
          onDragEnd={() => setDraggedPriority(null)}>
          <span className="priority-drag-handle" aria-hidden="true">⠿</span>
          <span className={`type-badge racing-type ${racingTypeClass(priority)}`}>{racingTypeLabel(priority)}</span><div>
            <button type="button" aria-label={`Move ${racingTypeLabel(priority)} up`} disabled={index === 0}
              onClick={() => setPriorities(movePriority(priorities, index, -1))}>↑</button>
            <button type="button" aria-label={`Move ${racingTypeLabel(priority)} down`} disabled={index === 4}
              onClick={() => setPriorities(movePriority(priorities, index, 1))}>↓</button></div></li>)}</ol>
        {version?.version !== "1.4.1" && <p role="alert">Recommendation is unavailable for this patch. The selected draft patch is preserved.</p>}
      </> : <>
        {selected && <><h3>{result.alreadyBest ? "Current setup retained" : "Proposed changes"}</h3>
          <dl className="recommendation-changes">{slots.filter(slot => !(slot.key === "tirePartId" && machineType === "BOOST")).map(slot =>
            <div key={slot.key}><dt>{slot.label}{locks[slot.key] ? " · locked" : ""}</dt><dd>
              {draftSelection(draft)[slot.key] === selected[slot.key] ? <><SelectionItem id={selected[slot.key]} slot={slot.key} catalog={catalog} /><small>Kept</small></>
                : <><SelectionItem id={draftSelection(draft)[slot.key]} slot={slot.key} catalog={catalog} /><span aria-label="changes to">→</span><SelectionItem id={selected[slot.key]} slot={slot.key} catalog={catalog} /></>}
            </dd></div>)}
            <div><dt>Gadgets</dt><dd><span>{draft.gadgetIds.length ? draft.gadgetIds.map(id => <SelectionItem key={id} id={id} catalog={catalog} />) : "None"}</span>
              <span aria-label="changes to">→</span><span>{selected.gadgetIds.length ? selected.gadgetIds.map(id => <SelectionItem key={id} id={id} catalog={catalog} />) : "None"}</span></dd></div>
          </dl>{machineType === "BOOST" && <p>Boost uses Front and Rear only. The proposed setup has no tire.</p>}
          <table className="recommendation-stats"><caption>Passive-adjusted stats · same selected patch</caption><thead><tr>
            <th>Stat</th><th>Current</th><th>Recommended</th><th>Change</th></tr></thead><tbody>{priorities.map(priority => {
              const before = stat(result.currentStats, priority), after = stat(result.recommendedStats, priority);
              const difference = before === null || after === null ? null : Number((after - before).toFixed(8));
              return <tr key={priority}><th>{racingTypeLabel(priority)}</th><td>{before ?? "Unavailable"}</td><td>{after ?? "Unavailable"}</td>
                <td>{difference === null ? "Unavailable" : difference > 0 ? `+${difference}` : difference}</td></tr>;
            })}</tbody></table></>}
        <section aria-label="Recommendation explanation"><h3>Reason</h3><p>{result.reason}</p>
          <p>Machine type: {racingTypeLabel(machineType)}. All locked selections are hard constraints.</p>
          <ul>{result.restrictions.map(restriction => <li key={restriction}>{restriction}</li>)}</ul>
          <p>{result.note}</p><small>Ruleset: {result.ruleset} · {result.work} search steps · {result.elapsedMillis} ms</small></section>
      </>}
    </div>
    <div className="recommendation-actions">{!result && !recommendation.busy && <button type="button" className="primary"
      disabled={!!conflict || !machineType || !draft.gameVersionId || !validPriorities(priorities) || version?.version !== "1.4.1"}
      onClick={() => recommendation.calculate({ gameVersionId: draft.gameVersionId!, machineType: machineType!, priorities,
        current: draftSelection(draft), locked: locks })}>Calculate recommendation</button>}
      {selected && <button type="button" className="primary" onClick={() => { if (recommendation.apply()) onClose(); }}>Apply to draft</button>}
      {result && <button type="button" onClick={recommendation.cancel}>Back to priorities</button>}
      <button type="button" onClick={close}>Cancel</button></div>
    {selected && <p className="recommendation-draft-note">Apply changes only your unsaved draft. Publish or save separately.</p>}
  </dialog>, document.body);
}
