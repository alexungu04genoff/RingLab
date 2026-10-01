import { useEffect, useId, useRef } from "react";
import { createPortal } from "react-dom";
import { ErrorNotice } from "../../shared/ui/ErrorNotice";
import { CollectionStatus } from "../collection/Collection";
import { useBuildRecommendation } from "./useBuildRecommendation";
import { useRecommendationConfiguration } from "./useRecommendationConfiguration";
import { RecommendationModeControls, RecommendationPriorityControls } from "./RecommendationControls";
import { RecommendationComparison } from "./RecommendationComparison";
import { RecommendationReference, SelectionItem } from "./RecommendationSelections";
import { draftSelection, recommendationSlots as slots } from "./recommendation";
import "../../styles/recommendation.css";
import type { RecommendationCatalog, RecommendationSelection } from "./recommendation";
import type { BuildDraft, BuildStatsResult, GameVersion } from "../../shared/types";

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
  const configuration = useRecommendationConfiguration(draft, locks, catalog, version, referenceStats);
  const { mode, setMode, machineType, activePriorities, secondary, losses, reference, referenceVersion,
    referenceIdentity, referenceAvailable, passiveReference, calculationBlocker } = configuration;
  const recommendation = useBuildRecommendation(draft, locks, context, catalog, onApply, configuration.identity, referenceIdentity);
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
  const lockedSlots = slots.filter(slot => locks[slot.key]);
  const selected = result?.selection;
  const close = () => { recommendation.cancel(); onClose(); };
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
      <a className="contextual-help recommendation-guide-link" href="/guide#recommendations" target="_blank" rel="noreferrer">How recommendations work ↗</a>
      {(!result || mode === "STRICT" || lockedSlots.length > 0 || locks.gadgetIds.length > 0) && <section aria-label="Locked selections"><h3>{result ? "Kept · locked selections" : "Locked selections"}</h3>
        {!lockedSlots.length && !locks.gadgetIds.length ? <p className="muted">Nothing is locked. All selections may change.</p>
          : <ul className="recommendation-locks">{lockedSlots.map(slot => <li key={slot.key}><strong>{slot.label}</strong>
            <SelectionItem id={locks[slot.key]} slot={slot.key} catalog={catalog} /><span>Locked</span></li>)}
            {locks.gadgetIds.map(id => <li key={id}><SelectionItem id={id} catalog={catalog} /><span>Locked gadget</span></li>)}</ul>}
      </section>}
      <ErrorNotice message={recommendation.error} />
      <CollectionStatus />
      <RecommendationModeControls configuration={configuration} hasResult={!!result}
        onModeChange={value => { recommendation.cancel(); setMode(value); }} />
      <RecommendationReference reference={reference} catalog={catalog} passiveReference={passiveReference} hasResult={!!result} />
      {recommendation.busy && <p className="recommendation-activity" role="status">Calculating recommendation…</p>}
      {!result ? <RecommendationPriorityControls configuration={configuration} headingId={headingId} />
        : <RecommendationComparison result={result} reference={reference} locks={locks} catalog={catalog}
          machineType={machineType} mode={mode} activePriorities={activePriorities} secondary={secondary} losses={losses} />}
    </div>
    <div className="recommendation-actions">
      {!result && !recommendation.busy && calculationBlocker && <p id={blockerId} className="recommendation-blocker" role="status">{calculationBlocker}</p>}
      {!result && !recommendation.busy && <button type="button" className="primary"
      disabled={!!calculationBlocker} aria-describedby={calculationBlocker ? blockerId : undefined}
      onClick={() => recommendation.calculate({ gameVersionId: reference.gameVersionId!, machineType: machineType!, priorities: activePriorities,
        current: draftSelection(reference), locked: locks, ...(mode === "BALANCED" ? { mode, balanced: {
          maximumLossPercent: Object.fromEntries(activePriorities.map(stat => [stat, Number(losses[stat])])), secondary } } : {}) })}>Calculate recommendation</button>}
      {selected && <button type="button" className="primary" disabled={recommendation.busy} onClick={async () => { if (await recommendation.apply()) onClose(); }}>Apply to draft</button>}
      {result && <button type="button" onClick={recommendation.cancel}>Back to priorities</button>}
      {!result && mode === "BALANCED" && !referenceAvailable && passiveReference &&
        <button type="button" onClick={() => { recommendation.cancel(); setMode("STRICT"); }}>Use Strict</button>}
      <button type="button" onClick={close}>{!result && mode === "BALANCED" && !referenceAvailable && passiveReference ? "Edit starting setup" : "Cancel"}</button></div>
    {selected && <p className="recommendation-draft-note">Apply changes only your unsaved draft. Publish or save separately.</p>}
  </dialog>, document.body);
}
