import { useEffect, useRef, useState } from "react";
import { desktopRecommendationQuery, emptyLocks } from "./recommendation";
import type { RecommendationSelection } from "./recommendation";

/** Desktop locks and popup state belong to one route/authentication context. */
export function useEditorRecommendationLocks(context: string) {
  const [desktop, setDesktop] = useState(() => window.matchMedia?.(desktopRecommendationQuery).matches ?? false);
  const [lockState, setLockState] = useState({ context, value: emptyLocks() });
  const locks = desktop && lockState.context === context ? lockState.value : emptyLocks();
  const [recommendationContext, setRecommendationContext] = useState<string | null>(null);
  const recommendButton = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    const query = window.matchMedia?.(desktopRecommendationQuery);
    if (!query) return;
    const changed = () => {
      setDesktop(query.matches);
      if (!query.matches) { setRecommendationContext(null); setLockState({ context, value: emptyLocks() }); }
    };
    query.addEventListener("change", changed);
    return () => query.removeEventListener("change", changed);
  }, [context]);
  useEffect(() => { setRecommendationContext(null); setLockState({ context, value: emptyLocks() }); }, [context]);
  function setLocks(value: RecommendationSelection) { setLockState({ context, value }); }
  return { desktop, locks, setLocks, recommendationContext, setRecommendationContext, recommendButton };
}
