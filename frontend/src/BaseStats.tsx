import type { BaseStats, Build, BuildDraft, GameVersion } from "./types";
import { useLoad } from "./useLoad";
import { ErrorNotice } from "./components";

const statNames = ["speed", "acceleration", "handling", "power", "boost"] as const;

export function StatsBlock({ stats, version, title = "Base stats" }: {
  stats?: BaseStats; version: string | null; title?: string;
}) {
  const known = statNames.filter((name) => stats?.[name] != null).length;
  return <section className="base-stats" aria-label={title}>
    <h3>{title}</h3>
    {!version ? <p>Select a game version to see stats.</p> : <>
      <p className="muted">{known === 0 ? `Stats unavailable for Ver. ${version}`
        : `Ver. ${version}${known < 5 ? " · Partial stats" : ""}`}</p>
      <dl>{statNames.map((name) => <div key={name}>
        <dt>{name[0].toUpperCase() + name.slice(1)}</dt><dd>{stats?.[name] ?? "—"}</dd>
      </div>)}</dl>
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
  return <>
    {path && result.loading ? <p role="status">Loading base stats…</p>
      : result.error ? <ErrorNotice message={`Could not load base stats: ${result.error}`} />
        : <StatsBlock stats={result.data} version={version} />}
    <p className="muted">Base stats only. Gadget effects are not included.</p>
  </>;
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
