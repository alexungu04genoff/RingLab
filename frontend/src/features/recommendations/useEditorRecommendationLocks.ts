import { useEffect, useRef, useState } from "react";
import { emptyLocks } from "./recommendation";
import type { RecommendationSelection } from "./recommendation";

/** Locks and popup state belong to one route/authentication context, at every viewport size. */
export function useEditorRecommendationLocks(context: string) {
  const [lockState, setLockState] = useState({ context, value: emptyLocks() });
  const locks = lockState.context === context ? lockState.value : emptyLocks();
  const [recommendationContext, setRecommendationContext] = useState<string | null>(null);
  const recommendButton = useRef<HTMLButtonElement>(null);
  useEffect(() => { setRecommendationContext(null); setLockState({ context, value: emptyLocks() }); }, [context]);
  function setLocks(value: RecommendationSelection) { setLockState({ context, value }); }
  return { locks, setLocks, recommendationContext, setRecommendationContext, recommendButton };
}
