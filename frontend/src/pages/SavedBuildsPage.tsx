import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { BuildCard } from "../components";
import { ComparisonTray } from "../BuildComparison";
import { useSavedBuilds } from "../SavedBuilds";
import { useLoad } from "../useLoad";
import type { Build, BuildStatsResult, GameVersion } from "../types";

export interface SavedBuildPage {
  items: { build: Build; savedAt: string }[];
  total: number; page: number; size: number;
  statsByBuildId: Record<string, BuildStatsResult> | null;
  statsError: string | null;
}

export function SavedBuildsPage() {
  const saved = useSavedBuilds();
  const [params, setParams] = useSearchParams();
  const search = params.get("search") ?? "";
  const patch = params.get("gameVersionId") ?? "";
  const requestedPage = Number(params.get("page") ?? 0);
  const page = Number.isInteger(requestedPage) && requestedPage >= 0 && requestedPage <= 100000 ? requestedPage : 0;
  const [query, setQuery] = useState(search);
  const [refresh, setRefresh] = useState(0);
  useEffect(() => setQuery(search), [search]);
  const versions = useLoad<GameVersion[]>("/game-versions");
  const request = new URLSearchParams({ search, page: String(page), size: "12" });
  if (patch) request.set("gameVersionId", patch);
  const result = useLoad<SavedBuildPage>(saved?.authenticated ? `/saved-builds?${request}` : "", refresh + (saved?.revision ?? 0));
  const update = (changes: Record<string, string>) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([key,value]) => value ? next.set(key,value) : next.delete(key));
    setParams(next);
  };
  useEffect(() => {
    if (!result.data) return;
    const last = Math.max(0, Math.ceil(result.data.total / 12) - 1);
    if (page > last) {
      const next = new URLSearchParams(params);
      if (last) next.set("page", String(last)); else next.delete("page");
      setParams(next, { replace: true });
    }
  }, [result.data, page, params, setParams]);
  // Reconcile deletions/edits made elsewhere when revisiting the page.
  useEffect(() => {
    const reload = () => setRefresh(value => value + 1);
    window.addEventListener("focus", reload);
    return () => window.removeEventListener("focus", reload);
  }, []);
  return <>
    <ComparisonTray />
    <div className="page-heading"><div><h1>Saved Builds</h1>
      <p>Private bookmarks of live community builds. Author edits appear here automatically.</p></div></div>
    <section className="saved-filters panel" aria-label="Filter saved builds">
      <form className="search" onSubmit={event => { event.preventDefault(); update({ search: query, page: "" }); }}>
        <label>Search saved builds<input value={query} maxLength={120} onChange={event => setQuery(event.target.value)} /></label>
        <button>Search</button>
      </form>
      <label>Patch<select value={patch} onChange={event => update({ gameVersionId: event.target.value, page: "" })}>
        <option value="">All patches</option>
        {versions.data?.map(version => <option key={version.id} value={version.id}>Ver. {version.version}</option>)}
      </select></label>
      <span>Most recently saved first</span>
    </section>
    {result.loading && <p role="status">Loading saved builds…</p>}
    {result.error && <div role="alert"><p>Could not load saved builds. {result.error}</p>
      <button onClick={() => setRefresh(value => value + 1)}>Retry saved builds</button></div>}
    {result.data && <>
      <p>{result.data.total} saved {result.data.total === 1 ? "build" : "builds"}</p>
      {result.data.items.length === 0 ? <div className="empty">
        <p>{search || patch ? "No saved builds match these filters." : "No saved builds yet. Bookmark a setup from Explore to find it here later."}</p>
        {(search || patch) && <button onClick={() => setParams({})}>Clear filters</button>}
        <Link to="/">Explore builds</Link>
      </div> : <div className="build-grid">
        {result.data.items.map(({build,savedAt}) => <div className="saved-card" key={build.id}>
          <p className="saved-date">Saved <time dateTime={savedAt}>{new Date(savedAt).toLocaleString()}</time></p>
          <BuildCard build={build} versions={versions.data ?? []} pageStats={result.data!.statsByBuildId?.[build.id]
            ? { status: "ready", value: result.data!.statsByBuildId[build.id] } : { status: "failed" }} />
        </div>)}
      </div>}
      {result.data.statsError && <p role="alert">{result.data.statsError} <button onClick={() => setRefresh(value => value + 1)}>Retry stats</button></p>}
      {result.data.total > 12 && <div className="pagination">
        <button disabled={page === 0} onClick={() => update({page:String(page-1)})}>Previous</button>
        <span>Page {page+1} of {Math.ceil(result.data.total/12)}</span>
        <button disabled={(page+1)*12 >= result.data.total} onClick={() => update({page:String(page+1)})}>Next</button>
      </div>}
    </>}
  </>;
}
