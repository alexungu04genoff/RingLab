import { CollectionArtwork as Artwork } from "../collection/CollectionArtwork";
import { RacingTypeBadge } from "../../shared/ui/RacingTypeBadge";
import { GadgetAdjustmentBadges } from "../stats/PassiveStats";
import { draftSelection, recommendationSlots as slots } from "./recommendation";
import type { ComponentKey, RecommendationCatalog } from "./recommendation";
import type { BuildDraft, BuildStatsResult } from "../../shared/types";

export function SelectionItem({ id, slot, catalog, showType = false }: {
  id: string | null; slot?: ComponentKey; catalog: RecommendationCatalog; showType?: boolean;
}) {
  const racer = slot === "racerId" ? catalog.racers.find(item => item.id === id) : undefined;
  const part = slot && slot !== "racerId" ? catalog.parts.find(item => item.id === id) : undefined;
  const gadget = !slot ? catalog.gadgets.find(item => item.id === id) : undefined;
  const item = racer ?? gadget ?? (part ? { id: part.sourceMachineId, name: part.sourceMachineName, racingType: part.racingType, imagePath: part.sourceMachineImagePath } : null);
  return <span className={`recommendation-item${gadget ? " recommendation-gadget-item" : ""}`}>{item && <Artwork item={item} category={racer ? "RACER" : gadget ? "GADGET" : "MACHINE"} compact />}
    <span className={showType ? "recommendation-item-copy" : undefined}>
      <span>{item?.name ?? (id ? "Unknown selection" : "Not selected")}</span>
      {showType && (racer || part) && <RacingTypeBadge kind={racer ? "racer" : "machine"}
        type={racer?.racingType ?? part?.racingType ?? null} />}
    </span>
    {gadget && <small className="gadget-slot-badge recommendation-gadget-cost">
      {gadget.slotCost === null ? "Cost unknown" : `${gadget.slotCost} ${gadget.slotCost === 1 ? "slot" : "slots"}`}
    </small>}</span>;
}

export function RecommendationReference({ reference, catalog, passiveReference, hasResult }: {
  reference: BuildDraft; catalog: RecommendationCatalog; passiveReference?: BuildStatsResult["passive"];
  hasResult: boolean;
}) {
  return (
    <section aria-label="Starting setup" className="balanced-reference">
      <details key={hasResult ? "result" : "configuration"} open={!hasResult}>
      <summary>Starting setup <span className="recommendation-info-icon" role="img"
        aria-label="This setup is shown for comparison; it does not set Balanced thresholds."
        title="This setup is shown for comparison; it does not set Balanced thresholds.">i</span></summary>
      <p>Selections and patch from when you opened this popup. Current stats are for comparison only.</p>
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
      </details></section>
  );
}
