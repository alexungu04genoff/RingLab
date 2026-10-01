import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom";
import { api, ApiError, currentSessionGeneration, json } from "../shared/api/api";
import { useAuth } from "../features/auth/auth";
import { MissingItems, useCollection } from "../features/collection/Collection";
import { ErrorNotice } from "../shared/ui/ErrorNotice";
import { gadgetPlateStatus, machineSetupError } from "../features/builds/buildForm";
import { useBuildDraft } from "../features/builds/useBuildDraft";
import { BuildEssentialsSection, type BuildEssentialsErrors } from "../features/builds/editor/BuildEssentialsSection";
import { MachineSetupSection } from "../features/builds/editor/MachineSetupSection";
import { GadgetSelectionSection } from "../features/builds/editor/GadgetSelectionSection";
import { BuildEditorPreview } from "../features/builds/editor/BuildEditorPreview";
import { useBuildEditorCatalog } from "../features/builds/editor/useBuildEditorCatalog";
import { MapRecommendationPicker } from "../features/maps/MapRecommendations";
import { BuildRecommendationDialog } from "../features/recommendations/BuildRecommendationDialog";
import { stockSourceForDraft } from "../features/recommendations/recommendation";
import { useEditorRecommendationLocks } from "../features/recommendations/useEditorRecommendationLocks";
import { passiveStatsPath } from "../features/stats/stats";
import { useLoad } from "../shared/hooks/useLoad";
import type { Build, BuildDraft, BuildStatsResult, Gadget } from "../shared/types";

export function BuildEditor() {
  const collection = useCollection();
  const ownershipLabel = (category: "racers" | "machines" | "gadgets", id: string) =>
    collection.status === "ready" && collection.data[category].includes(id) ? " — Not owned" : "";
  const { id } = useParams();
  const [searchParams] = useSearchParams();
  const remixSourceId = id ? null : searchParams.get("remixFrom");
  const saveContext = useRef<object | null>(null);
  const { user } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<BuildEssentialsErrors>({});
  const [busy, setBusy] = useState(false);
  const [stockSourceId, setStockSourceId] = useState("");
  const draftContext = JSON.stringify([id, remixSourceId, user?.id]);
  const editorContext = JSON.stringify([id, remixSourceId, user?.id, currentSessionGeneration()]);
  const { desktop, locks, setLocks, recommendationContext, setRecommendationContext, recommendButton } =
    useEditorRecommendationLocks(editorContext);
  const { racers, parts, gadgets, gadgetRules, versions, maps } = useBuildEditorCatalog();
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
  const setupError = machineSetupError(draft, parts.data ?? []);
  const gadgetTypeSelection = { racerType: selectedRacer?.racingType ?? null, machineType: setupError ? null : draft.machineType };
  const mapSelectionMissing = draft.mapRecommendationMode === "SELECTED" && draft.recommendedMapIds.length === 0;
  async function saveBuild(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
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
  }
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
            {selectedRacer && <MissingItems build={{ racer: selectedRacer,
              frontPart: parts.data?.find(part => part.id === draft.frontPartId),
              rearPart: parts.data?.find(part => part.id === draft.rearPartId),
              tirePart: parts.data?.find(part => part.id === draft.tirePartId) ?? null,
              gadgets: selectedGadgets.filter((g): g is Gadget => !!g) }} />}
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
          <form className="editor" onSubmit={saveBuild}>
            <div className="editor-main">
              <BuildEssentialsSection draft={draft} versions={versions.data} versionsLoading={versions.loading}
                fieldErrors={fieldErrors} onChange={field} />
              <MachineSetupSection draft={draft} setDraft={setDraft} racers={racers.data} parts={parts.data}
                partsLoading={parts.loading} stockSourceId={stockSourceId} setStockSourceId={setStockSourceId}
                setupError={setupError} desktop={desktop} locks={locks} setLocks={setLocks}
                ownershipLabel={ownershipLabel} onChange={field} />
              <GadgetSelectionSection draft={draft} gadgets={gadgets.data} gadgetRules={gadgetRules.data}
                passiveStats={draftStats.data?.passive} gadgetTypeSelection={gadgetTypeSelection}
                desktop={desktop} locks={locks} setLocks={setLocks} ownershipLabel={ownershipLabel}
                onChange={ids => field("gadgetIds", ids)} draftContext={draftContext} />
              <MapRecommendationPicker maps={maps.data ?? []} loading={maps.loading} error={maps.error}
                mode={draft.mapRecommendationMode} selectedIds={draft.recommendedMapIds}
                onChange={(mode, ids) => setDraft(current => ({ ...current, mapRecommendationMode: mode, recommendedMapIds: ids }))} />
            </div>
            <BuildEditorPreview draft={draft} selectedRacer={selectedRacer} plateStatus={plateStatus} parts={parts.data} gadgets={gadgets.data}
              gadgetRules={gadgetRules.data} maps={maps.data} mapsError={maps.error}
              version={versions.data?.find(version => version.id === draft.gameVersionId) ?? null}
              draftStats={draftStats} draftContext={draftContext} onGadgetsChange={ids => field("gadgetIds", ids)}
              recommendationAction={
                desktop && <div className="recommendation-entry"><button ref={recommendButton} type="button" className="recommend-action"
                  aria-haspopup="dialog" aria-expanded={recommendationContext === editorContext}
                  disabled={busy || !racers.data || !parts.data || !gadgets.data || versions.loading || !versions.data}
                  onClick={() => setRecommendationContext(editorContext)}>Recommend a build</button>
                  <small>Lock selections, then preview a recommendation.</small></div>
              }
              saveAction={
                <button
                  className="primary"
                  disabled={
                    busy || !racers.data || !parts.data || !gadgets.data || !plateStatus.valid || !!setupError || mapSelectionMissing
                  }
                >
                  {busy ? "Saving…" : id ? "Save changes" : "Publish build"}
                </button>
              } />
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
