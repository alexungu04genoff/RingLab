import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from "react";
import { api, currentSessionGeneration, json } from "./api";
import { useAuth } from "./auth";
import type { Build } from "./types";

export interface CollectionExclusions { racers: string[]; machines: string[]; gadgets: string[] }
export type CollectionCategory = "RACER" | "MACHINE" | "GADGET";
const empty: CollectionExclusions = { racers: [], machines: [], gadgets: [] };
interface CollectionState {
  data: CollectionExclusions;
  status: "anonymous" | "loading" | "ready" | "error";
  error: string;
  busy: boolean;
  revision: number;
  update: (category: CollectionCategory, id: string, owned: boolean) => Promise<void>;
  refresh: () => Promise<CollectionExclusions>;
}
const Context = createContext<CollectionState>({ data: empty, status: "anonymous", error: "", busy: false,
  revision: 0, update: async () => { throw new Error("Collection session unavailable"); },
  refresh: async () => { throw new Error("Collection session unavailable"); } });
export function useCollection() { return useContext(Context); }

export function CollectionProvider({ children }: { children: ReactNode }) {
  const { user, loading, sessionError } = useAuth();
  const session = currentSessionGeneration();
  const key = `${user?.id ?? "anonymous"}:${session}`;
  const live = useRef(key); live.current = key;
  const sequence = useRef(0);
  const pending = useRef(false);
  const [revision, setRevision] = useState(0);
  const [state, setState] = useState<{ key: string; data: CollectionExclusions; status: CollectionState["status"]; error: string; busy: boolean }>(
    { key: "", data: empty, status: "loading", error: "", busy: false });
  const current = () => live.current === key && currentSessionGeneration() === session;
  async function refresh() {
    if (!user || loading || sessionError || !current()) throw new Error("Collection session unavailable");
    const request = ++sequence.current;
    setState(previous => ({ ...previous, key, status: "loading", error: "" }));
    try {
      const data = await api<CollectionExclusions>("/collection", { cache: "no-store" });
      if (!current() || sequence.current !== request) throw new Error("Collection changed. Try again.");
      if (!data || ![data.racers, data.machines, data.gadgets].every(ids => Array.isArray(ids) && ids.every(id => typeof id === "string")))
        throw new Error("Invalid collection response. Try again.");
      setState({ key, data, status: "ready", error: "", busy: pending.current });
      return data;
    } catch (error) {
      if (current() && sequence.current === request) setState({ key, data: empty, status: "error", error: (error as Error).message, busy: pending.current });
      throw error;
    }
  }
  useEffect(() => {
    pending.current = false;
    if (user && !loading && !sessionError) void refresh().catch(() => {});
    return () => { sequence.current++; };
  }, [key, loading, sessionError]);
  // Refresh private presentation after another tab/device changes the account's collection.
  useEffect(() => {
    const focus = () => { if (!pending.current && user) { setRevision(value => value + 1); void refresh().catch(() => {}); } };
    window.addEventListener("focus", focus);
    return () => window.removeEventListener("focus", focus);
  }, [key, loading, sessionError]);
  async function update(category: CollectionCategory, id: string, owned: boolean) {
    if (!current() || pending.current || state.key !== key || state.status !== "ready") return;
    pending.current = true;
    sequence.current++;
    setRevision(value => value + 1);
    setState(previous => ({ ...previous, busy: true }));
    try {
      await api(`/collection/${category}/${encodeURIComponent(id)}`, json("PUT", { owned }));
      if (!current()) return;
      await refresh();
    } catch (error) {
      if (current()) setState({ key, data: empty, status: "error", error: (error as Error).message, busy: false });
    } finally {
      if (current()) { pending.current = false; setState(previous => ({ ...previous, busy: false })); }
    }
  }
  const effective = loading || sessionError ? { ...state, data: empty, status: sessionError ? "error" as const : "loading" as const }
    : !user ? { ...state, data: empty, status: "anonymous" as const, error: "", busy: false }
      : state.key === key ? state : { ...state, data: empty, status: "loading" as const, busy: false };
  return <Context.Provider value={{ ...effective, revision, update, refresh }}>{children}</Context.Provider>;
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
export function OwnershipCheckbox({ category, id, name }: { category: CollectionCategory; id: string; name: string }) {
  const collection = useCollection();
  if (collection.status === "anonymous") return null;
  const excluded = isExcluded(collection.data, category, id);
  return <label className="ownership-checkbox"><input type="checkbox" checked={collection.status === "ready" && !excluded}
    ref={input => { if (input) input.indeterminate = collection.status !== "ready"; }}
    disabled={collection.status !== "ready" || collection.busy} aria-label={`Owned / unlocked: ${name}`}
    onChange={event => void collection.update(category, id, event.target.checked)} />Owned / unlocked
    {collection.status === "ready" && excluded && <strong>Not owned</strong>}</label>;
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
