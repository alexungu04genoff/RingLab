import { useCallback, useEffect, useRef, useState } from "react";
import { api, currentSessionGeneration, json } from "../../shared/api/api";
import { applyRecommendation, recommendationIdentity } from "./recommendation";
import type { RecommendationCatalog, RecommendationRequest, RecommendationResult, RecommendationSelection } from "./recommendation";
import type { BuildDraft } from "../../shared/types";
import { useCollection } from "../collection/Collection";

export function useBuildRecommendation(draft: BuildDraft, locks: RecommendationSelection, context: string,
  catalog: RecommendationCatalog, onApply: (draft: BuildDraft) => void, configuration = "", referenceIdentity?: string) {
  const collection = useCollection();
  const collectionLive = useRef(collection); collectionLive.current = collection;
  const [state, setState] = useState<{ busy: boolean; error: string; result: RecommendationResult | null }>(
    { busy: false, error: "", result: null });
  const live = useRef({ draft, locks, context, catalog, onApply, configuration });
  live.current = { draft, locks, context, catalog, onApply, configuration };
  const active = useRef<AbortController | null>(null);
  const proposal = useRef<{ request: RecommendationRequest; identity: string; context: string; session: number; configuration: string; revision: number; collectionIdentity: string } | null>(null);

  const cancel = useCallback(() => {
    active.current?.abort(); active.current = null; proposal.current = null;
    setState({ busy: false, error: "", result: null });
  }, []);
  useEffect(() => { cancel(); return () => { active.current?.abort(); active.current = null; proposal.current = null; }; }, [context, configuration, cancel]);

  async function calculate(request: RecommendationRequest) {
    if (active.current) return;
    if (collection.status !== "ready" || collection.busy) {
      setState({ busy: false, error: "Load your collection successfully before calculating a recommendation.", result: null });
      return;
    }
    if (referenceIdentity && referenceIdentity !== recommendationIdentity(live.current.draft, live.current.locks)) {
      setState({ busy: false, error: "The draft or locks changed. Close and reopen recommendations to freeze a new reference.", result: null });
      return;
    }
    const controller = new AbortController(); active.current = controller;
    const snapshot = { request, identity: recommendationIdentity(live.current.draft, live.current.locks),
      context: live.current.context, session: currentSessionGeneration(), configuration, revision: collection.revision,
      collectionIdentity: collection.identity };
    const current = () => active.current === controller && !controller.signal.aborted
      && live.current.context === snapshot.context && currentSessionGeneration() === snapshot.session
      && live.current.configuration === configuration;
    proposal.current = null;
    setState({ busy: true, error: "", result: null });
    try {
      const result = await api<RecommendationResult>("/build-recommendations", { ...json("POST", request), signal: controller.signal });
      if (!current()) return;
      proposal.current = snapshot;
      setState({ busy: false, error: "", result });
    } catch (error) {
      if (current()) setState({ busy: false, error: (error as Error).message, result: null });
    } finally { if (active.current === controller) active.current = null; }
  }

  async function apply(): Promise<boolean> {
    const snapshot = proposal.current;
    if (!snapshot || !state.result || state.busy) return false;
    try {
      if (snapshot.context !== live.current.context || snapshot.session !== currentSessionGeneration())
        throw new Error("This proposal belongs to an earlier editor session. Calculate again.");
      if (collectionLive.current.busy || !["ready", "loading"].includes(collectionLive.current.status)
          || snapshot.revision !== collectionLive.current.revision || snapshot.collectionIdentity !== collectionLive.current.identity)
        throw new Error("Collection settings changed or are unavailable. Calculate again.");
      setState(current => ({ ...current, busy: true }));
      const refreshed = await collectionLive.current.refresh();
      if (proposal.current !== snapshot || collectionLive.current.busy || snapshot.revision !== collectionLive.current.revision
          || snapshot.revision !== refreshed.revision || snapshot.collectionIdentity !== refreshed.identity
          || snapshot.collectionIdentity !== collectionLive.current.identity)
        throw new Error("Collection settings changed. Calculate again.");
      if (snapshot.context !== live.current.context || snapshot.session !== currentSessionGeneration())
        throw new Error("This proposal belongs to an earlier editor session. Calculate again.");
      if (snapshot.configuration !== live.current.configuration)
        throw new Error("This proposal belongs to an earlier configuration. Calculate again.");
      const selection = state.result.selection;
      if (!selection) throw new Error("No recommendation to apply.");
      const exclusions = refreshed.data;
      if ((selection.racerId && exclusions.racers.includes(selection.racerId))
          || selection.gadgetIds.some(id => exclusions.gadgets.includes(id))
          || [selection.frontPartId, selection.rearPartId, selection.tirePartId].some(id => {
            if (!id) return false;
            const part = live.current.catalog.parts.find(part => part.id === id);
            return !part || exclusions.machines.includes(part.sourceMachineId);
          })) throw new Error("This recommendation contains items you no longer own. Calculate again.");
      live.current.onApply(applyRecommendation(live.current.draft, live.current.locks, snapshot.identity,
        snapshot.request, state.result, live.current.catalog));
      cancel();
      return true;
    } catch (error) { setState(current => ({ ...current, busy: false, error: (error as Error).message })); return false; }
  }
  return { ...state, calculate, cancel, apply };
}
