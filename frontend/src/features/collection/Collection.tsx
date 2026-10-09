import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { api, currentSessionGeneration, json } from "../../shared/api/api";
import { useAuth } from "../auth/auth";
import type { Build } from "../../shared/types";
import { OwnershipStatusBadge } from "./OwnershipStatusBadge";

export interface CollectionExclusions { racers: string[]; machines: string[]; gadgets: string[] }
export interface CollectionSnapshot { data: CollectionExclusions; revision: number; identity: string }
export type CollectionCategory = "RACER" | "MACHINE" | "GADGET";
const empty: CollectionExclusions = { racers: [], machines: [], gadgets: [] };
interface CollectionState {
  data: CollectionExclusions;
  status: "anonymous" | "loading" | "ready" | "error";
  error: string;
  busy: boolean;
  revision: number;
  identity: string;
  update: (category: CollectionCategory, id: string, owned: boolean) => Promise<void>;
  refresh: () => Promise<CollectionSnapshot>;
}
const Context = createContext<CollectionState>({ data: empty, status: "anonymous", error: "", busy: false,
  revision: 0, identity: "", update: async () => { throw new Error("Collection session unavailable"); },
  refresh: async () => { throw new Error("Collection session unavailable"); } });
export function useCollection() { return useContext(Context); }

function normalizeExclusions(data: CollectionExclusions): CollectionExclusions {
  const ids = (values: string[]) => [...new Set(values)].sort();
  return { racers: ids(data.racers), machines: ids(data.machines), gadgets: ids(data.gadgets) };
}
function sameExclusions(first: CollectionExclusions, second: CollectionExclusions) {
  return (["racers", "machines", "gadgets"] as const).every(category =>
    first[category].length === second[category].length
    && first[category].every((id, index) => id === second[category][index]));
}

export function CollectionProvider({ children }: { children: ReactNode }) {
  const { user, loading, sessionError } = useAuth();
  const session = currentSessionGeneration();
  const key = `${user?.id ?? "anonymous"}:${session}`;
  const live = useRef(key); live.current = key;
  const sequence = useRef(0);
  const pending = useRef(false);
  // Updated before refresh resolves, so Apply never waits for a React render to see a real change.
  const revision = useRef(0);
  const confirmed = useRef<CollectionSnapshot | null>(null);
  const inFlight = useRef<{ key: string; sequence: number; promise: Promise<CollectionSnapshot> } | null>(null);
  const [state, setState] = useState<{ key: string; data: CollectionExclusions; status: CollectionState["status"]; error: string; busy: boolean }>(
    { key: "", data: empty, status: "loading", error: "", busy: false });
  const current = () => live.current === key && currentSessionGeneration() === session;
  function loadCollection(): Promise<CollectionSnapshot> {
    if (!user || loading || sessionError || !current()) return Promise.reject(new Error("Collection session unavailable"));
    if (inFlight.current?.key === key && inFlight.current.sequence === sequence.current) return inFlight.current.promise;
    const request = ++sequence.current;
    setState(previous => ({ ...previous, key, status: "loading", error: "" }));
    const promise = (async () => {
      try {
        const response = await api<CollectionExclusions>("/collection", { cache: "no-store" });
        if (!current() || sequence.current !== request) throw new Error("Collection changed. Try again.");
        if (!response || ![response.racers, response.machines, response.gadgets].every(ids => Array.isArray(ids) && ids.every(id => typeof id === "string")))
          throw new Error("Invalid collection response. Try again.");
        const data = normalizeExclusions(response);
        if (confirmed.current?.identity === key && !sameExclusions(confirmed.current.data, data)) revision.current++;
        const snapshot = { data, revision: revision.current, identity: key };
        confirmed.current = snapshot;
        setState({ key, data, status: "ready", error: "", busy: pending.current });
        return snapshot;
      } catch (error) {
        if (current() && sequence.current === request) setState({ key, data: empty, status: "error", error: (error as Error).message, busy: pending.current });
        throw error;
      } finally {
        if (inFlight.current?.sequence === request) inFlight.current = null;
      }
    })();
    inFlight.current = { key, sequence: request, promise };
    return promise;
  }
  async function refresh() {
    if (pending.current) throw new Error("Collection update in progress. Try again.");
    const snapshot = await loadCollection();
    if (!current() || pending.current) throw new Error("Collection changed. Try again.");
    return snapshot;
  }
  useEffect(() => {
    pending.current = false;
    if (user && !loading && !sessionError) void refresh().catch(() => {});
    return () => { sequence.current++; };
  }, [key, loading, sessionError]);
  // Refresh private presentation after another tab/device changes the account's collection.
  useEffect(() => {
    const focus = () => { if (!pending.current && user) void refresh().catch(() => {}); };
    window.addEventListener("focus", focus);
    return () => window.removeEventListener("focus", focus);
  }, [key, loading, sessionError]);
  async function update(category: CollectionCategory, id: string, owned: boolean) {
    if (!current() || pending.current || state.key !== key || state.status !== "ready") return;
    pending.current = true;
    sequence.current++;
    setState(previous => ({ ...previous, busy: true }));
    try {
      await api(`/collection/${category}/${encodeURIComponent(id)}`, json("PUT", { owned }));
      if (!current()) return;
      await loadCollection();
    } catch (error) {
      if (current()) setState({ key, data: empty, status: "error", error: (error as Error).message, busy: false });
    } finally {
      if (current()) { pending.current = false; setState(previous => ({ ...previous, busy: false })); }
    }
  }
  const effective = loading || sessionError ? { ...state, data: empty, status: sessionError ? "error" as const : "loading" as const }
    : !user ? { ...state, data: empty, status: "anonymous" as const, error: "", busy: false }
      : state.key === key ? state : { ...state, data: empty, status: "loading" as const, busy: false };
  return <Context.Provider value={{ ...effective, revision: revision.current, identity: key, update, refresh }}>{children}</Context.Provider>;
}

export function isExcluded(data: CollectionExclusions, category: CollectionCategory, id: string) {
  return data[category === "RACER" ? "racers" : category === "MACHINE" ? "machines" : "gadgets"].includes(id);
}
export function CollectionStatus() {
  const collection = useCollection();
  if (collection.status === "loading") return <p role="status">Checking your collection…</p>;
  if (collection.status === "error") return <p role="alert">Collection availability unknown. {collection.error} <button type="button" onClick={() => void collection.refresh().catch(() => {})}>Retry collection</button></p>;
  return null;
}
export function OwnershipCheckbox({ category, id, name, showNotOwnedStatus = true }: {
  category: CollectionCategory; id: string; name: string; showNotOwnedStatus?: boolean;
}) {
  const collection = useCollection();
  if (collection.status === "anonymous") return null;
  const excluded = isExcluded(collection.data, category, id);
  const state = collection.busy ? "Saving…" : collection.status === "error" ? "Unavailable"
    : collection.status === "loading" ? "Loading…" : excluded ? "Not owned" : "Owned";
  const tone = collection.status !== "ready" || collection.busy ? "pending" : excluded ? "missing" : "owned";
  return <label className={`ownership-control ownership-${tone}`}><input type="checkbox" checked={collection.status === "ready" && !excluded}
    ref={input => { if (input) input.indeterminate = collection.status !== "ready"; }}
    disabled={collection.status !== "ready" || collection.busy} aria-label={`Owned: ${name}`}
    onChange={event => void collection.update(category, id, event.target.checked)} />
    <span aria-live="polite">{state === "Not owned"
      ? showNotOwnedStatus ? <OwnershipStatusBadge notOwned /> : "Owned"
      : state}</span></label>;
}
type AvailabilitySelection = Pick<Build, "racer" | "gadgets"> & Partial<Pick<Build, "frontPart" | "rearPart" | "tirePart">>;
export function missingBuildItems(build: AvailabilitySelection, data: CollectionExclusions) {
  const missing = new Map<string, string>();
  if (data.racers.includes(build.racer.id)) missing.set(`racer:${build.racer.id}`, `Racer: ${build.racer.name}`);
  for (const part of [build.frontPart, build.rearPart, build.tirePart])
    if (part && data.machines.includes(part.sourceMachineId)) missing.set(`machine:${part.sourceMachineId}`, `Source machine: ${part.sourceMachineName}`);
  for (const gadget of build.gadgets) if (data.gadgets.includes(gadget.id)) missing.set(`gadget:${gadget.id}`, `Gadget: ${gadget.name}`);
  return [...missing.values()];
}
export function MissingItems({ build }: { build: AvailabilitySelection }) {
  const collection = useCollection();
  if (collection.status !== "ready") return <CollectionStatus />;
  const missing = missingBuildItems(build, collection.data);
  return missing.length ? <details className="missing-items"><summary>Missing {missing.length} {missing.length === 1 ? "item" : "items"}</summary>
    <ul>{missing.map(name => <li key={name}>{name}</li>)}</ul></details> : null;
}
