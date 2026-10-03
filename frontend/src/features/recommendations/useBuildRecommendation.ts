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
  const applying = useRef(false);
  const proposal = useRef<{ request: RecommendationRequest; identity: string; context: string; session: number; configuration: string; revision: number; collectionIdentity: string } | null>(null);

  const cancel = useCallback(() => {
    active.current?.abort(); active.current = null; proposal.current = null; applying.current = false;
    setState({ busy: false, error: "", result: null });
  }, []);
  useEffect(() => { cancel(); return () => { active.current?.abort(); active.current = null; proposal.current = null; applying.current = false; }; }, [context, configuration, cancel]);

  function staleReason(snapshot: NonNullable<typeof proposal.current>): string {
    if (snapshot.context !== live.current.context || snapshot.session !== currentSessionGeneration()
        || snapshot.collectionIdentity !== collectionLive.current.identity)
      return "This proposal belongs to an earlier editor session. Calculate again.";
    if (snapshot.configuration !== live.current.configuration)
      return "Recommendation settings changed. Calculate again.";
    if (snapshot.identity !== recommendationIdentity(live.current.draft,live.current.locks))
      return "The draft or locks changed; this proposal is stale. Close and reopen recommendations.";
    if (snapshot.revision !== collectionLive.current.revision)
      return "Collection settings changed. Calculate again with the items you own now.";
    return "";
  }

  function collectionBlocker(): string {
    if (collectionLive.current.busy) return "Your collection update is still being saved. Wait before applying.";
    if (!["ready","loading"].includes(collectionLive.current.status))
      return "Your collection could not be checked. Retry loading it before applying.";
    return "";
  }

  async function calculate(request: RecommendationRequest) {
    if (active.current || applying.current) return;
    if (collection.status !== "ready" || collection.busy) {
      setState({ busy: false, error: "Load your collection successfully before calculating a recommendation.", result: null });
      return;
    }
    if (referenceIdentity && referenceIdentity !== recommendationIdentity(live.current.draft, live.current.locks)) {
      setState({ busy: false, error: "The draft or locks changed. Close and reopen recommendations to use your current setup.", result: null });
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
      if (!current()) {
        if (active.current === controller) setState({ busy: false, error: "Your editor session changed. Calculate again.", result: null });
        return;
      }
      proposal.current = snapshot;
      setState({ busy: false, error: "", result });
    } catch (error) {
      if (current()) setState({ busy: false, error: (error as Error).message, result: null });
      else if (active.current === controller) setState({ busy: false, error: "Your editor session changed. Calculate again.", result: null });
    } finally { if (active.current === controller) active.current = null; }
  }

  async function apply(): Promise<boolean> {
    const snapshot = proposal.current;
    if (!snapshot || !state.result || state.busy || applying.current) return false;
    applying.current = true;
    try {
      const blocker = staleReason(snapshot) || collectionBlocker();
      if (blocker) throw new Error(blocker);
      setState(current => ({ ...current, busy: true }));
      const refreshed = await collectionLive.current.refresh();
      // A canceled or replaced Apply must never change the newer dialog's state.
      if (proposal.current !== snapshot) return false;
      const changed = staleReason(snapshot) || collectionBlocker();
      if (changed) throw new Error(changed);
      if (snapshot.revision !== refreshed.revision || snapshot.collectionIdentity !== refreshed.identity)
        throw new Error("Collection settings changed. Calculate again.");
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
    } catch (error) {
      if (proposal.current === snapshot) setState(current => ({ ...current, busy: false, error: (error as Error).message }));
      return false;
    } finally { if (proposal.current === snapshot) applying.current = false; }
  }
  const applyBlocker = proposal.current ? staleReason(proposal.current) || collectionBlocker() : "";
  return { ...state, applyBlocker, calculate, cancel, apply };
}
