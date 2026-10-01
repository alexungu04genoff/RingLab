import { useEffect, useState, type ReactNode } from "react";
import { CollectionArtwork as Artwork } from "../../collection/CollectionArtwork";
import { DraftStats } from "../../stats/BaseStats";
import { ScenarioPreview } from "../../stats/ScenarioPreview";
import { GadgetAdjustmentBadges } from "../../stats/PassiveStats";
import { RecommendedMapList } from "../../maps/MapRecommendations";
import { racingTypeClass } from "../../../shared/ui/RacingTypeBadge";
import { moveGadget, moveGadgetTo } from "../buildForm";
import type { gadgetPlateStatus } from "../buildForm";
import { visibleMachinePartSlots } from "../machineComposition";
import type { BuildDraft, BuildStatsResult, Gadget, GadgetRulesCatalog, GameVersion, MachinePart, Racer, RaceMap } from "../../../shared/types";

export function BuildEditorPreview({ draft, selectedRacer, plateStatus, parts, gadgets, gadgetRules, maps, mapsError,
  version, draftStats, draftContext, onGadgetsChange, recommendationAction, saveAction }: {
  draft: BuildDraft; selectedRacer?: Racer; plateStatus: ReturnType<typeof gadgetPlateStatus>;
  parts?: MachinePart[]; gadgets?: Gadget[]; gadgetRules?: GadgetRulesCatalog;
  maps?: RaceMap[]; mapsError: string; version: GameVersion | null;
  draftStats: { data?: BuildStatsResult; loading: boolean; error: string }; draftContext: string;
  onGadgetsChange: (ids: string[]) => void; recommendationAction: ReactNode; saveAction: ReactNode;
}) {
  const [gadgetDrag, setGadgetDrag] = useState<{ source: number; target: number } | null>(null);
  useEffect(() => setGadgetDrag(null), [draftContext]);
  const activePartSlots = visibleMachinePartSlots(draft.machineType);
  return (
    <aside className="panel selection build-preview">
      {recommendationAction}
      <div className="build-preview-scroll" role="region" aria-label="Build summary details" tabIndex={0}>
      <div className="eyebrow accent">YOUR COMBINATION</div>
      <h2>{draft.title || "Untitled build"}</h2>
      <DraftStats draft={draft} version={version} loadedResult={draftStats} />
      <ScenarioPreview selections={[{ label: "Current draft", selection: draft }]} />
      <div className="preview-core">
        <section className="preview-item">
          <span className="preview-label">Selected racer</span>
          {selectedRacer ? (
            <Artwork item={selectedRacer} portrait />
          ) : (
            <div className="preview-placeholder" aria-hidden="true">
              R
            </div>
          )}
          <strong>{selectedRacer?.name || "Choose a racer"}</strong>
          {selectedRacer ? (
            <span className={`preview-meta racing-type-text ${racingTypeClass(selectedRacer.racingType)}`}>
              {selectedRacer.racingType?.toLowerCase() ?? "Unknown"}
            </span>
          ) : (
            <span className="preview-meta">Your driver appears here</span>
          )}
        </section>
        <section className="preview-item">
          <span className="preview-label">Machine setup</span>
          <div className="preview-parts">
            {activePartSlots.map((slot) => {
              const selectedPart = parts?.find((part) => part.id === draft[slot.key]);
              return <div className={selectedPart ? "has-selection" : ""} key={slot.key}>
                <span>{slot.label}</span>
                {selectedPart && <Artwork item={{ id: selectedPart.sourceMachineId, name: selectedPart.sourceMachineName,
                  imagePath: selectedPart.sourceMachineImagePath, racingType: selectedPart.racingType }} compact />}
                <strong>{selectedPart?.sourceMachineName || "Choose a source machine"}</strong>
              </div>;
            })}
          </div>
        </section>
      </div>
      <h3>Gadgets · {draft.gadgetIds.length}</h3>
      <p className={`cost-summary ${plateStatus.valid ? "valid" : "invalid"}`}>
        {plateStatus.summary}
      </p>
      {draft.gadgetIds.length === 0 && (
        <p className="preview-empty">
          Select gadgets to add them to the loadout.
        </p>
      )}
      <ol className="selected-gadgets preview-gadgets">
        {draft.gadgetIds.map((gadgetId, i) => {
          const gadget = gadgets?.find((item) => item.id === gadgetId);
          return (
            <li key={`${gadgetId}-${i}`}
              className={gadgetDrag?.source === i ? "dragging" : gadgetDrag?.target === i ? "drag-target" : undefined}
              onDragEnter={() => setGadgetDrag(current => current ? { ...current, target: i } : null)}
              onDragOver={event => { event.preventDefault(); event.dataTransfer.dropEffect = "move"; }}
              onDrop={event => {
                event.preventDefault();
                const sourceValue = event.dataTransfer.getData("text/plain");
                const source = Number(sourceValue);
                if (sourceValue !== "" && Number.isInteger(source)) onGadgetsChange(moveGadgetTo(draft.gadgetIds, source, i));
                setGadgetDrag(null);
              }}>
              <span className="gadget-number">{i + 1}</span>
              {gadget ? (
                <Artwork item={gadget} compact />
              ) : (
                <span className="gadget-placeholder" aria-hidden="true">
                  ?
                </span>
              )}
              <span className="gadget-name">
                <strong>{gadget?.name || "Loading gadget…"}</strong>
                {gadget && <small>{gadget.slotCost === null ? "Cost unknown" : `${gadget.slotCost} ${gadget.slotCost === 1 ? "slot" : "slots"}`}</small>}
                <GadgetAdjustmentBadges gadgetId={gadgetId} value={draftStats.data?.passive} catalog={gadgetRules} />
              </span>
              <div>
                <span className="gadget-drag-handle" draggable={draft.gadgetIds.length > 1}
                  title={draft.gadgetIds.length > 1 ? `Drag gadget ${i + 1} to reorder` : "Add another gadget to reorder"}
                  onDragStart={event => {
                    event.dataTransfer.setData("text/plain", String(i));
                    event.dataTransfer.effectAllowed = "move";
                    setGadgetDrag({ source: i, target: i });
                  }}
                  onDragEnd={() => setGadgetDrag(null)} aria-hidden="true">⠿</span>
                <button
                  type="button"
                  aria-label={`Move gadget ${i + 1} up`}
                  disabled={i === 0}
                  onClick={() =>
                    onGadgetsChange(moveGadget(draft.gadgetIds, i, -1))
                  }
                >
                  ↑
                </button>
                <button
                  type="button"
                  aria-label={`Move gadget ${i + 1} down`}
                  disabled={i === draft.gadgetIds.length - 1}
                  onClick={() =>
                    onGadgetsChange(moveGadget(draft.gadgetIds, i, 1))
                  }
                >
                  ↓
                </button>
              </div>
            </li>
          );
        })}
      </ol>
      <section className="preview-maps" aria-label="Your combination recommended maps">
        <h3>Recommended maps · {draft.mapRecommendationMode === "ALL" ? "All" : draft.recommendedMapIds.length}</h3>
        {draft.mapRecommendationMode === "ALL" ? <p className="muted">No map-specific preference.</p>
          : draft.recommendedMapIds.length === 0 ? <p className="field-warning">Choose at least one map.</p>
            : maps ? <RecommendedMapList recommendations={{ mode: "SELECTED",
              maps: maps.filter(map => draft.recommendedMapIds.includes(map.id)) }} />
              : <p className="muted">{mapsError ? "Map names unavailable. Your selection is preserved." : "Loading selected maps…"}</p>}
      </section>
      </div>
      {saveAction}
    </aside>
  );
}
