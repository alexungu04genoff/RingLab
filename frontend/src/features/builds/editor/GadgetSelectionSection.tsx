import { useEffect, useState } from "react";
import { CollectionArtwork as Artwork } from "../../collection/CollectionArtwork";
import { OwnershipStatusBadge } from "../../collection/OwnershipStatusBadge";
import { isExcluded, useCollection } from "../../collection/Collection";
import { GadgetAdjustmentBadges, GadgetCatalogAdjustmentBadges, GadgetCatalogTypeLabels } from "../../stats/PassiveStats";
import { SelectionLock } from "../../recommendations/SelectionLock";
import { GadgetMetadata, GadgetMetadataLegend } from "../../gadgets/GadgetMetadata";
import { GadgetFilterControls } from "../../gadgets/GadgetFilterControls";
import { gadgetPresentation, unsupportedGadgetsLast } from "../../gadgets/gadgetPresentation";
import { emptyGadgetFilters, matchesGadgetFilters } from "../../gadgets/gadgetFilters";
import type { RecommendationSelection } from "../../recommendations/recommendation";
import { filterGadgets, toggleGadget } from "../buildForm";
import type { BuildDraft, BuildStatsResult, Gadget, GadgetRulesCatalog, ScenarioRulesCatalog, RacingType } from "../../../shared/types";

export function GadgetSelectionSection({ draft, gadgets, gadgetRules, scenarioRules, passiveStats, gadgetTypeSelection,
  locks, setLocks, onChange, draftContext }: {
  draft: BuildDraft; gadgets?: Gadget[]; gadgetRules?: GadgetRulesCatalog;
  scenarioRules?: ScenarioRulesCatalog;
  passiveStats?: BuildStatsResult["passive"];
  gadgetTypeSelection: { racerType: RacingType | null; machineType: RacingType | null };
  locks: RecommendationSelection; setLocks: (locks: RecommendationSelection) => void;
  onChange: (ids: string[]) => void; draftContext: string;
}) {
  const collection = useCollection();
  const [gadgetSearch, setGadgetSearch] = useState("");
  const [filters, setFilters] = useState(emptyGadgetFilters);
  useEffect(() => { setGadgetSearch(""); setFilters(emptyGadgetFilters); }, [draftContext]);
  const catalog = gadgets ?? [];
  const selected = draft.gadgetIds.map(id => catalog.find(gadget => gadget.id === id))
    .filter((gadget): gadget is Gadget => gadget !== undefined);
  const available = unsupportedGadgetsLast(filterGadgets(catalog, gadgetSearch).filter(gadget => !draft.gadgetIds.includes(gadget.id)
    && matchesGadgetFilters(gadgetPresentation(gadget, gadgetRules, scenarioRules), filters)), gadgetRules);
  const renderGadget = (g: Gadget) => (
    <div className={`gadget-choice ${draft.gadgetIds.includes(g.id) ? "has-lock" : ""}`} key={g.id}>
      {draft.gadgetIds.includes(g.id) && <SelectionLock label={g.name} locked={locks.gadgetIds.includes(g.id)}
        onClick={() => setLocks({ ...locks, gadgetIds: toggleGadget(locks.gadgetIds, g.id) })} />}
      <label className={`gadget-option ${draft.gadgetIds.includes(g.id) ? "selected" : ""}`}>
        <input type="checkbox" checked={draft.gadgetIds.includes(g.id)} disabled={locks.gadgetIds.includes(g.id)}
          onChange={() => onChange(toggleGadget(draft.gadgetIds, g.id))} />
        <Artwork item={g} compact />
        <span className="gadget-option-copy"><strong>{g.name}</strong>
          <span className="gadget-option-meta">
            <OwnershipStatusBadge notOwned={collection.status === "ready" && isExcluded(collection.data, "GADGET", g.id)} />
            <small className="gadget-slot-badge">{g.slotCost === null ? "Cost unknown" : `${g.slotCost} ${g.slotCost === 1 ? "slot" : "slots"}`}</small>
            <GadgetCatalogTypeLabels gadgetId={g.id} catalog={gadgetRules} selection={gadgetTypeSelection} />
            <GadgetMetadata gadget={g} rules={gadgetRules} scenarios={scenarioRules} />
          </span>
          {g.description && <span>{g.description}</span>}
          {draft.gadgetIds.includes(g.id) && passiveStats
            ? <GadgetAdjustmentBadges gadgetId={g.id} value={passiveStats} catalog={gadgetRules} selection={gadgetTypeSelection} />
            : <GadgetCatalogAdjustmentBadges gadgetId={g.id} catalog={gadgetRules} selection={gadgetTypeSelection} />}
        </span>
      </label>
    </div>
  );
  return (
    <section className="panel">
      <h2>
        <span className="step">03</span> Gadgets{" "}
        <span className="muted">optional</span>
      </h2>
      <p>Select gadgets in the order you want them displayed.</p>
      <GadgetMetadataLegend />
      <label className="gadget-search">
        <span className="sr-only">Search gadgets</span>
        <input type="search" value={gadgetSearch} onChange={(e) => setGadgetSearch(e.target.value)}
          placeholder="Search gadgets..." />
      </label>
      <GadgetFilterControls filters={filters} onChange={setFilters} />
      <p className="gadget-result-count" role="status">Showing {selected.length + available.length} of {catalog.length} gadgets · selected gadgets stay visible.</p>
      <section className="gadget-selection-group" aria-label="Selected gadgets">
        <h3>Selected · {selected.length}</h3>
        {selected.length === 0 && <p className="muted">No gadgets selected.</p>}
        <div className="gadget-options">{selected.map(renderGadget)}</div>
      </section>
      <section className="gadget-selection-group" aria-label="Available gadgets">
        <h3>Available gadgets · {available.length}</h3>
        {available.length === 0 && <p className="muted">No available gadgets match your search and filters.</p>}
        <div className="gadget-options">{available.map(renderGadget)}</div>
      </section>
      <p className="muted">
        Display order does not affect Gadget Plate validity.
      </p>
    </section>
  );
}
