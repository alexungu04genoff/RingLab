import { draftSelection, recommendationSlots as slots } from "./recommendation";
import { SelectionItem } from "./RecommendationSelections";
import type { RecommendationCatalog, RecommendationSelection, RecommendationRequest } from "./recommendation";
import type { BuildDraft, RacingType } from "../../shared/types";

/** Selections remain visible even when kept; an incomplete draft has no Current column. */
export function RecommendationSelectionComparison({ reference, selected, locks, catalog, machineType, showCurrent, gadgetScope }: {
  reference: BuildDraft; selected: RecommendationSelection; locks: RecommendationSelection;
  catalog: RecommendationCatalog; machineType: RacingType | null; showCurrent: boolean; gadgetScope: NonNullable<RecommendationRequest["gadgetScope"]>;
}) {
  const current = draftSelection(reference);
  const removedGadgets = current.gadgetIds.filter(id => !selected.gadgetIds.includes(id));
  return <section className={`recommendation-selection-comparison${showCurrent ? "" : " recommended-only"}`} aria-label="Setup comparison">
    <h3>Setup comparison</h3>
    <p>{gadgetScope === "KEEP_CURRENT" ? "Gadgets deliberately kept, including an empty plate."
      : "Unlocked gadgets may be added, removed or replaced. Locked gadgets stay."}</p>
    <div className="recommendation-comparison-head" aria-hidden="true"><span>Selection</span>{showCurrent && <span>Current</span>}<span>Recommended</span></div>
    <dl>{slots.filter(slot => !(slot.key === "tirePartId" && machineType === "BOOST")).map(slot =>
      <div key={slot.key}>
        <dt>{slot.label}{locks[slot.key] && <small>Locked</small>}</dt>
        {showCurrent && <dd aria-label={`Current ${slot.label}`}><SelectionItem id={current[slot.key]} slot={slot.key} catalog={catalog} showType /></dd>}
        <dd aria-label={`Recommended ${slot.label}`}><SelectionItem id={selected[slot.key]} slot={slot.key} catalog={catalog} showType />
          <small className="recommendation-change-label">{current[slot.key] === selected[slot.key] ? "Kept" : current[slot.key] ? "Changed" : "Added"}</small></dd>
      </div>)}
      <div><dt>Gadgets</dt>
        {showCurrent && <dd aria-label="Current gadgets">{current.gadgetIds.length
          ? current.gadgetIds.map(id => <SelectionItem key={id} id={id} catalog={catalog} />) : "None"}</dd>}
        <dd aria-label="Recommended gadgets">{selected.gadgetIds.length ? selected.gadgetIds.map(id =>
          <div className="recommendation-gadget-change" key={id}><SelectionItem id={id} catalog={catalog} />
            <small>{current.gadgetIds.includes(id) ? "Kept" : "Added"}{locks.gadgetIds.includes(id) ? " · Locked" : ""}</small></div>)
          : <span>None{!current.gadgetIds.length && " · Kept"}</span>}
          {removedGadgets.map(id => <div className="recommendation-gadget-change" key={id}>
            <small>Removed</small><SelectionItem id={id} catalog={catalog} /></div>)}
        </dd>
      </div>
    </dl>
  </section>;
}
