import type { BaseStats, Build, BuildDraft, GameVersion, StatsCatalog } from "./types";
import { useLoad } from "./useLoad";
import { ErrorNotice } from "./components";
import { buildStatsPath, statNames } from "./stats";

export { buildStatsPath } from "./stats";

const statMaximum = 100;
type StatsDraft = Pick<BuildDraft,
  "gameVersionId" | "racerId" | "frontPartId" | "rearPartId" | "tirePartId">;
type StatsBreakdown = { character: BaseStats; machine: BaseStats };

function statLabel(name: typeof statNames[number]) {
  return name[0].toUpperCase() + name.slice(1);
}

function sumStats(values: Array<BaseStats | undefined>): BaseStats {
  return Object.fromEntries(statNames.map((name) => [name,
    values.some((value) => value?.[name] == null)
      ? null
      : values.reduce((sum, value) => sum + (value?.[name] ?? 0), 0),
  ])) as unknown as BaseStats;
}

export function calculateStatsBreakdown(draft: StatsDraft, catalog?: StatsCatalog): StatsBreakdown | undefined {
  if (!catalog || !draft.racerId || !draft.frontPartId || !draft.rearPartId || !draft.tirePartId) return undefined;
  return {
    character: catalog.racers?.[draft.racerId] ?? sumStats([undefined]),
    machine: sumStats([draft.frontPartId, draft.rearPartId, draft.tirePartId]
      .map((id) => catalog.machineParts?.[id])),
  };
}

export function StatsBlock({ stats, version, title = "Base stats", breakdown,
  missingVersionMessage = "Select a game version to see stats." }: {
  stats?: BaseStats; version: string | null; title?: string; breakdown?: StatsBreakdown;
  missingVersionMessage?: string;
}) {
  const known = statNames.filter((name) => stats?.[name] != null).length;
  return <section className="base-stats" aria-label={title}>
    <h3>{title}</h3>
    {!version ? <p>{missingVersionMessage}</p> : <>
      <p className="muted">{known === 0 ? `Stats unavailable for Ver. ${version}`
        : `Ver. ${version}${known < 5 ? " · Partial stats" : ""}`}</p>
      {breakdown && known > 0 && <div className="stat-legend" aria-label="Stat bar breakdown">
        <span><i className="character-swatch" />Character</span>
        <span><i className="machine-swatch" />Machine</span>
      </div>}
      <dl className="stat-bars">{statNames.map((name) => {
        const value = stats?.[name];
        const character = breakdown?.character[name];
        const machine = breakdown?.machine[name];
        const hasBreakdown = value != null && character != null && machine != null;
        const label = statLabel(name);
        const width = value == null ? 0 : Math.min(100, Math.max(0, value));
        const characterWidth = hasBreakdown ? Math.min(100, Math.max(0, character)) : 0;
        const machineWidth = hasBreakdown ? Math.min(100 - characterWidth, Math.max(0, machine)) : 0;
        return <div className={`stat-row stat-${name}`} key={name}>
          <div className="stat-label"><dt>{label}</dt><dd>{value ?? "—"}</dd></div>
          {hasBreakdown && <div className="stat-calculation"
            aria-label={`${label} calculation: Character ${character} plus Machine ${machine} equals ${value}`}>
            <span className="character-value">Character {character}</span>
            <span aria-hidden="true">+</span>
            <span className="machine-value">Machine {machine}</span>
            <span aria-hidden="true">=</span>
            <strong>{value}</strong>
          </div>}
          <div className={`stat-track${value == null ? " unknown" : ""}`} role="progressbar"
            aria-label={`${label}: ${value ?? "unknown"}`} aria-valuemin={0} aria-valuemax={statMaximum}
            aria-valuenow={value ?? undefined}>
            {hasBreakdown ? <>
              <span className="stat-fill stat-character-fill" style={{ width: `${characterWidth}%` }} />
              <span className="stat-fill stat-machine-fill" style={{ width: `${machineWidth}%` }} />
            </> : <span className="stat-fill" style={{ width: `${width}%` }} />}
          </div>
        </div>;
      })}</dl>
    </>}
  </section>;
}

function StatsRequest({ path, version, draft }: { path: string; version: string | null; draft: StatsDraft }) {
  const result = useLoad<BaseStats>(path);
  const catalog = useLoad<StatsCatalog>(draft.gameVersionId
    ? `/stats/catalog?gameVersionId=${encodeURIComponent(draft.gameVersionId)}` : "");
  const breakdown = calculateStatsBreakdown(draft, catalog.data);
  return <div className="base-stats-request">
    {path && (result.loading || catalog.loading) ? <p role="status">Loading base stats…</p>
      : result.error || catalog.error ? <ErrorNotice message={`Could not load base stats: ${result.error || catalog.error}`} />
        : <StatsBlock stats={result.data} version={version} breakdown={breakdown} />}
    <p className="muted">Base stats only. Gadget effects are not included.</p>
  </div>;
}

export function DraftStats({ draft, version }: { draft: StatsDraft; version: GameVersion | null }) {
  const path = buildStatsPath(draft);
  // Remount on selection changes so an earlier response cannot flash as the new setup's stats.
  return <StatsRequest key={path} path={path} version={version?.version ?? null} draft={draft} />;
}

export function BuildStats({ build }: { build: Build }) {
  if (!build.gameVersion) {
    return <StatsBlock version={null} missingVersionMessage="Stats unavailable because this build has no recorded game version." />;
  }
  return <DraftStats draft={{ gameVersionId: build.gameVersion?.id ?? null,
    racerId: build.racer.id, frontPartId: build.frontPart.id,
    rearPartId: build.rearPart.id, tirePartId: build.tirePart.id }} version={build.gameVersion} />;
}
