import type { BaseStats, Build, BuildDraft, BuildStatsResult, GameVersion } from "./types";
import { useLoad } from "./useLoad";
import { ErrorNotice } from "./components";
import { buildStatsPath, buildStatsWarning, statNames, statPresentation } from "./stats";
import type { StatsBreakdown } from "./stats";
import { RacerIcon, SteeringWheelIcon } from "./icons";

export { buildStatsPath } from "./stats";

const statMaximum = 100;
type StatsDraft = Pick<BuildDraft,
  "gameVersionId" | "racerId" | "frontPartId" | "rearPartId" | "tirePartId" | "machineType">;
export function StatsBlock({ stats, version, title = "Base stats", breakdown,
  missingVersionMessage = "Select a game version to see stats.", warning }: {
  stats?: BaseStats; version: string | null; title?: string; breakdown?: StatsBreakdown;
  missingVersionMessage?: string; warning?: string | null;
}) {
  const known = statNames.filter((name) => stats?.[name] != null).length;
  return <section className="base-stats" aria-label={title}>
    <h3>{title}</h3>
    {!version ? <p>{missingVersionMessage}</p> : <>
      <p className="muted">{known === 0 ? `Stats unavailable for Ver. ${version}`
        : `Ver. ${version}${known < 5 ? " · Partial stats" : ""}`}</p>
      {warning && <p className="stats-compatibility-warning" role="note">{warning}</p>}
      {breakdown && known > 0 && <div className="stat-legend" aria-label="Stat bar breakdown">
        <span><RacerIcon /><i className="character-swatch" />Character</span>
        <span><SteeringWheelIcon /><i className="machine-swatch" />Machine</span>
      </div>}
      <dl className="stat-bars">{statNames.map((name) => {
        const { value, character, machine, hasBreakdown, label, width,
          characterWidth, machineWidth } = statPresentation(name, stats, breakdown);
        return <div className={`stat-row stat-${name}`} key={name}>
          <div className="stat-label">
            <dt>{label}</dt>
            {hasBreakdown && <span className="stat-calculation"
              aria-label={`${label} components: Character ${character} plus Machine ${machine}`}>
              <span className="character-value">Character {character}</span>
              <span aria-hidden="true">+</span>
              <span className="machine-value">Machine {machine}</span>
            </span>}
            <dd>{value ?? "—"}</dd>
          </div>
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

function StatsRequest({ path, version, warning }: {
  path: string; version: string | null; warning?: string | null;
}) {
  const result = useLoad<BuildStatsResult>(path);
  return <div className="base-stats-request">
    {path && result.loading ? <p role="status">Loading base stats…</p>
      : result.error ? <ErrorNotice message={`Could not load base stats: ${result.error}`} />
        : <StatsBlock stats={result.data} version={version} breakdown={result.data} warning={warning} />}
    <p className="muted">Base stats only. Gadget effects are not included.</p>
  </div>;
}

export function DraftStats({ draft, version, warning }: {
  draft: StatsDraft; version: GameVersion | null; warning?: string | null;
}) {
  const path = buildStatsPath(draft);
  // Remount on selection changes so an earlier response cannot flash as the new setup's stats.
  return <StatsRequest key={path} path={path} version={version?.version ?? null} warning={warning} />;
}

export function BuildStats({ build }: { build: Build }) {
  if (!build.gameVersion) {
    return <StatsBlock version={null} missingVersionMessage="Stats unavailable because this build has no recorded game version." />;
  }
  return <DraftStats draft={{ gameVersionId: build.gameVersion?.id ?? null,
    racerId: build.racer.id, frontPartId: build.frontPart.id,
    rearPartId: build.rearPart.id, tirePartId: build.tirePart?.id ?? null,
    machineType: build.frontPart.racingType }} version={build.gameVersion}
    warning={buildStatsWarning(build)} />;
}
