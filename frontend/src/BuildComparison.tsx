import { createContext, useContext, useEffect, useReducer, useRef, useState } from "react";
import type { ReactNode } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { api, ApiError } from "./api";
import { Artwork, browseOrigin } from "./components";
import type { Build } from "./types";

type Selection = Pick<Build, "id" | "title" | "racer">;
type State = { selected: Selection[]; announcement: string; limitReached: boolean };
type Action = { type: "toggle"; build: Selection } | { type: "remove"; id: string } | { type: "clear" };

function reduce(state: State, action: Action): State {
  if (action.type === "clear") return { selected: [], announcement: "Comparison cleared.", limitReached: false };
  const id = action.type === "remove" ? action.id : action.build.id;
  if (state.selected.some((build) => build.id === id)) {
    return { selected: state.selected.filter((build) => build.id !== id), announcement: "Build removed from comparison.", limitReached: false };
  }
  if (action.type === "remove") return state;
  if (state.selected.length === 2) return { ...state, announcement: "Two builds are selected. Remove one before adding another.", limitReached: true };
  const { title, racer } = action.build;
  return { selected: [...state.selected, { id, title, racer }], announcement: `${title} added. ${state.selected.length + 1} of 2 selected.`, limitReached: false };
}

const ComparisonContext = createContext<{ state: State; dispatch: React.Dispatch<Action> } | null>(null);

export function BuildComparisonProvider({ children }: { children: ReactNode }) {
  const [state, dispatch] = useReducer(reduce, { selected: [], announcement: "", limitReached: false });
  return <ComparisonContext.Provider value={{ state, dispatch }}>
    {children}
    <p className="sr-only" role="status" aria-live="polite">{state.limitReached ? "" : state.announcement}</p>
  </ComparisonContext.Provider>;
}

export function CompareToggle({ build }: { build: Build }) {
  const comparison = useContext(ComparisonContext);
  if (!comparison) return null;
  const selected = comparison.state.selected.some(({ id }) => id === build.id);
  const comparisonFull = comparison.state.selected.length === 2;
  return <button type="button" className="compare-toggle" aria-pressed={selected}
    disabled={comparisonFull && !selected}
    title={comparisonFull && !selected ? "Remove a selected build before adding another." : undefined}
    aria-label={`Compare ${build.title}`} onClick={() => comparison.dispatch({ type: "toggle", build })}>
    {selected ? "✓ Added to compare" : "Add to compare"}
  </button>;
}

export function ComparisonTray() {
  const comparison = useContext(ComparisonContext);
  const selected = comparison?.state.selected;
  const navigate = useNavigate();
  const location = useLocation();
  const request = useRef<AbortController | null>(null);
  const [checking, setChecking] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  useEffect(() => {
    setErrors({});
    setChecking(false);
    return () => { request.current?.abort(); };
  }, [selected, location.key]);
  if (!comparison || !selected?.length) return null;

  async function compare() {
    if (!selected || selected.length !== 2) return;
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    setChecking(true);
    setErrors({});
    const results = await Promise.all(selected.map(async (build) => {
      try {
        await api<Build>(`/builds/${encodeURIComponent(build.id)}`, { signal: controller.signal });
        return [build.id, ""] as const;
      } catch (error) {
        return [build.id, error instanceof ApiError && error.status === 404
          ? "This build no longer exists. Remove it and choose another."
          : "Couldn’t check this build. Your selection is kept; try Compare again."] as const;
      }
    }));
    if (controller.signal.aborted) return;
    setChecking(false);
    const failures = results.filter(([, message]) => message);
    if (failures.length) { setErrors(Object.fromEntries(failures)); return; }
    navigate(`/compare?${new URLSearchParams({ left: selected[0].id, right: selected[1].id })}`,
      { state: { from: browseOrigin(location.pathname, location.search) } });
  }

  return <section className="comparison-tray panel" aria-label="Build comparison selection">
    <strong>{selected.length} of 2 selected</strong>
    {comparison.state.limitReached && <p className="comparison-tray-message" role="status">{comparison.state.announcement}</p>}
    <ul>{selected.map((build) => <li key={build.id}>
      <Artwork item={build.racer} compact />
      <div className="comparison-selection-title"><strong>{build.title}</strong>
        {errors[build.id] && <p role="alert">{errors[build.id]}</p>}</div>
      <button type="button" aria-label={`Remove ${build.title} from comparison`}
        onClick={() => comparison.dispatch({ type: "remove", id: build.id })}>Remove</button>
    </li>)}</ul>
    <div className="comparison-tray-actions">
      <button type="button" onClick={() => comparison.dispatch({ type: "clear" })}>Clear</button>
      <button type="button" className="primary" disabled={selected.length !== 2 || checking} onClick={compare}>
        {checking ? "Checking builds…" : "Compare"}</button>
    </div>
  </section>;
}
