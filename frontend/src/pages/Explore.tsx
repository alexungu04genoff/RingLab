import { useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth";
import { BuildCard, ErrorNotice, ItemSelect } from "../components";
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
  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow accent">SONIC RACING: CROSSWORLDS</div>
          <h1>{mine ? "Your garage." : "Find your next build."}</h1>
          <p>
            {mine
              ? "Your setups, ready for the next race."
              : "Racers. Machines. Gadgets. Shared by the community."}
          </p>
        </div>
        <Link className="button primary" to="/builds/new">
          ＋ Create build
        </Link>
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
          </select>
        </label>
      </section>
      <ErrorNotice message={builds.error || racers.error || machines.error} />
      <div className="section-heading">
        <h2>{mine ? "My builds" : "Community builds"}</h2>
        <span>{builds.data ? `${builds.data.total} builds` : ""}</span>
      </div>
      {builds.loading && <p role="status">Loading the garage…</p>}
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
            <h2>No builds here yet</h2>
            <p>
              {query || racer || machine
                ? "Try another search or filter."
                : "Put a racer, stock machine and your favorite gadgets on the grid."}
            </p>
            <Link className="button primary" to="/builds/new">
              Create a build
            </Link>
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
