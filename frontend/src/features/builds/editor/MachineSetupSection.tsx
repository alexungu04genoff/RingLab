import { useId, type Dispatch, type SetStateAction } from "react";
import { CollectionArtwork as Artwork } from "../../collection/CollectionArtwork";
import { ItemSelect } from "../../collection/ItemSelect";
import { racingTypeClass, racingTypeLabel } from "../../../shared/ui/RacingTypeBadge";
import { SelectionLock } from "../../recommendations/SelectionLock";
import { groupLockState, lockTypeConflict, toggleMachineLocks } from "../../recommendations/recommendation";
import type { ComponentKey, RecommendationSelection } from "../../recommendations/recommendation";
import { applyStockMachine, machinePartsForType, switchMachineType, stockMachineSources } from "../buildForm";
import { machineTypes, machineTypeLabel, requiredMachineSlots, visibleMachinePartSlots } from "../machineComposition";
import type { BuildDraft, MachinePart, Racer, RacingType } from "../../../shared/types";

export function MachineSetupSection({ draft, setDraft, racers, parts, partsLoading, stockSourceId,
  setStockSourceId, setupError, desktop, locks, setLocks, ownershipLabel, onChange }: {
  draft: BuildDraft; setDraft: Dispatch<SetStateAction<BuildDraft>>; racers?: Racer[];
  parts?: MachinePart[]; partsLoading: boolean; stockSourceId: string; setStockSourceId: (id: string) => void;
  setupError: string; desktop: boolean; locks: RecommendationSelection; setLocks: (locks: RecommendationSelection) => void;
  ownershipLabel: (category: "machines", id: string) => string;
  onChange: <K extends ComponentKey>(key: K, value: BuildDraft[K]) => void;
}) {
  const editorId = useId();
  const selectedRacer = racers?.find(racer => racer.id === draft.racerId);
  const activePartSlots = visibleMachinePartSlots(draft.machineType);
  function toggleLock(key: ComponentKey) {
    setLocks({ ...locks, [key]: locks[key] ? null : draft[key] || null });
  }
  return (
    <section className="panel">
      <h2>
        <span className="step">02</span> Racer & machine
      </h2>
      <div className="machine-setup-controls">
        <div className="stock-machine-control">
          <div className="selection-field-heading">
            {desktop && <SelectionLock label="machine setup" locked={groupLockState(draft, locks, parts ?? [])}
              disabled={!!setupError} onClick={() => setLocks(toggleMachineLocks(draft, locks, parts ?? []))} />}
            <label htmlFor={`${editorId}-stock`}>Use stock machine</label>
          </div>
          <select id={`${editorId}-stock`} value={stockSourceId} disabled={partsLoading || !!locks.frontPartId || !!locks.rearPartId || !!locks.tirePartId} onChange={(e) => {
            setStockSourceId(e.target.value);
            if (e.target.value) setDraft((current) => applyStockMachine(current, parts ?? [], e.target.value));
          }}>
            <option value="">Choose a complete stock setup</option>
            {stockMachineSources(parts ?? [], draft.machineType).map((part) => (
              <option key={part.sourceMachineId} value={part.sourceMachineId}>{part.sourceMachineName} · {racingTypeLabel(part.racingType)}{ownershipLabel("machines", part.sourceMachineId)}</option>
            ))}
          </select>
        </div>
        <label>
          Machine type
          <select value={draft.machineType ?? ""} required onChange={(e) => {
            const nextType = (e.target.value || null) as RacingType | null;
            if (nextType ? lockTypeConflict(nextType, locks, parts ?? [])
              : locks.frontPartId || locks.rearPartId || locks.tirePartId) return;
            setStockSourceId("");
            setDraft((current) => switchMachineType(current, (e.target.value || null) as RacingType | null, parts ?? []));
          }}>
            <option value="">Any type — choose a machine or part</option>
            {machineTypes.map((type) => <option key={type} value={type}
              disabled={!!lockTypeConflict(type, locks, parts ?? [])}>{machineTypeLabel(type)}</option>)}
          </select>
        </label>
      </div>
      <div className="machine-selection-grid">
        <div className="racer-select">
          <ItemSelect
            label="Racer"
            items={racers || []}
            value={draft.racerId}
            disabled={!!locks.racerId}
            labelAction={desktop && <SelectionLock label="Racer" locked={!!locks.racerId} disabled={!selectedRacer} onClick={() => toggleLock("racerId")} />}
            onChange={(v) => onChange("racerId", v)}
          />
          {selectedRacer && (
            <span className="selected-part selected-racer">
              <Artwork item={selectedRacer} portrait />
              <span>
                <strong>{selectedRacer.name}</strong>
                <small className={`racing-type-text ${racingTypeClass(selectedRacer.racingType)}`}>
                  {selectedRacer.racingType ?? "Type unknown"}
                </small>
              </span>
            </span>
          )}
        </div>
        {activePartSlots.map((slot) => {
          const selectedPart = parts?.find((part) => part.id === draft[slot.key]);
          const options = machinePartsForType(parts ?? [], draft.machineType, slot.type);
          const incompatible = !!draft[slot.key] && !options.some((part) => part.id === draft[slot.key]);
          return <div key={slot.key} className="part-select">
            <div className="selection-field-heading">
              {desktop && <SelectionLock label={slot.label} locked={!!locks[slot.key]} disabled={!selectedPart}
                onClick={() => toggleLock(slot.key)} />}
              <label htmlFor={`${editorId}-${slot.key}`}>{slot.label}</label>
            </div>
            <select id={`${editorId}-${slot.key}`} required value={draft[slot.key] ?? ""} disabled={partsLoading || !!locks[slot.key]} aria-invalid={incompatible}
              onChange={(e) => {
                setStockSourceId("");
                const partId = e.target.value;
                const chosen = parts?.find(part => part.id === partId);
                setDraft(current => ({ ...current, [slot.key]: partId,
                  machineType: current.machineType ?? chosen?.racingType ?? null }));
              }}>
              <option value="">Choose a source machine</option>
              {incompatible && <option value={draft[slot.key]!} disabled>{selectedPart?.sourceMachineName ?? "Unknown stored part"} — incompatible</option>}
              {options.map((part) => (
                <option key={part.id} value={part.id}
                  className={`typed-option ${racingTypeClass(part.racingType)}`}>
                  {part.sourceMachineName} · ● {racingTypeLabel(part.racingType)}{ownershipLabel("machines", part.sourceMachineId)}
                </option>
              ))}
            </select>
            {selectedPart && <span className="selected-part">
              <Artwork item={{ id: selectedPart.sourceMachineId, name: selectedPart.sourceMachineName,
                imagePath: selectedPart.sourceMachineImagePath, racingType: selectedPart.racingType }} compact />
              <span><strong>{selectedPart.sourceMachineName}</strong>
                <small className={`racing-type-text ${racingTypeClass(selectedPart.racingType)}`}>
                  {selectedPart.racingType ?? "Type unknown"}
                </small></span>
            </span>}
          </div>
        })}
      </div>
      {parts && setupError && <p role="alert" className="field-warning">{setupError}</p>}
      {draft.machineType && !requiredMachineSlots(draft.machineType).includes("TIRE") && draft.tirePartId && (
        <p role="alert">Stored tire: {parts?.find((part) => part.id === draft.tirePartId)?.sourceMachineName ?? draft.tirePartId}.
          <button type="button" disabled={!!locks.tirePartId} onClick={() => { setStockSourceId(""); onChange("tirePartId", null); }}>Remove incompatible tire</button>
        </p>
      )}
    </section>
  );
}
