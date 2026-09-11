import { useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth";
import { Artwork, BuildCard, ErrorNotice, ItemSelect } from "../components";
import { useLoad } from "../useLoad";
import type { BuildPage, Machine, Racer } from "../types";
export function Explore({ mine = false }: { mine?: boolean }) {
  const { user } = useAuth();
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const [racer, setRacer] = useState("");
  const [machine, setMachine] = useState("");
  const [sort, setSort] = useState("newest");
  const [page, setPage] = useState(0);
  const racers = useLoad<Racer[]>("/racers");
  const machines = useLoad<Machine[]>("/machines");
  const heroRacers = (racers.data || [])
    .filter(({ racingType }) => racingType !== "POWER")
    .slice(0, 4);
  const params = new URLSearchParams({
    search: query,
    sort,
    page: String(page),
    size: "12",
  });
  if (racer) params.set("racerId", racer);
  if (machine) params.set("machineId", machine);
  if (mine && user) params.set("authorId", user.id);
  const builds = useLoad<BuildPage>(`/builds?${params}`);
  const hasActiveFilters = Boolean(query || racer || machine);
  const clearFilters = () => {
    setSearch("");
    setQuery("");
    setRacer("");
    setMachine("");
    setPage(0);
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
                  <Artwork item={heroRacer} />
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
            setQuery(search);
            setPage(0);
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
            setRacer(v);
            setPage(0);
          }}
          optional
        />
        <ItemSelect
          label="Machine"
          items={machines.data || []}
          value={machine}
          onChange={(v) => {
            setMachine(v);
            setPage(0);
          }}
          optional
        />
        <label>
          Sort by
          <select
            value={sort}
            onChange={(e) => {
              setSort(e.target.value);
              setPage(0);
            }}
          >
            <option value="newest">Newest first</option>
            <option value="score">Highest score</option>
            <option value="rated">Best rated</option>
          </select>
        </label>
      </section>
      <ErrorNotice message={racers.error || machines.error} />
      <div className="section-heading">
        <h2>{mine ? "My builds" : "Community builds"}</h2>
        <span>{builds.data ? `${builds.data.total} builds` : ""}</span>
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
              <BuildCard key={b.id} build={b} />
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
                  A RingLab build combines one racer, one stock machine, and optional
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
          <button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            ← Previous
          </button>
          <span>Page {page + 1}</span>
          <button
            disabled={(page + 1) * 12 >= builds.data.total}
            onClick={() => setPage((p) => p + 1)}
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
    </>
  );
}
