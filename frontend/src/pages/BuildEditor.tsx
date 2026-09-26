import { useEffect, useRef, useState } from "react";
import { DraftStats } from "../BaseStats";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { api, ApiError, json } from "../api";
import { useAuth } from "../auth";
import { Artwork, ErrorNotice, ItemSelect, racingTypeClass, racingTypeLabel } from "../components";
import { applyStockMachine, filterGadgets, gadgetPlateStatus, machinePartsForType, moveGadget, stockMachineSources, switchMachineType, toggleGadget } from "../buildForm";
import { useLoad } from "../useLoad";
import type { Build, BuildDraft, Gadget, GameVersion, MachinePart, Racer, RacingType } from "../types";
import { machineSetupError } from "../buildForm";
import { machineTypes, machineTypeLabel, requiredMachineSlots } from "../machineComposition";
import { useBuildDraft } from "./useBuildDraft";

const partSlots = [
  { key: "frontPartId", type: "FRONT", label: "Front" },
  { key: "rearPartId", type: "REAR", label: "Rear" },
  { key: "tirePartId", type: "TIRE", label: "Tires" },
] as const;
export function BuildEditor() {
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
  const [stockSourceId, setStockSourceId] = useState("");
  const racers = useLoad<Racer[]>("/racers");
  const parts = useLoad<MachinePart[]>("/machine-parts");
  const gadgets = useLoad<Gadget[]>("/gadgets");
  const versions = useLoad<GameVersion[]>("/game-versions");
  const { draft, setDraft, loading, loadError, canSubmit } = useBuildDraft({
    id, remixSourceId, userId: user?.id, newestVersionId: versions.data?.[0]?.id,
  });
  useEffect(() => {
    saveContext.current = {};
    setBusy(false);
    setError("");
    setFieldErrors({});
    setStockSourceId("");
    setGadgetSearch("");
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
  return (
    <>
      <Link className="back" to={id ? `/builds/${id}` : "/"}>
        ← Back
      </Link>
      <div className="page-heading">
        <div>
          <div className="eyebrow accent">THE GARAGE</div>
          <h1>{id ? "Fine-tune your build." : "Make it your own."}</h1>
          <p>One racer. Your machine type and gadget combination.</p>
        </div>
      </div>
      <ErrorNotice
        message={error || loadError || racers.error || parts.error || gadgets.error || versions.error}
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
                const { machineType: _machineType, ...request } = draft;
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
                  <label className="stock-machine-control">
                    Use stock machine
                    <select value={stockSourceId} disabled={!draft.machineType} onChange={(e) => {
                      setStockSourceId(e.target.value);
                      if (e.target.value) setDraft((current) => applyStockMachine(current, parts.data ?? [], e.target.value));
                    }}>
                      <option value="">Choose a complete stock setup</option>
                      {stockMachineSources(parts.data ?? [], draft.machineType).map((part) => (
                        <option key={part.sourceMachineId} value={part.sourceMachineId}>{part.sourceMachineName}</option>
                      ))}
                    </select>
                  </label>
                  <label>
                    Machine type
                    <select value={draft.machineType ?? ""} required onChange={(e) => {
                      setStockSourceId("");
                      setDraft((current) => switchMachineType(current, (e.target.value || null) as RacingType | null, parts.data ?? []));
                    }}>
                      <option value="">Choose a machine type</option>
                      {machineTypes.map((type) => <option key={type} value={type}>{machineTypeLabel(type)}</option>)}
                    </select>
                  </label>
                </div>
                <div className="machine-selection-grid">
                  <div className="racer-select">
                    <ItemSelect
                      label="Racer"
                      items={racers.data || []}
                      value={draft.racerId}
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
                    return <label key={slot.key} className="part-select">
                      {slot.label}
                      <select required value={draft[slot.key] ?? ""} disabled={!draft.machineType} aria-invalid={incompatible}
                        onChange={(e) => {
                          setStockSourceId("");
                          field(slot.key, e.target.value);
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
                    </label>
                  })}
                </div>
                {parts.data && setupError && <p role="alert" className="field-warning">{setupError}</p>}
                {draft.machineType && !requiredMachineSlots(draft.machineType).includes("TIRE") && draft.tirePartId && (
                  <p role="alert">Stored tire: {parts.data?.find((part) => part.id === draft.tirePartId)?.sourceMachineName ?? draft.tirePartId}.
                    <button type="button" onClick={() => { setStockSourceId(""); field("tirePartId", null); }}>Remove incompatible tire</button>
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
                  Search gadgets
                  <input type="search" value={gadgetSearch} onChange={(e) => setGadgetSearch(e.target.value)}
                    placeholder="Search gadgets..." />
                </label>
                <div className="gadget-options">
                  {filterGadgets(gadgets.data ?? [], gadgetSearch, draft.gadgetIds).map((g) => (
                    <label
                      key={g.id}
                      className={`gadget-option ${draft.gadgetIds.includes(g.id) ? "selected" : ""}`}
                    >
                      <input
                        type="checkbox"
                        checked={draft.gadgetIds.includes(g.id)}
                        onChange={() =>
                          field(
                            "gadgetIds",
                            toggleGadget(draft.gadgetIds, g.id),
                          )
                        }
                      />
                      <Artwork item={g} compact />
                      <span className="gadget-option-copy"><strong>{g.name}</strong>
                        <small>{g.slotCost === null ? "Cost unknown" : `${g.slotCost} ${g.slotCost === 1 ? "slot" : "slots"}`}</small>
                        {g.description && <span>{g.description}</span>}
                      </span>
                    </label>
                  ))}
                </div>
                <p className="muted">
                  Display order does not affect Gadget Plate validity.
                </p>
              </section>
            </div>
            <aside className="panel selection build-preview">
              <div className="eyebrow accent">YOUR COMBINATION</div>
              <h2>{draft.title || "Untitled build"}</h2>
              <DraftStats draft={draft} version={versions.data?.find((v) => v.id === draft.gameVersionId) ?? null} />
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
                    <li key={`${gadgetId}-${i}`}>
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
                      </span>
                      <div>
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
              <button
                className="primary"
                disabled={
                  busy || !racers.data || !parts.data || !gadgets.data || !plateStatus.valid || !!setupError
                }
              >
                {busy ? "Saving…" : id ? "Save changes" : "Publish build"}
              </button>
            </aside>
          </form>
        )
      )}
    </>
  );
}
