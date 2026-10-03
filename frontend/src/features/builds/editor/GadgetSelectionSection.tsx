import { useEffect, useState } from "react";
import { CollectionArtwork as Artwork } from "../../collection/CollectionArtwork";
import { GadgetAdjustmentBadges, GadgetCatalogAdjustmentBadges, GadgetCatalogTypeLabels } from "../../stats/PassiveStats";
import { SelectionLock } from "../../recommendations/SelectionLock";
import type { RecommendationSelection } from "../../recommendations/recommendation";
import { filterGadgets, toggleGadget } from "../buildForm";
import type { BuildDraft, BuildStatsResult, Gadget, GadgetRulesCatalog, RacingType } from "../../../shared/types";

export function GadgetSelectionSection({ draft, gadgets, gadgetRules, passiveStats, gadgetTypeSelection,
  locks, setLocks, ownershipLabel, onChange, draftContext }: {
  draft: BuildDraft; gadgets?: Gadget[]; gadgetRules?: GadgetRulesCatalog;
  passiveStats?: BuildStatsResult["passive"];
  gadgetTypeSelection: { racerType: RacingType | null; machineType: RacingType | null };
  locks: RecommendationSelection; setLocks: (locks: RecommendationSelection) => void;
  ownershipLabel: (category: "gadgets", id: string) => string;
  onChange: (ids: string[]) => void; draftContext: string;
}) {
  const [gadgetSearch, setGadgetSearch] = useState("");
  useEffect(() => setGadgetSearch(""), [draftContext]);
  return (
    <section className="panel">
      <h2>
        <span className="step">03</span> Gadgets{" "}
        <span className="muted">optional</span>
      </h2>
      <p>Select gadgets in the order you want them displayed.</p>
      <label className="gadget-search">
        <span className="sr-only">Search gadgets</span>
        <input type="search" value={gadgetSearch} onChange={(e) => setGadgetSearch(e.target.value)}
          placeholder="Search gadgets..." />
      </label>
      <div className="gadget-options">
        {filterGadgets(gadgets ?? [], gadgetSearch, draft.gadgetIds).map((g) => (
          <div className={`gadget-choice ${draft.gadgetIds.includes(g.id) ? "has-lock" : ""}`} key={g.id}>
          {draft.gadgetIds.includes(g.id) && <SelectionLock label={g.name} locked={locks.gadgetIds.includes(g.id)}
            onClick={() => setLocks({ ...locks, gadgetIds: toggleGadget(locks.gadgetIds, g.id) })} />}
          <label
            className={`gadget-option ${draft.gadgetIds.includes(g.id) ? "selected" : ""}`}
          >
            <input
              type="checkbox"
              checked={draft.gadgetIds.includes(g.id)}
              disabled={locks.gadgetIds.includes(g.id)}
              onChange={() =>
                onChange(toggleGadget(draft.gadgetIds, g.id))
              }
            />
            <Artwork item={g} compact />
            <span className="gadget-option-copy"><strong>{g.name}{ownershipLabel("gadgets", g.id)}</strong>
              <span className="gadget-option-meta">
                <small className="gadget-slot-badge">{g.slotCost === null ? "Cost unknown" : `${g.slotCost} ${g.slotCost === 1 ? "slot" : "slots"}`}</small>
                <GadgetCatalogTypeLabels gadgetId={g.id} catalog={gadgetRules} selection={gadgetTypeSelection} />
              </span>
              {g.description && <span>{g.description}</span>}
              {draft.gadgetIds.includes(g.id) && passiveStats
                ? <GadgetAdjustmentBadges gadgetId={g.id} value={passiveStats} catalog={gadgetRules} selection={gadgetTypeSelection} />
                : <GadgetCatalogAdjustmentBadges gadgetId={g.id} catalog={gadgetRules} selection={gadgetTypeSelection} />}
            </span>
          </label>
          </div>
        ))}
      </div>
      <p className="muted">
        Display order does not affect Gadget Plate validity.
      </p>
    </section>
  );
}
