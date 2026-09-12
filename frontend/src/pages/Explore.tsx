import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth";
import { Artwork, BuildCard, ErrorNotice, ItemSelect } from "../components";
import { useLoad } from "../useLoad";
import { LatestNews } from "../LatestNews";
import type { BuildPage, GameVersion, Machine, Racer } from "../types";

const PUBLIC_SORT_PREFERENCE = "ringlab.explore.sort";
const PUBLIC_PATCH_PREFERENCE = "ringlab.explore.gameVersionId";
const SORT_VALUES = ["newest", "score", "rated"] as const;
type BuildSort = (typeof SORT_VALUES)[number];
type FilterKey = "search" | "racerId" | "machineId" | "gameVersionId" | "sort";
export interface ActiveFilterChip { key: FilterKey; label: string }

function readPreference(key: string) {
  try {
    return typeof localStorage === "undefined" ? null : localStorage.getItem(key);
  } catch {
    return null;
  }
}

function isBuildSort(value: string | null): value is BuildSort {
  return SORT_VALUES.some((sort) => sort === value);
}

export function resolveSort(urlValue: string | null, savedValue: string | null, mine: boolean): BuildSort {
  if (isBuildSort(urlValue)) return urlValue;
  if (!mine && isBuildSort(savedValue)) return savedValue;
  return mine ? "newest" : "rated";
}

export function resolvePage(value: string | null) {
  if (!value || !/^\d+$/.test(value)) return 0;
  const page = Number(value);
  return Number.isSafeInteger(page) ? page : 0;
}

export function resolveGameVersion(
  urlValue: string | null,
  savedValue: string | null,
  knownIds: string[] | undefined,
  mine: boolean,
) {
  if (!knownIds) return "";
  const preferred = urlValue || (mine ? null : savedValue) || "";
  return knownIds.includes(preferred) ? preferred : "";
}

export function updateExploreParams(
  current: URLSearchParams,
  changes: Record<string, string>,
  resetPage = true,
) {
  const next = new URLSearchParams(current);
  Object.entries(changes).forEach(([key, value]) => {
    if (value) next.set(key, value);
    else next.delete(key);
  });
  if (resetPage) next.delete("page");
  return next;
}

export function clearExploreFilters(current: URLSearchParams) {
  const next = new URLSearchParams(current);
  ["search", "racerId", "machineId", "gameVersionId", "page"].forEach((key) => next.delete(key));
  return next;
}

export function activeExploreFilterChips(
  params: URLSearchParams,
  racers: Racer[],
  machines: Machine[],
  versions: GameVersion[],
  mine: boolean,
): ActiveFilterChip[] {
  const chips: ActiveFilterChip[] = [];
  const search = params.get("search");
  const racer = racers.find(({ id }) => id === params.get("racerId"));
  const machine = machines.find(({ id }) => id === params.get("machineId"));
  const version = versions.find(({ id }) => id === params.get("gameVersionId"));
  const sort = params.get("sort");
  if (search) chips.push({ key: "search", label: `Search: ${search}` });
  if (racer) chips.push({ key: "racerId", label: `Racer: ${racer.name}` });
  if (machine) chips.push({ key: "machineId", label: `Parts from: ${machine.name}` });
  if (version) chips.push({ key: "gameVersionId", label: `Patch: Ver. ${version.version}` });
  const defaultSort = mine ? "newest" : "rated";
  if (sort && sort !== defaultSort && isBuildSort(sort)) {
    const labels: Record<BuildSort, string> = {
      newest: "Newest first", score: "Highest score", rated: "Best rated",
    };
    chips.push({ key: "sort", label: `Sort: ${labels[sort]}` });
  }
  return chips;
}

export const exploreLayoutClass = (newsVisible: boolean) =>
  `explore-content ${newsVisible ? "with-news" : "news-hidden"}`;

export function selectRandomHeroRacers(
  racers: Racer[],
  count = 4,
  random: () => number = Math.random,
) {
  if (racers.length <= count) return racers;

  const shuffled = [...racers];
  for (let index = shuffled.length - 1; index > 0; index -= 1) {
    const swapIndex = Math.floor(random() * (index + 1));
    [shuffled[index], shuffled[swapIndex]] = [shuffled[swapIndex], shuffled[index]];
  }
  return shuffled.slice(0, count);
}

export function Explore({ mine = false }: { mine?: boolean }) {
  const { user } = useAuth();
  const [urlParams, setUrlParams] = useSearchParams();
  const query = urlParams.get("search") ?? "";
  const racer = urlParams.get("racerId") ?? "";
  const machine = urlParams.get("machineId") ?? "";
  const requestedGameVersion = urlParams.get("gameVersionId") ?? "";
  const sort = resolveSort(urlParams.get("sort"), readPreference(PUBLIC_SORT_PREFERENCE), mine);
  const page = resolvePage(urlParams.get("page"));
  const [search, setSearch] = useState(query);
  const [newsVisible, setNewsVisible] = useState(true);
  const racers = useLoad<Racer[]>("/racers");
  const machines = useLoad<Machine[]>("/machines");
  const versions = useLoad<GameVersion[]>("/game-versions");
  const savedGameVersion = mine ? null : readPreference(PUBLIC_PATCH_PREFERENCE);
  const preferredGameVersion = requestedGameVersion || savedGameVersion || "";
  const gameVersion = resolveGameVersion(
    requestedGameVersion,
    savedGameVersion,
    versions.data?.map(({ id }) => id),
    mine,
  );
  const heroRacers = useMemo(
    () => selectRandomHeroRacers(racers.data || []),
    [racers.data],
  );
  const params = new URLSearchParams({
    search: query,
    sort,
    page: String(page),
    size: "12",
  });
  if (racer) params.set("racerId", racer);
  if (machine) params.set("machineId", machine);
  if (gameVersion) params.set("gameVersionId", gameVersion);
  if (mine && user) params.set("authorId", user.id);
  const builds = useLoad<BuildPage>(`/builds?${params}`);
  const hasActiveFilters = Boolean(query || racer || machine || gameVersion);
  const activeChips = activeExploreFilterChips(
    urlParams, racers.data || [], machines.data || [], versions.data || [], mine,
  );
  useEffect(() => setSearch(query), [query]);
  useEffect(() => {
    const next = new URLSearchParams(urlParams);
    let changed = false;
    if (next.get("sort") !== sort) {
      next.set("sort", sort);
      changed = true;
    }
    if (next.get("page") && resolvePage(next.get("page")) === 0) {
      next.delete("page");
      changed = true;
    }
    if (versions.data && preferredGameVersion !== gameVersion) {
      next.delete("gameVersionId");
      changed = true;
    } else if (gameVersion && !requestedGameVersion) {
      next.set("gameVersionId", gameVersion);
      changed = true;
    }
    if (changed) setUrlParams(next, { replace: true });
  }, [gameVersion, preferredGameVersion, requestedGameVersion, setUrlParams, sort, urlParams, versions.data]);

  function updateUrl(changes: Record<string, string>, resetPage = true) {
    setUrlParams(updateExploreParams(urlParams, changes, resetPage));
  }

  function savePublicPreference(key: string, value: string) {
    if (mine) return;
    try {
      if (value) localStorage.setItem(key, value);
      else localStorage.removeItem(key);
    } catch {
      // Browsing still works when storage is unavailable.
    }
  }

  const clearFilters = () => {
    setSearch("");
    savePublicPreference(PUBLIC_PATCH_PREFERENCE, "");
    setUrlParams(clearExploreFilters(urlParams));
  };
  return (
    <>
      <div className={`page-heading ${mine ? "" : "explore-hero"}`}>
        <div>
          <div className="eyebrow accent">SONIC RACING: CROSSWORLDS</div>
          <h1>{mine ? "Your garage." : "Find your next build."}</h1>
          <p>
            {mine
              ? "Your setups, ready for the next race."
              : "Racers. Machines. Gadgets. Shared by the community."}
          </p>
        </div>
        <div className={mine ? undefined : "explore-hero-side"}>
          {!mine && heroRacers.length > 0 && (
            <div className="hero-racers" aria-hidden="true">
              {heroRacers.map((heroRacer) => (
                <div className="hero-racer" key={heroRacer.id}>
                  <Artwork item={heroRacer} portrait />
                </div>
              ))}
            </div>
          )}
          <Link className="button primary" to="/builds/new">
            ＋ Create build
          </Link>
        </div>
      </div>
      <section className="filters" aria-label="Filter builds">
        <form
          className="search"
          onSubmit={(e) => {
            e.preventDefault();
            updateUrl({ search });
          }}
        >
          <label>
            Search builds
            <input
              placeholder="Search by build title…"
              maxLength={120}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </label>
          <button type="submit">Search</button>
        </form>
        <ItemSelect
          label="Racer"
          items={racers.data || []}
          value={racer}
          onChange={(v) => {
            updateUrl({ racerId: v });
          }}
          optional
        />
        <ItemSelect
          label="Uses parts from"
          emptyLabel="All source machines"
          items={machines.data || []}
          value={machine}
          onChange={(v) => {
            updateUrl({ machineId: v });
          }}
          optional
        />
        <label>
          Patch
          <select value={gameVersion} onChange={(e) => {
            const value = e.target.value;
            savePublicPreference(PUBLIC_PATCH_PREFERENCE, value);
            updateUrl({ gameVersionId: value });
          }}>
            <option value="">All versions</option>
            {versions.data?.map((version) => (
              <option key={version.id} value={version.id}>Ver. {version.version}</option>
            ))}
          </select>
        </label>
        <label>
          <span className="sort-label">Sort by <span className="sort-help" tabIndex={0} aria-label="How Best rated works">ⓘ<span role="tooltip">Best rated groups positive scores above neutral and negative scores, then uses Wilson confidence to rank builds within each group.</span></span></span>
          <select
            value={sort}
            onChange={(e) => {
              const value = e.target.value as BuildSort;
              savePublicPreference(PUBLIC_SORT_PREFERENCE, value);
              updateUrl({ sort: value });
            }}
          >
            <option value="newest">Newest first</option>
            <option value="score">Highest score</option>
            <option value="rated">Best rated</option>
          </select>
        </label>
      </section>
      {activeChips.length > 0 && (
        <div className="active-filters" aria-label="Active filters">
          {activeChips.map((chip) => (
            <button key={chip.key} type="button" className="filter-chip"
              aria-label={`Remove ${chip.label}`} onClick={() => {
                if (chip.key === "search") setSearch("");
                if (chip.key === "gameVersionId") savePublicPreference(PUBLIC_PATCH_PREFERENCE, "");
                updateUrl({ [chip.key]: "" });
              }}>
              {chip.label} <span aria-hidden="true">×</span>
            </button>
          ))}
          <button type="button" className="clear-filters" onClick={clearFilters}>Clear filters</button>
        </div>
      )}
      <ErrorNotice message={racers.error || machines.error || versions.error} />
      <div className={mine ? undefined : exploreLayoutClass(newsVisible)}>
        <div>
          <div className="section-heading">
            <h2>{mine ? "My builds" : "Community builds"}</h2>
            <div className="section-heading-actions">
              {!mine && !newsVisible && <button type="button" className="news-toggle" onClick={() => setNewsVisible(true)}>Show news</button>}
              <span>{builds.data ? `${builds.data.total} builds` : ""}</span>
            </div>
          </div>
          {builds.loading && (
            <div className="explore-state loading-state" role="status">
              <span className="loading-ring" aria-hidden="true" />
              <div>
                <h2>Loading community builds</h2>
                <p>Getting the latest shared setups ready for you.</p>
              </div>
            </div>
          )}
          {builds.error && (
            <div className="explore-state error-state">
              <div>
                <h2>We couldn’t load the builds</h2>
                <p>Your search and filters are still here. Please try again shortly.</p>
              </div>
              <ErrorNotice message={builds.error} />
            </div>
          )}
          {builds.data &&
            (builds.data.items.length ? (
              <div className="build-grid">
                {builds.data.items.map((b) => (
                  <BuildCard key={b.id} build={b} versions={versions.data || []} />
                ))}
              </div>
            ) : (
              <div className="empty">
                <div className="ring" />
                {hasActiveFilters ? (
                  <>
                    <h2>No matching builds</h2>
                    <p>No builds matched your current search or filters.</p>
                    <button type="button" onClick={clearFilters}>
                      Clear search and filters
                    </button>
                  </>
                ) : !mine ? (
                  <>
                    <h2>Be the first to share a build</h2>
                    <p className="empty-intro">
                      A RingLab build combines one racer, front, rear and tire parts, and optional
                      ordered gadgets. Share your setup with the community when it is ready.
                    </p>
                    <div className="empty-actions">
                      <Link className="button primary" to="/game-data">
                        Browse game collection
                      </Link>
                      {user ? (
                        <Link className="button" to="/builds/new">
                          Create the first build
                        </Link>
                      ) : (
                        <Link className="button" to="/register">
                          Create an account to share
                        </Link>
                      )}
                    </div>
                  </>
                ) : (
                  <>
                    <h2>No builds here yet</h2>
                    <p>Create a build to see it in your garage.</p>
                    <Link className="button primary" to="/builds/new">
                      Create a build
                    </Link>
                  </>
                )}
              </div>
            ))}
          {builds.data && builds.data.total > 12 && (
            <div className="pagination">
              <button disabled={page === 0} onClick={() => updateUrl({ page: String(page - 1) }, false)}>
                ← Previous
              </button>
              <span>Page {page + 1}</span>
              <button
                disabled={(page + 1) * 12 >= builds.data.total}
                onClick={() => updateUrl({ page: String(page + 1) }, false)}
              >
                Next →
              </button>
            </div>
          )}
          {!mine && (
            <p className="catalog-link">
              Looking for a racer or gadget?{" "}
              <Link to="/game-data">Browse the game collection →</Link>
            </p>
          )}
        </div>
        {!mine && newsVisible && <LatestNews onHide={() => setNewsVisible(false)} />}
      </div>
    </>
  );
}
