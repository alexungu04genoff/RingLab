import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { api, currentSessionGeneration, json } from "./api";
import { useAuth } from "./auth";
import { BookmarkIcon } from "./icons";
import type { Build } from "./types";

type SavedState = { saved?: boolean; pending: boolean; error?: string };
type SavedContext = {
  authenticated: boolean;
  states: Record<string, SavedState>;
  revision: number;
  register: (id: string) => () => void;
  retry: (id: string) => void;
  toggle: (id: string) => Promise<void>;
};
const Context = createContext<SavedContext | null>(null);
export const useSavedBuilds = () => useContext(Context);

export function SavedBuildsProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const generation = currentSessionGeneration();
  // Remount private consumers as well as the store; no old page or status can flash for a new actor.
  return <SessionSavedBuilds key={`${user?.id ?? "anonymous"}:${generation}`} authenticated={!!user} generation={generation}>
    {children}
  </SessionSavedBuilds>;
}

function SessionSavedBuilds({ children, authenticated, generation }: {
  children: ReactNode; authenticated: boolean; generation: number;
}) {
  const [states, setStates] = useState<Record<string, SavedState>>({});
  const [revision, setRevision] = useState(0);
  const entries = useRef<Record<string, SavedState>>({});
  const visible = useRef(new Map<string, number>());
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const requests = useRef(new Set<AbortController>());
  const active = useRef(true);
  const current = useCallback(() => active.current && generation === currentSessionGeneration(), [generation]);
  const update = useCallback((changes: Record<string, SavedState>) => {
    if (!current()) return;
    entries.current = { ...entries.current, ...changes };
    setStates(entries.current);
  }, [current]);

  const flush = useCallback(() => {
    timer.current = undefined;
    if (!authenticated || !current()) return;
    const ids = [...visible.current.keys()].filter(id => !entries.current[id]);
    for (let start = 0; start < ids.length; start += 50) {
      const batch = ids.slice(start, start + 50);
      update(Object.fromEntries(batch.map(id => [id, { pending: true }])));
      const controller = new AbortController();
      requests.current.add(controller);
      const params = new URLSearchParams();
      batch.forEach(id => params.append("buildId", id));
      api<{ savedIds: string[] }>(`/saved-builds/status?${params}`, { signal: controller.signal, cache: "no-store" })
        .then(result => {
          if (!controller.signal.aborted) update(Object.fromEntries(batch.map(id =>
            [id, { saved: result.savedIds.includes(id), pending: false }])));
        })
        .catch(() => {
          if (!controller.signal.aborted) update(Object.fromEntries(batch.map(id =>
            [id, { pending: false, error: "Could not check saved status." }])));
        }).finally(() => requests.current.delete(controller));
    }
  }, [authenticated, current, update]);
  const schedule = useCallback(() => {
    if (timer.current === undefined) timer.current = setTimeout(flush, 0);
  }, [flush]);
  const register = useCallback((id: string) => {
    visible.current.set(id, (visible.current.get(id) ?? 0) + 1);
    schedule();
    return () => {
      const count = (visible.current.get(id) ?? 1) - 1;
      if (count) visible.current.set(id, count); else visible.current.delete(id);
    };
  }, [schedule]);
  const retry = useCallback((id: string) => {
    delete entries.current[id];
    schedule();
  }, [schedule]);
  const toggle = useCallback(async (id: string) => {
    const previous = entries.current[id];
    if (!authenticated || !current() || previous?.pending || previous?.saved === undefined) return;
    update({ [id]: { ...previous, pending: true, error: undefined } });
    const controller = new AbortController();
    requests.current.add(controller);
    try {
      await api(`/saved-builds/${encodeURIComponent(id)}`, { ...json(previous.saved ? "DELETE" : "PUT"), signal: controller.signal });
      if (current()) {
        update({ [id]: { saved: !previous.saved, pending: false } });
        setRevision(value => value + 1);
      }
    } catch {
      if (!controller.signal.aborted) update({ [id]: { ...previous, pending: false, error: "Could not update saved build. Try again." } });
    } finally { requests.current.delete(controller); }
  }, [authenticated, current, update]);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
      clearTimeout(timer.current);
      timer.current = undefined;
      requests.current.forEach(request => request.abort());
      requests.current.clear();
      entries.current = {};
    };
  }, []);
  const value = useMemo(() => ({ authenticated, states, revision, register, retry, toggle }),
    [authenticated, states, revision, register, retry, toggle]);
  return <Context.Provider value={value}>{children}</Context.Provider>;
}

export function SaveBuildButton({ build }: { build: Pick<Build, "id" | "title"> }) {
  const saved = useSavedBuilds();
  const register = saved?.register;
  useEffect(() => register?.(build.id), [register, build.id]);
  if (!saved) return null;
  if (!saved.authenticated) return <Link className="button save-build" to="/login"
    state={{ from: `/builds/${encodeURIComponent(build.id)}` }} aria-label={`Save ${build.title}`} title="Log in to save this build">
    <BookmarkIcon />
  </Link>;
  const state = saved.states[build.id];
  const label = state?.saved ? `Remove ${build.title} from saved builds` : `Save ${build.title}`;
  return <span className="save-build-control">
    <button type="button" className="save-build" aria-label={state?.saved === undefined && state?.error ? `Retry saved status for ${build.title}` : label}
      aria-pressed={state?.saved} disabled={!state || state.pending} title={state?.error ?? label}
      onClick={() => state?.saved === undefined ? saved.retry(build.id) : void saved.toggle(build.id)}>
      <BookmarkIcon fill={state?.saved ? "currentColor" : "none"} />
      {state?.pending && <span aria-hidden="true">…</span>}
      {state?.saved && <span aria-hidden="true">✓</span>}
    </button>
    {state?.error && <span className="save-build-error" role="alert">{state.error}</span>}
  </span>;
}
