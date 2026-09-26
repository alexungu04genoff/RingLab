import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth";
import { Artwork, BuildCard, ErrorNotice } from "../components";
import { useLoad } from "../useLoad";
import { ComparisonTray } from "../BuildComparison";
import { pageCardStats } from "../CardStats";
import { LatestNews } from "../LatestNews";
import { TopCommunityBuilds } from "../TopCommunityBuilds";
import type { BuildPage, GameVersion, Machine, Racer, TopCommunitySnapshot } from "../types";
import { ComponentsIcon, LibraryIcon, RacerIcon, SearchIcon, SortIcon, TagIcon } from "../icons";
import { SearchableFilter } from "../SearchableFilter";
import {
  activeExploreFilterChips, clearExploreFilters, PUBLIC_PATCH_PREFERENCE,
  PUBLIC_SORT_PREFERENCE, readPreference, resolveGameVersion, resolvePage, resolveSort,
  updateExploreParams, type BuildSort,
} from "./exploreFilters";

const SONIC_HERO_NAME = "Sonic the Hedgehog";
const FEATURED_HERO_NAMES = new Set([
  'Miles "Tails" Prower',
  "Knuckles the Echidna",
  "Shadow the Hedgehog",
  "Dr. Eggman",
]);
export const exploreLayoutClass = (newsVisible: boolean) =>
  `explore-content ${newsVisible ? "with-news" : "news-hidden"}`;

export function selectRandomHeroRacers(
  racers: Racer[],
  count = 5,
  random: () => number = Math.random,
) {
  const selectRandom = (candidates: Racer[], limit: number) => {
    const shuffled = [...candidates];
    for (let index = shuffled.length - 1; index > 0; index -= 1) {
      const swapIndex = Math.floor(random() * (index + 1));
      [shuffled[index], shuffled[swapIndex]] = [shuffled[swapIndex], shuffled[index]];
    }
    return shuffled.slice(0, limit);
  };

  const sonic = racers.find(({ name }) => name === SONIC_HERO_NAME);
  const selected = sonic ? [sonic] : [];
  const featured = racers.filter(
    ({ id, name }) => id !== sonic?.id && FEATURED_HERO_NAMES.has(name),
  );
  selected.push(...selectRandom(featured, Math.min(2, count - selected.length)));

  const others = racers.filter(
    ({ id, name }) => id !== sonic?.id && !FEATURED_HERO_NAMES.has(name),
  );
  selected.push(...selectRandom(others, count - selected.length));

  if (selected.length < count) {
    const remaining = racers.filter(({ id }) => !selected.some((racer) => racer.id === id));
    selected.push(...selectRandom(remaining, count - selected.length));
  }
  return selected;
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
  const [spotlight, setSpotlight] = useState<TopCommunitySnapshot>();
  const [statsRefresh, setStatsRefresh] = useState(0);
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
    includeStats: "true",
  });
  if (racer) params.set("racerId", racer);
  if (machine) params.set("machineId", machine);
  if (gameVersion) params.set("gameVersionId", gameVersion);
  if (mine && user) params.set("authorId", user.id);
  if (!mine) spotlight?.items.forEach(({ build }) => params.append("excludeId", build.id));
  const builds = useLoad<BuildPage>(`/builds?${params}`, statsRefresh);
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
      if (next.has("gameVersionId")) {
        next.delete("gameVersionId");
        changed = true;
      }
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
    }
  }

  const clearFilters = () => {
    setSearch("");
    savePublicPreference(PUBLIC_PATCH_PREFERENCE, "");
    setUrlParams(clearExploreFilters(urlParams));
  };
  return (
    <>
      <ComparisonTray />
      <div className={`page-heading ${mine ? "" : "explore-hero"}`}>
        <div className={mine ? "" : "hero-copy"}>
          {!mine ? (
            <div className="hero-title-row">
              <img
                className="hero-game-logo"
                src="/assets/branding/sonic-racing-crossworlds-logo.png"
                alt="Sonic Racing: CrossWorlds"
              />
              <h1>Find your next build.</h1>
            </div>
          ) : (
            <h1>Your garage.</h1>
          )}
          <p>
            {mine
              ? "Your setups, ready for the next race."
              : "Racers. Machines. Gadgets. Shared by the community."}
          </p>
        </div>
        {!mine && heroRacers.length > 0 && (
          <div className="hero-racers" aria-hidden="true">
            {heroRacers.map((heroRacer) => (
              <div className="hero-racer" key={heroRacer.id}>
                <Artwork item={heroRacer} portrait />
              </div>
            ))}
          </div>
        )}
      </div>
      {!mine && <TopCommunityBuilds versions={versions.data || []} onSnapshot={setSpotlight} />}
      <section className="filters" aria-label="Filter builds">
        <form
          className="search"
          onSubmit={(e) => {
            e.preventDefault();
            updateUrl({ search });
          }}
        >
          <label>
            <span className="field-label"><SearchIcon /> Search builds</span>
            <input
              placeholder="Search titles, racers, machines, or gadgets…"
              maxLength={120}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </label>
          <button type="submit">Search</button>
        </form>
        <SearchableFilter
          label="Racer"
          icon={<RacerIcon />}
          options={(racers.data || []).map(({ id, name }) => ({ value: id, label: name }))}
          value={racer}
          allLabel="All racers"
          onChange={(v) => {
            updateUrl({ racerId: v });
          }}
        />
        <SearchableFilter
          label="Uses parts from"
          icon={<ComponentsIcon />}
          options={(machines.data || []).map(({ id, name }) => ({ value: id, label: name }))}
          value={machine}
          allLabel="All source machines"
          onChange={(v) => {
            updateUrl({ machineId: v });
          }}
        />
        <SearchableFilter
          label="Patch"
          icon={<TagIcon />}
          options={(versions.data || []).map(({ id, version }) => ({
            value: id,
            label: `Ver. ${version}`,
          }))}
          value={gameVersion}
          allLabel="All versions"
          onChange={(value) => {
            savePublicPreference(PUBLIC_PATCH_PREFERENCE, value);
            updateUrl({ gameVersionId: value });
          }}
        />
        <label>
          <span className="sort-label"><SortIcon /> Sort by <span className="sort-help" tabIndex={0} aria-label="How Best rated works"><span aria-hidden="true">i</span><span role="tooltip">Best rated uses Wilson vote confidence. Exact ties favor newer patches, then fewer downvotes at zero confidence, then newer submissions.</span></span></span>
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
                if (chip.key === "sort") savePublicPreference(PUBLIC_SORT_PREFERENCE, "");
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
            builds.data.statsError && <div className="error" role="alert">Builds are available, but base stats could not load. <button type="button"
              onClick={() => setStatsRefresh((value) => value + 1)}>Retry page stats</button></div>}
          {builds.data &&
            (builds.data.items.length ? (
              <div className="build-grid">
                {builds.data.items.map((b) => (
                  <BuildCard key={b.id} build={b} versions={versions.data || []} pageStats={pageCardStats(builds.data!, b.id)} />
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
                        <LibraryIcon /> Browse game collection
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
              <Link to="/game-data"><LibraryIcon /> Browse the game collection →</Link>
            </p>
          )}
        </div>
        {!mine && newsVisible && <LatestNews onHide={() => setNewsVisible(false)} />}
      </div>
    </>
  );
}
