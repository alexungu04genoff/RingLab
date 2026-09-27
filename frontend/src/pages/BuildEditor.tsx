import { useEffect, useId, useRef, useState } from "react";
import { DraftStats } from "../BaseStats";
import { GadgetAdjustmentBadges, GadgetCatalogAdjustmentBadges, GadgetCatalogTypeLabels } from "../PassiveStats";
import { passiveStatsPath } from "../stats";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { api, ApiError, currentSessionGeneration, json } from "../api";
import { useAuth } from "../auth";
import { Artwork, ErrorNotice, ItemSelect, racingTypeClass, racingTypeLabel } from "../components";
import { applyStockMachine, filterGadgets, gadgetPlateStatus, machinePartsForType, moveGadget, moveGadgetTo, stockMachineSources, switchMachineType, toggleGadget } from "../buildForm";
import { useLoad } from "../useLoad";
import type { Build, BuildDraft, BuildStatsResult, Gadget, GadgetRulesCatalog, GameVersion, MachinePart, Racer, RacingType } from "../types";
import { machineSetupError } from "../buildForm";
import { machineTypes, machineTypeLabel, requiredMachineSlots } from "../machineComposition";
import { useBuildDraft } from "./useBuildDraft";
import { MapRecommendationPicker, RecommendedMapList } from "../MapRecommendations";
import type { RaceMap } from "../types";
import { BuildRecommendationDialog, SelectionLock } from "../BuildRecommendationDialog";
import { desktopRecommendationQuery, emptyLocks, groupLockState, lockTypeConflict, stockSourceForDraft, toggleMachineLocks } from "../recommendation";
import type { ComponentKey, RecommendationSelection } from "../recommendation";

const partSlots = [
  { key: "frontPartId", type: "FRONT", label: "Front" },
  { key: "rearPartId", type: "REAR", label: "Rear" },
  { key: "tirePartId", type: "TIRE", label: "Tires" },
] as const;
export function BuildEditor() {
  const editorId = useId();
  const { id } = useParams();
  const [searchParams] = useSearchParams();
  const remixSourceId = id ? null : searchParams.get("remixFrom");
  const saveContext = useRef<object | null>(null);
  const { user } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<{
    title?: string; description?: string; gameVersionId?: string;
  }>({});
  const [busy, setBusy] = useState(false);
  const [gadgetSearch, setGadgetSearch] = useState("");
  const [gadgetDrag, setGadgetDrag] = useState<{ source: number; target: number } | null>(null);
  const [stockSourceId, setStockSourceId] = useState("");
  const editorContext = JSON.stringify([id, remixSourceId, user?.id, currentSessionGeneration()]);
  const [desktop, setDesktop] = useState(() => window.matchMedia?.(desktopRecommendationQuery).matches ?? false);
  const [lockState, setLockState] = useState({ context: editorContext, value: emptyLocks() });
  const locks = desktop && lockState.context === editorContext ? lockState.value : emptyLocks();
  const [recommendationContext, setRecommendationContext] = useState<string | null>(null);
  const recommendButton = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    const query = window.matchMedia?.(desktopRecommendationQuery);
    if (!query) return;
    const changed = () => {
      setDesktop(query.matches);
      if (!query.matches) { setRecommendationContext(null); setLockState({ context: editorContext, value: emptyLocks() }); }
    };
    query.addEventListener("change", changed);
    return () => query.removeEventListener("change", changed);
  }, [editorContext]);
  useEffect(() => { setRecommendationContext(null); setLockState({ context: editorContext, value: emptyLocks() }); }, [editorContext]);
  function setLocks(value: RecommendationSelection) { setLockState({ context: editorContext, value }); }
  function toggleLock(key: ComponentKey) { setLocks({ ...locks, [key]: locks[key] ? null : draft[key] || null }); }
  const racers = useLoad<Racer[]>("/racers");
  const parts = useLoad<MachinePart[]>("/machine-parts");
  const gadgets = useLoad<Gadget[]>("/gadgets");
  const gadgetRules = useLoad<GadgetRulesCatalog>("/stats/gadget-rules");
  const versions = useLoad<GameVersion[]>("/game-versions");
  const maps = useLoad<RaceMap[]>("/maps");
  const { draft, setDraft, loading, loadError, canSubmit } = useBuildDraft({
    id, remixSourceId, userId: user?.id,
  });
  const draftStats = useLoad<BuildStatsResult>(!loading && canSubmit ? passiveStatsPath(draft) : "");
  useEffect(() => {
    saveContext.current = {};
    setBusy(false);
    setError("");
    setFieldErrors({});
    setStockSourceId("");
    setGadgetSearch("");
    setGadgetDrag(null);
    return () => { saveContext.current = null; };
  }, [id, remixSourceId, user?.id]);
  function field<K extends keyof BuildDraft>(key: K, value: BuildDraft[K]) {
    setDraft((d) => ({ ...d, [key]: value }));
    if (key === "title" || key === "description" || key === "gameVersionId") {
      setFieldErrors((current) => ({ ...current, [key]: undefined }));
    }
  }
  const selectedRacer = racers.data?.find((r) => r.id === draft.racerId);
  const selectedGadgets = draft.gadgetIds
    .map((gadgetId) => gadgets.data?.find((item) => item.id === gadgetId));
  const plateStatus = gadgetPlateStatus(selectedGadgets);
  const activePartSlots = partSlots.filter((slot) => !draft.machineType ? slot.type !== "TIRE" : requiredMachineSlots(draft.machineType).includes(slot.type));
  const setupError = machineSetupError(draft, parts.data ?? []);
  const gadgetTypeSelection = { racerType: selectedRacer?.racingType ?? null, machineType: setupError ? null : draft.machineType };
  const mapSelectionMissing = draft.mapRecommendationMode === "SELECTED" && draft.recommendedMapIds.length === 0;
  return (
    <>
      <div className="page-heading build-editor-heading">
        <div>
          <div className="build-editor-kicker">
            <div className="eyebrow accent">THE GARAGE</div>
            <Link className="back" to={id ? `/builds/${id}` : "/"}>
              ← Back
            </Link>
          </div>
          <div className="build-editor-title-row">
            <h1>{id ? "Fine-tune your build." : "Make it your own."}</h1>
            <p>One racer. Your machine type and gadget combination.</p>
          </div>
        </div>
      </div>
      <ErrorNotice
        message={error || loadError || racers.error || parts.error || gadgets.error || gadgetRules.error || versions.error}
      />
      {loading ? (
        <p role="status">Loading your build…</p>
      ) : (
        canSubmit && (
          <form
            className="editor"
            onSubmit={async (e) => {
              e.preventDefault();
              if (busy || !canSubmit || loading || !plateStatus.valid || setupError) return;
              if (!draft.gameVersionId) {
                setFieldErrors({ gameVersionId: "Select a game version / patch." });
                return;
              }
              setBusy(true);
              const context = saveContext.current;
              setError("");
              setFieldErrors({});
              try {
                if (mapSelectionMissing) { setError("Select at least one map, or choose All maps."); return; }
                const { machineType: _machineType, mapRecommendationMode: _mapMode, ...request } = draft;
                const b = await api<Build>(
                  id ? `/builds/${id}` : "/builds",
                  json(id ? "PUT" : "POST", request),
                );
                if (saveContext.current === context) navigate(`/builds/${b.id}`);
              } catch (e) {
                if (saveContext.current !== context) return;
                if (e instanceof ApiError && (e.field === "title" || e.field === "description" || e.field === "gameVersionId")) {
                  setFieldErrors({ [e.field]: e.message });
                } else {
                  setError((e as Error).message);
                }
              } finally {
                if (saveContext.current === context) setBusy(false);
              }
            }}
          >
            <div className="editor-main">
              <section className="panel">
                <h2>
                  <span className="step">01</span> The essentials
                </h2>
                <label>
                  Game version / Patch
                  <select value={draft.gameVersionId ?? ""}
                    required
                    className={!draft.gameVersionId ? "invalid-select" : undefined}
                    aria-invalid={!draft.gameVersionId}
                    aria-describedby={!draft.gameVersionId ? "build-version-error" : undefined}
                    disabled={versions.loading}
                    onChange={(e) => field("gameVersionId", e.target.value || null)}>
                    <option value="" disabled>Select a patch</option>
                    {versions.data?.map((version) => (
                      <option key={version.id} value={version.id}>Ver. {version.version}</option>
                    ))}
                  </select>
                </label>
                {!draft.gameVersionId && (
                  <p id="build-version-error" className="field-warning" role="alert">
                    {fieldErrors.gameVersionId ?? "A game version / patch is required."}
                  </p>
                )}
                <label>
                  Build title
                  <input
                    required
                    maxLength={120}
                    value={draft.title}
                    aria-invalid={!!fieldErrors.title}
                    aria-describedby={fieldErrors.title ? "build-title-error" : undefined}
                    onChange={(e) => field("title", e.target.value)}
                    placeholder="Give your setup a name"
                  />
                </label>
                <div id="build-title-error"><ErrorNotice message={fieldErrors.title ?? ""} /></div>
                <label>
                  Description
                  <textarea
                    maxLength={10000}
                    rows={6}
                    value={draft.description}
                    aria-invalid={!!fieldErrors.description}
                    aria-describedby={fieldErrors.description ? "build-description-error" : undefined}
                    onChange={(e) => field("description", e.target.value)}
                    placeholder="What makes this combination work for you?"
                  />
                </label>
                <div id="build-description-error"><ErrorNotice message={fieldErrors.description ?? ""} /></div>
              </section>
              <section className="panel">
                <h2>
                  <span className="step">02</span> Racer & machine
                </h2>
                <div className="machine-setup-controls">
                  <div className="stock-machine-control">
                    <div className="selection-field-heading">
                      {desktop && <SelectionLock label="machine setup" locked={groupLockState(draft, locks, parts.data ?? [])}
                        disabled={!!setupError} onClick={() => setLocks(toggleMachineLocks(draft, locks, parts.data ?? []))} />}
                      <label htmlFor={`${editorId}-stock`}>Use stock machine</label>
                    </div>
                    <select id={`${editorId}-stock`} value={stockSourceId} disabled={parts.loading || !!locks.frontPartId || !!locks.rearPartId || !!locks.tirePartId} onChange={(e) => {
                      setStockSourceId(e.target.value);
                      if (e.target.value) setDraft((current) => applyStockMachine(current, parts.data ?? [], e.target.value));
                    }}>
                      <option value="">Choose a complete stock setup</option>
                      {stockMachineSources(parts.data ?? [], draft.machineType).map((part) => (
                        <option key={part.sourceMachineId} value={part.sourceMachineId}>{part.sourceMachineName} · {racingTypeLabel(part.racingType)}</option>
                      ))}
                    </select>
                  </div>
                  <label>
                    Machine type
                    <select value={draft.machineType ?? ""} required onChange={(e) => {
                      const nextType = (e.target.value || null) as RacingType | null;
                      if (nextType ? lockTypeConflict(nextType, locks, parts.data ?? [])
                        : locks.frontPartId || locks.rearPartId || locks.tirePartId) return;
                      setStockSourceId("");
                      setDraft((current) => switchMachineType(current, (e.target.value || null) as RacingType | null, parts.data ?? []));
                    }}>
                      <option value="">Any type — choose a machine or part</option>
                      {machineTypes.map((type) => <option key={type} value={type}
                        disabled={!!lockTypeConflict(type, locks, parts.data ?? [])}>{machineTypeLabel(type)}</option>)}
                    </select>
                  </label>
                </div>
                <div className="machine-selection-grid">
                  <div className="racer-select">
                    <ItemSelect
                      label="Racer"
                      items={racers.data || []}
                      value={draft.racerId}
                      disabled={!!locks.racerId}
                      labelAction={desktop && <SelectionLock label="Racer" locked={!!locks.racerId} disabled={!selectedRacer} onClick={() => toggleLock("racerId")} />}
                      onChange={(v) => field("racerId", v)}
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
                    const selectedPart = parts.data?.find((part) => part.id === draft[slot.key]);
                    const options = machinePartsForType(parts.data ?? [], draft.machineType, slot.type);
                    const incompatible = !!draft[slot.key] && !options.some((part) => part.id === draft[slot.key]);
                    return <div key={slot.key} className="part-select">
                      <div className="selection-field-heading">
                        {desktop && <SelectionLock label={slot.label} locked={!!locks[slot.key]} disabled={!selectedPart}
                          onClick={() => toggleLock(slot.key)} />}
                        <label htmlFor={`${editorId}-${slot.key}`}>{slot.label}</label>
                      </div>
                      <select id={`${editorId}-${slot.key}`} required value={draft[slot.key] ?? ""} disabled={parts.loading || !!locks[slot.key]} aria-invalid={incompatible}
                        onChange={(e) => {
                          setStockSourceId("");
                          const partId = e.target.value;
                          const chosen = parts.data?.find(part => part.id === partId);
                          setDraft(current => ({ ...current, [slot.key]: partId,
                            machineType: current.machineType ?? chosen?.racingType ?? null }));
                        }}>
                        <option value="">Choose a source machine</option>
                        {incompatible && <option value={draft[slot.key]!} disabled>{selectedPart?.sourceMachineName ?? "Unknown stored part"} — incompatible</option>}
                        {options.map((part) => (
                          <option key={part.id} value={part.id}
                            className={`typed-option ${racingTypeClass(part.racingType)}`}>
                            {part.sourceMachineName} · ● {racingTypeLabel(part.racingType)}
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
                {parts.data && setupError && <p role="alert" className="field-warning">{setupError}</p>}
                {draft.machineType && !requiredMachineSlots(draft.machineType).includes("TIRE") && draft.tirePartId && (
                  <p role="alert">Stored tire: {parts.data?.find((part) => part.id === draft.tirePartId)?.sourceMachineName ?? draft.tirePartId}.
                    <button type="button" disabled={!!locks.tirePartId} onClick={() => { setStockSourceId(""); field("tirePartId", null); }}>Remove incompatible tire</button>
                  </p>
                )}
              </section>
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
                  {filterGadgets(gadgets.data ?? [], gadgetSearch, draft.gadgetIds).map((g) => (
                    <div className={`gadget-choice ${desktop && draft.gadgetIds.includes(g.id) ? "has-lock" : ""}`} key={g.id}>
                    {desktop && draft.gadgetIds.includes(g.id) && <SelectionLock label={g.name} locked={locks.gadgetIds.includes(g.id)}
                      onClick={() => setLocks({ ...locks, gadgetIds: toggleGadget(locks.gadgetIds, g.id) })} />}
                    <label
                      className={`gadget-option ${draft.gadgetIds.includes(g.id) ? "selected" : ""}`}
                    >
                      <input
                        type="checkbox"
                        checked={draft.gadgetIds.includes(g.id)}
                        disabled={locks.gadgetIds.includes(g.id)}
                        onChange={() =>
                          field(
                            "gadgetIds",
                            toggleGadget(draft.gadgetIds, g.id),
                          )
                        }
                      />
                      <Artwork item={g} compact />
                      <span className="gadget-option-copy"><strong>{g.name}</strong>
                        <span className="gadget-option-meta">
                          <small className="gadget-slot-badge">{g.slotCost === null ? "Cost unknown" : `${g.slotCost} ${g.slotCost === 1 ? "slot" : "slots"}`}</small>
                          <GadgetCatalogTypeLabels gadgetId={g.id} catalog={gadgetRules.data} selection={gadgetTypeSelection} />
                        </span>
                        {g.description && <span>{g.description}</span>}
                        {draft.gadgetIds.includes(g.id) && draftStats.data?.passive
                          ? <GadgetAdjustmentBadges gadgetId={g.id} value={draftStats.data.passive} catalog={gadgetRules.data} selection={gadgetTypeSelection} />
                          : <GadgetCatalogAdjustmentBadges gadgetId={g.id} catalog={gadgetRules.data} selection={gadgetTypeSelection} />}
                      </span>
                    </label>
                    </div>
                  ))}
                </div>
                <p className="muted">
                  Display order does not affect Gadget Plate validity.
                </p>
              </section>
              <MapRecommendationPicker maps={maps.data ?? []} loading={maps.loading} error={maps.error}
                mode={draft.mapRecommendationMode} selectedIds={draft.recommendedMapIds}
                onChange={(mode, ids) => setDraft(current => ({ ...current, mapRecommendationMode: mode, recommendedMapIds: ids }))} />
            </div>
            <aside className="panel selection build-preview">
              {desktop && <div className="recommendation-entry"><button ref={recommendButton} type="button" className="recommend-action"
                aria-haspopup="dialog" aria-expanded={recommendationContext === editorContext}
                disabled={busy || !racers.data || !parts.data || !gadgets.data || versions.loading || !versions.data}
                onClick={() => setRecommendationContext(editorContext)}>Recommend a build</button>
                <small>Lock selections, then preview a recommendation.</small></div>}
              <div className="build-preview-scroll" role="region" aria-label="Build summary details" tabIndex={0}>
              <div className="eyebrow accent">YOUR COMBINATION</div>
              <h2>{draft.title || "Untitled build"}</h2>
              <DraftStats draft={draft} version={versions.data?.find((v) => v.id === draft.gameVersionId) ?? null} loadedResult={draftStats} />
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
                      const selectedPart = parts.data?.find((part) => part.id === draft[slot.key]);
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
                  const gadget = gadgets.data?.find((item) => item.id === gadgetId);
                  return (
                    <li key={`${gadgetId}-${i}`}
                      className={gadgetDrag?.source === i ? "dragging" : gadgetDrag?.target === i ? "drag-target" : undefined}
                      onDragEnter={() => setGadgetDrag(current => current ? { ...current, target: i } : null)}
                      onDragOver={event => { event.preventDefault(); event.dataTransfer.dropEffect = "move"; }}
                      onDrop={event => {
                        event.preventDefault();
                        const sourceValue = event.dataTransfer.getData("text/plain");
                        const source = Number(sourceValue);
                        if (sourceValue !== "" && Number.isInteger(source)) field("gadgetIds", moveGadgetTo(draft.gadgetIds, source, i));
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
                        <GadgetAdjustmentBadges gadgetId={gadgetId} value={draftStats.data?.passive} catalog={gadgetRules.data} />
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
                            field("gadgetIds", moveGadget(draft.gadgetIds, i, -1))
                          }
                        >
                          ↑
                        </button>
                        <button
                          type="button"
                          aria-label={`Move gadget ${i + 1} down`}
                          disabled={i === draft.gadgetIds.length - 1}
                          onClick={() =>
                            field("gadgetIds", moveGadget(draft.gadgetIds, i, 1))
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
                    : maps.data ? <RecommendedMapList recommendations={{ mode: "SELECTED",
                      maps: maps.data.filter(map => draft.recommendedMapIds.includes(map.id)) }} />
                      : <p className="muted">{maps.error ? "Map names unavailable. Your selection is preserved." : "Loading selected maps…"}</p>}
              </section>
              </div>
              <button
                className="primary"
                disabled={
                  busy || !racers.data || !parts.data || !gadgets.data || !plateStatus.valid || !!setupError || mapSelectionMissing
                }
              >
                {busy ? "Saving…" : id ? "Save changes" : "Publish build"}
              </button>
            </aside>
          </form>
        )
      )}
      {desktop && recommendationContext === editorContext && !loading && canSubmit &&
        <BuildRecommendationDialog draft={draft} locks={locks} context={editorContext}
          catalog={{ racers: racers.data ?? [], parts: parts.data ?? [], gadgets: gadgets.data ?? [] }}
          version={versions.data?.find(version => version.id === draft.gameVersionId) ?? null}
          referenceStats={draftStats.data ?? null}
          returnFocus={recommendButton.current} onClose={() => setRecommendationContext(null)}
          onApply={next => { setDraft(next); setStockSourceId(stockSourceForDraft(next, parts.data ?? [])); }} />}
    </>
  );
}
