import type { BaseStats, Build, BuildDraft, GameVersion } from "./types";
import { useLoad } from "./useLoad";
import { ErrorNotice } from "./components";

const statNames = ["speed", "acceleration", "handling", "power", "boost"] as const;
const statMaximum = 100;

function statLabel(name: typeof statNames[number]) {
  return name[0].toUpperCase() + name.slice(1);
}

export function StatsBlock({ stats, version, title = "Base stats" }: {
  stats?: BaseStats; version: string | null; title?: string;
}) {
  const known = statNames.filter((name) => stats?.[name] != null).length;
  return <section className="base-stats" aria-label={title}>
    <h3>{title}</h3>
    {!version ? <p>Select a game version to see stats.</p> : <>
      <p className="muted">{known === 0 ? `Stats unavailable for Ver. ${version}`
        : `Ver. ${version}${known < 5 ? " · Partial stats" : ""}`}</p>
      <dl className="stat-bars">{statNames.map((name) => {
        const value = stats?.[name];
        const label = statLabel(name);
        const width = value == null ? 0 : Math.min(100, Math.max(0, value));
        return <div className={`stat-row stat-${name}`} key={name}>
          <div className="stat-label"><dt>{label}</dt><dd>{value ?? "—"}</dd></div>
          <div className={`stat-track${value == null ? " unknown" : ""}`} role="progressbar"
            aria-label={`${label}: ${value ?? "unknown"}`} aria-valuemin={0} aria-valuemax={statMaximum}
            aria-valuenow={value ?? undefined}>
            <span className="stat-fill" style={{ width: `${width}%` }} />
          </div>
        </div>;
      })}</dl>
    </>}
  </section>;
}

export function buildStatsPath(draft: Pick<BuildDraft,
  "gameVersionId" | "racerId" | "frontPartId" | "rearPartId" | "tirePartId">) {
  if (!draft.gameVersionId) return "";
  const params = new URLSearchParams();
  for (const key of ["gameVersionId", "racerId", "frontPartId", "rearPartId", "tirePartId"] as const) {
    if (draft[key]) params.set(key, draft[key]);
  }
  return `/stats/build?${params}`;
}

function StatsRequest({ path, version }: { path: string; version: string | null }) {
  const result = useLoad<BaseStats>(path);
  return <div className="base-stats-request">
    {path && result.loading ? <p role="status">Loading base stats…</p>
      : result.error ? <ErrorNotice message={`Could not load base stats: ${result.error}`} />
        : <StatsBlock stats={result.data} version={version} />}
    <p className="muted">Base stats only. Gadget effects are not included.</p>
  </div>;
}

export function DraftStats({ draft, version }: { draft: Pick<BuildDraft,
  "gameVersionId" | "racerId" | "frontPartId" | "rearPartId" | "tirePartId">; version: GameVersion | null }) {
  const path = buildStatsPath(draft);
  // Remount on selection changes so an earlier response cannot flash as the new setup's stats.
  return <StatsRequest key={path} path={path} version={version?.version ?? null} />;
}

export function BuildStats({ build }: { build: Build }) {
  return <DraftStats draft={{ gameVersionId: build.gameVersion?.id ?? null,
    racerId: build.racer.id, frontPartId: build.frontPart.id,
    rearPartId: build.rearPart.id, tirePartId: build.tirePart.id }} version={build.gameVersion} />;
}
