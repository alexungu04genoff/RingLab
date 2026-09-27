import { useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Artwork, ErrorNotice, racingTypeClass, racingTypeLabel } from "./components";
import { useBuildRecommendation } from "./useBuildRecommendation";
import { defaultPriorities, draftSelection, lockTypeConflict, movePriority, movePriorityTo, validPriorities, validBalanced, signedStatChange, recommendationIdentity } from "./recommendation";
import { useLoad } from "./useLoad";
import { passiveStatsPath } from "./stats";
import { RacingTypeBadge } from "./RacingTypeBadge";
import { GadgetAdjustmentBadges } from "./PassiveStats";
import "./balancedRecommendation.css";
import type { ComponentKey, RecommendationCatalog, RecommendationSelection } from "./recommendation";
import type { BaseStats, BuildDraft, BuildStatsResult, GameVersion, RacingType } from "./types";

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
function SelectionItem({ id, slot, catalog, showType = false }: {
  id: string | null; slot?: ComponentKey; catalog: RecommendationCatalog; showType?: boolean;
}) {
  const racer = slot === "racerId" ? catalog.racers.find(item => item.id === id) : undefined;
  const part = slot && slot !== "racerId" ? catalog.parts.find(item => item.id === id) : undefined;
  const gadget = !slot ? catalog.gadgets.find(item => item.id === id) : undefined;
  const item = racer ?? gadget ?? (part ? { id: part.id, name: part.sourceMachineName, racingType: part.racingType, imagePath: part.sourceMachineImagePath } : null);
  return <span className="recommendation-item">{item && <Artwork item={item} compact />}
    <span className={showType ? "recommendation-item-copy" : undefined}>
      <span>{item?.name ?? (id ? "Unknown selection" : "Not selected")}</span>
      {showType && (racer || part) && <RacingTypeBadge kind={racer ? "racer" : "machine"}
        type={racer?.racingType ?? part?.racingType ?? null} />}
    </span></span>;
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

export function BuildRecommendationDialog({ draft, locks, context, catalog, version, referenceStats, returnFocus, onClose, onApply }: {
  draft: BuildDraft; locks: RecommendationSelection; context: string; catalog: RecommendationCatalog;
  version: GameVersion | null; referenceStats?: BuildStatsResult | null; returnFocus: HTMLButtonElement | null;
  onClose: () => void; onApply: (draft: BuildDraft) => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const body = useRef<HTMLDivElement>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const headingId = useId();
  const blockerId = useId();
  const [machineType, setMachineType] = useState<RacingType | null>(draft.machineType);
  const [priorities, setPriorities] = useState(() => {
    const current = referenceStats?.passive?.coverage === "CALCULATED" ? referenceStats.passive.adjusted : null;
    const value = (priority: RacingType) => {
      const points = current?.[priority.toLowerCase() as keyof BaseStats];
      return points != null && Number.isFinite(points) ? points : -Infinity;
    };
    return [...defaultPriorities].sort((left, right) => {
      const a = value(left), b = value(right);
      return a === b ? 0 : a > b ? -1 : 1;
    });
  });
  const [mode, setMode] = useState<"STRICT" | "BALANCED">("STRICT");
  const [secondary, setSecondary] = useState<RacingType[]>([]);
  const [losses, setLosses] = useState<Record<RacingType, string>>({ SPEED: "0", ACCELERATION: "0", HANDLING: "0", BOOST: "0", POWER: "0" });
  const [reference] = useState(() => ({ ...draft, gadgetIds: [...draft.gadgetIds] }));
  // Freeze the selected patch ID, but allow its catalog entry to finish loading.
  const referenceVersion = version?.id === reference.gameVersionId ? version : null;
  const [referenceIdentity] = useState(() => recommendationIdentity(draft, locks));
  const loadedReferenceStats = useLoad<BuildStatsResult>(mode === "BALANCED" && !referenceStats ? passiveStatsPath(reference) : "");
  const passiveReference = referenceStats?.passive ?? loadedReferenceStats.data?.passive;
  const referenceValues = passiveReference?.coverage === "CALCULATED" ? passiveReference.adjusted : null;
  const referenceAvailable = !!referenceValues && defaultPriorities.every(priority => {
    const value = referenceValues[priority.toLowerCase() as keyof BaseStats];
    return value != null && Number.isFinite(value) && value >= 0;
  });
  const activePriorities = mode === "BALANCED" ? priorities.filter(priority => !secondary.includes(priority)) : priorities;
  const [draggedPriority, setDraggedPriority] = useState<RacingType | null>(null);
  const configuration = JSON.stringify([mode, machineType, priorities, secondary, losses]);
  const recommendation = useBuildRecommendation(draft, locks, context, catalog, onApply, configuration, referenceIdentity);
  const result = recommendation.result;
  useEffect(() => {
    const element = dialog.current!;
    const previousOverflow = document.body.style.overflow;
    element.showModal(); heading.current?.focus(); document.body.style.overflow = "hidden";
    return () => { element.close(); document.body.style.overflow = previousOverflow; returnFocus?.focus(); };
  }, [returnFocus]);
  useEffect(() => {
    if (result) {
      heading.current?.focus();
      if (body.current) body.current.scrollTop = 0;
    }
  }, [result]);
  const conflict = lockTypeConflict(machineType, locks, catalog.parts);
  const lockedSlots = slots.filter(slot => locks[slot.key]);
  const selected = result?.selection;
  const comparisonScale = Math.max(100, ...Object.values(result?.currentStats ?? {}), ...Object.values(result?.recommendedStats ?? {}));
  const ExplanationContainer = mode === "BALANCED" ? "details" : "section";
  const close = () => { recommendation.cancel(); onClose(); };
  const stat = (stats: BaseStats | null | undefined, priority: RacingType) => stats?.[priority.toLowerCase() as keyof BaseStats] ?? null;
  let calculationBlocker = "";
  if (referenceIdentity !== recommendationIdentity(draft, locks))
    calculationBlocker = "Your draft or locks changed after this popup opened. Close and reopen it to use your current setup.";
  else if (!reference.gameVersionId)
    calculationBlocker = "Close this popup and select Ver. 1.4.1 in the editor's Game version / Patch field.";
  else if (!referenceVersion)
    calculationBlocker = "The selected patch details are not available yet. Wait for the patch list to load, or close and reopen this popup.";
  else if (referenceVersion.version !== "1.4.1")
    calculationBlocker = "Recommendations support Ver. 1.4.1. Close this popup to change the editor's patch.";
  else if (!machineType)
    calculationBlocker = "Choose a recommendation machine type.";
  else if (conflict)
    calculationBlocker = conflict;
  else if (!validPriorities(priorities))
    calculationBlocker = "Rank all five stats before calculating.";
  else if (mode === "BALANCED") {
    if (loadedReferenceStats.loading)
      calculationBlocker = "Loading the current setup's stats…";
    else if (loadedReferenceStats.error)
      calculationBlocker = `Could not load reference stats: ${loadedReferenceStats.error}`;
    else if (!referenceAvailable)
      calculationBlocker = "Balanced needs a complete setup with supported, nonnegative stats. Close this popup to complete your racer and parts, or use Strict to generate a setup.";
    else if (!validBalanced(activePriorities, secondary, losses))
      calculationBlocker = "Enter a maximum loss from 0 to 100 for each prioritized stat. Leave 0 to allow no decrease.";
  }
  return createPortal(<dialog className={`build-recommendation-dialog${mode === "BALANCED" && result ? " balanced-result" : ""}`} ref={dialog} aria-labelledby={headingId}
    onKeyDown={event => {
      if (event.key !== "Tab" || event.altKey || event.ctrlKey || event.metaKey) return;
      const controls = [...event.currentTarget.querySelectorAll<HTMLElement>("button, a[href], input, select, textarea, summary, [tabindex]")]
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
      <button type="button" aria-label="Close recommendation" onClick={close}>
        <svg width="20" height="20" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
          <path d="m5 5 10 10M15 5 5 15" />
        </svg>
      </button></div>
    <div className="recommendation-body" ref={body}>
      <p className="recommendation-patch">Selected patch: {referenceVersion ? `Ver. ${referenceVersion.version}` : "Not selected"} · Reviewed rules: Ver. 1.4.1</p>
      {(!result || mode === "STRICT" || lockedSlots.length > 0 || locks.gadgetIds.length > 0) && <section aria-label="Locked selections"><h3>{result ? "Kept · locked selections" : "Locked selections"}</h3>
        {!lockedSlots.length && !locks.gadgetIds.length ? <p className="muted">Nothing is locked. All selections may change.</p>
          : <ul className="recommendation-locks">{lockedSlots.map(slot => <li key={slot.key}><strong>{slot.label}</strong>
            <SelectionItem id={locks[slot.key]} slot={slot.key} catalog={catalog} /><span>Locked</span></li>)}
            {locks.gadgetIds.map(id => <li key={id}><SelectionItem id={id} catalog={catalog} /><span>Locked gadget</span></li>)}</ul>}
      </section>}
      <ErrorNotice message={recommendation.error} />
      <div className="recommendation-toolbar">
      <div className="recommendation-modes" role="group" aria-label="Recommendation mode">
        {(["STRICT", "BALANCED"] as const).map(value => <button type="button" key={value} aria-pressed={mode === value}
          onClick={() => { recommendation.cancel(); setMode(value); }}>{value === "STRICT" ? "Strict" : "Balanced"}</button>)}
      </div>
      {!result && <label className="recommendation-machine-type">Recommendation machine type<select value={machineType ?? ""} onChange={event => setMachineType((event.target.value || null) as RacingType | null)}>
        <option value="">Choose a machine type</option>{["SPEED", "ACCELERATION", "HANDLING", "POWER", "BOOST"].map(type =>
          <option key={type} value={type}>{racingTypeLabel(type as RacingType)}</option>)}</select></label>}
      </div>
      <section aria-label="Frozen reference" className="balanced-reference">
        <details key={result ? "result" : "configuration"} open={!result}>
        <summary>Starting setup <span className="recommendation-info-icon" role="img"
          aria-label="This setup stays fixed for this recommendation session."
          title="This setup stays fixed for this recommendation session.">i</span></summary>
        <p>Selections and patch from when you opened this popup. Recalculating never compounds losses.</p>
        <div className="balanced-reference-items">{slots.filter(slot => draftSelection(reference)[slot.key]).map(slot =>
          <span key={slot.key}><strong>{slot.label}</strong><SelectionItem id={draftSelection(reference)[slot.key]} slot={slot.key} catalog={catalog} showType /></span>)}</div>
        <section className="recommendation-reference-gadgets" aria-label="Starting gadgets">
          <h4>Gadgets <span>· {reference.gadgetIds.length}</span></h4>
          <div className="balanced-reference-items">
          {reference.gadgetIds.map(id => <span key={id} className="recommendation-reference-gadget">
            <SelectionItem id={id} catalog={catalog} />
            {catalog.gadgets.find(gadget => gadget.id === id)?.description &&
              <span className="recommendation-gadget-description">{catalog.gadgets.find(gadget => gadget.id === id)?.description}</span>}
            {passiveReference?.effects && <GadgetAdjustmentBadges gadgetId={id} value={passiveReference} />}
          </span>)}</div>
          {!reference.gadgetIds.length && <p className="muted">None selected</p>}
        </section>
        {mode === "BALANCED" && (loadedReferenceStats.loading ? <p role="status">Loading reference stats…</p> : !referenceAvailable &&
          <p role="alert">Balanced unavailable: choose a complete legal reference with fully supported, nonnegative passive-adjusted values. {loadedReferenceStats.error}</p>)}
        </details></section>
      {recommendation.busy && <p className="recommendation-activity" role="status">Calculating recommendation…</p>}
      {!result ? <>
        <h3>Stat priorities</h3>{mode === "STRICT" ? <p>Maximizes your first stat. Lower priorities break ties.</p>
          : <><p>Rank your stats and set how much each may decrease. Every minimum is enforced.</p>
            <details className="balanced-scoring-details"><summary>How balanced scoring works</summary>
              <p>Rank weights are {activePriorities.map((_, index) => activePriorities.length - index).join(", ")}. Weighted relative gains may compensate for allowed losses, but every minimum is mandatory. Loss limits do not set weights. A zero reference uses one stat point for normalization and shows point changes.</p>
            </details></>}
        <p className="muted">Drag the stats to reorder, or use the arrow buttons.</p>
        {mode === "STRICT" ? <ol className="recommendation-priorities">{activePriorities.map((priority, index) => <li key={priority}
          draggable className={draggedPriority === priority ? "dragging" : undefined}
          onDragStart={event => { event.dataTransfer.setData("text/plain", priority); event.dataTransfer.effectAllowed = "move"; setDraggedPriority(priority); }}
          onDragOver={event => { event.preventDefault(); event.dataTransfer.dropEffect = "move"; }}
          onDrop={event => { event.preventDefault(); const source = event.dataTransfer.getData("text/plain") as RacingType;
            setPriorities(current => movePriorityTo(current, source, priority)); setDraggedPriority(null); }}
          onDragEnd={() => setDraggedPriority(null)}>
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
              onDragStart={event => { event.dataTransfer.setData("text/plain", priority); event.dataTransfer.effectAllowed = "move"; setDraggedPriority(priority); }}
              onDragOver={event => { event.preventDefault(); event.dataTransfer.dropEffect = "move"; }}
              onDrop={event => { event.preventDefault(); const source = event.dataTransfer.getData("text/plain") as RacingType;
                setPriorities(current => movePriorityTo(current, source, priority)); setDraggedPriority(null); }}
              onDragEnd={() => setDraggedPriority(null)}>
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
      </> : <>
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
            <p className="recommendation-stat-legend">Starting value → new value · <span className="recommendation-gain">+ Gain</span> · <span className="recommendation-loss">− Loss</span></p>
            {[...activePriorities, ...(mode === "BALANCED" ? secondary : [])].map(priority => {
              const before = stat(result.currentStats, priority), after = stat(result.recommendedStats, priority);
              const difference = before === null || after === null ? null : Number((after - before).toFixed(8));
              const changeClass = difference === null || difference === 0 ? "recommendation-neutral" : difference > 0 ? "recommendation-gain" : "recommendation-loss";
              return <div key={priority} className={`recommendation-stat-row stat-row stat-${priority.toLowerCase()}`}>
                <div className="recommendation-stat-label"><strong>{racingTypeLabel(priority)}</strong>
                  <span>{before ?? "Unavailable"} → <strong>{after ?? "Unavailable"}</strong></span>
                  <span className={`recommendation-stat-delta ${changeClass}`}>{difference === null ? "Unavailable" : difference === 0 ? "No change" : `${difference > 0 ? "+" : ""}${difference}`}
                    {mode === "BALANCED" && difference !== null && difference !== 0 && <small> ({signedStatChange(before, after)})</small>}</span>
                </div>
                <div className="recommendation-stat-track" role="img" aria-label={`${racingTypeLabel(priority)}: ${before ?? "Unavailable"} to ${after ?? "Unavailable"}`}>
                  {before !== null && after !== null && <>
                    <span className="recommendation-stat-base" style={{ width: `${Math.max(0, Math.min(before, after)) / comparisonScale * 100}%` }} />
                    <span className={`recommendation-stat-segment ${changeClass}`} style={{ left: `${Math.max(0, Math.min(before, after)) / comparisonScale * 100}%`, width: `${Math.abs(difference!) / comparisonScale * 100}%` }} />
                    <span className="recommendation-stat-marker" style={{ left: `${Math.max(0, before) / comparisonScale * 100}%` }} />
                  </>}
                </div>
                {mode === "BALANCED" && <small className="recommendation-stat-limit">{secondary.includes(priority) ? "Tie-break / Ignore — no minimum"
                  : `Maximum loss ${losses[priority]}% · Minimum allowed ${result.balanced?.minimum[priority] ?? "Unavailable"}`}</small>}
              </div>;
            })}</section>
          {mode === "BALANCED" && <BalancedSelectionChanges reference={reference} selected={selected} locks={locks} catalog={catalog} machineType={machineType} />}</>}
        <ExplanationContainer aria-label="Recommendation explanation" className={mode === "BALANCED" ? "balanced-explanation" : undefined}>
          {mode === "BALANCED" ? <summary>Why this setup</summary> : <h3>Reason</h3>}<p>{result.reason}</p>
          <p>Machine type: {racingTypeLabel(machineType)}. All locked selections are hard constraints.</p>
          <ul>{result.restrictions.map(restriction => <li key={restriction}>{restriction}</li>)}</ul>
          <p>{result.note}</p><small>Ruleset: {result.ruleset} · {result.work} search steps · {result.elapsedMillis} ms</small></ExplanationContainer>
      </>}
    </div>
    <div className="recommendation-actions">
      {!result && !recommendation.busy && calculationBlocker && <p id={blockerId} className="recommendation-blocker" role="status">{calculationBlocker}</p>}
      {!result && !recommendation.busy && <button type="button" className="primary"
      disabled={!!calculationBlocker} aria-describedby={calculationBlocker ? blockerId : undefined}
      onClick={() => recommendation.calculate({ gameVersionId: reference.gameVersionId!, machineType: machineType!, priorities: activePriorities,
        current: draftSelection(reference), locked: locks, ...(mode === "BALANCED" ? { mode, balanced: {
          maximumLossPercent: Object.fromEntries(activePriorities.map(stat => [stat, Number(losses[stat])])), secondary } } : {}) })}>Calculate recommendation</button>}
      {selected && <button type="button" className="primary" onClick={() => { if (recommendation.apply()) onClose(); }}>Apply to draft</button>}
      {result && <button type="button" onClick={recommendation.cancel}>Back to priorities</button>}
      <button type="button" onClick={close}>Cancel</button></div>
    {selected && <p className="recommendation-draft-note">Apply changes only your unsaved draft. Publish or save separately.</p>}
  </dialog>, document.body);
}
