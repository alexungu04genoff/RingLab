import { useCallback, useEffect, useRef, useState } from "react";
import { api, currentSessionGeneration, json } from "./api";
import { applyRecommendation, recommendationIdentity } from "./recommendation";
import type { RecommendationCatalog, RecommendationRequest, RecommendationResult, RecommendationSelection } from "./recommendation";
import type { BuildDraft } from "./types";

export function useBuildRecommendation(draft: BuildDraft, locks: RecommendationSelection, context: string,
  catalog: RecommendationCatalog, onApply: (draft: BuildDraft) => void) {
  const [state, setState] = useState<{ busy: boolean; error: string; result: RecommendationResult | null }>(
    { busy: false, error: "", result: null });
  const live = useRef({ draft, locks, context, catalog, onApply });
  live.current = { draft, locks, context, catalog, onApply };
  const active = useRef<AbortController | null>(null);
  const proposal = useRef<{ request: RecommendationRequest; identity: string; context: string; session: number } | null>(null);

  const cancel = useCallback(() => {
    active.current?.abort(); active.current = null; proposal.current = null;
    setState({ busy: false, error: "", result: null });
  }, []);
  useEffect(() => { cancel(); return () => { active.current?.abort(); active.current = null; }; }, [context, cancel]);

  async function calculate(request: RecommendationRequest) {
    if (active.current) return;
    const controller = new AbortController(); active.current = controller;
    const snapshot = { request, identity: recommendationIdentity(live.current.draft, live.current.locks),
      context: live.current.context, session: currentSessionGeneration() };
    const current = () => active.current === controller && !controller.signal.aborted
      && live.current.context === snapshot.context && currentSessionGeneration() === snapshot.session;
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

  function apply(): boolean {
    const snapshot = proposal.current;
    if (!snapshot || !state.result) return false;
    try {
      if (snapshot.context !== live.current.context || snapshot.session !== currentSessionGeneration())
        throw new Error("This proposal belongs to an earlier editor session. Calculate again.");
      live.current.onApply(applyRecommendation(live.current.draft, live.current.locks, snapshot.identity,
        snapshot.request, state.result, live.current.catalog));
      cancel();
      return true;
    } catch (error) { setState(current => ({ ...current, error: (error as Error).message })); return false; }
  }
  return { ...state, calculate, cancel, apply };
}
