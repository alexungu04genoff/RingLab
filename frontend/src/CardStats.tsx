import { RacerIcon, SteeringWheelIcon } from "./icons";
import { buildStatsPath, buildStatsWarning, statNames, statPresentation } from "./stats";
import type { Build, BuildPage, BuildStatsResult } from "./types";
import { useLoad } from "./useLoad";

export type PageCardStats = { status: "ready"; value: BuildStatsResult }
  | { status: "not-requested" | "pending" | "failed" };

export function pageCardStats(page: BuildPage, id: string): PageCardStats {
  if (page.statsError) return { status: "failed" };
  if (!page.statsByBuildId) return { status: "not-requested" };
  const value = page.statsByBuildId[id];
  return value ? { status: "ready", value } : { status: "failed" };
}

export function CardStats({ build, stats, pageStats }: { build: Build; stats?: BuildStatsResult; pageStats?: PageCardStats }) {
  return <section className="card-stats-section">
    <div className="card-stats-heading"><h3>Base stats</h3>
      <details className="stats-explanation"><summary>About base stats</summary>
        <div><p>Racer + machine parts. Gadget effects not included.</p>
          <div className="stat-legend"><span><RacerIcon /><i className="character-swatch" />Racer</span>
            <span><SteeringWheelIcon /><i className="machine-swatch" />Machine parts</span></div>
        </div>
      </details>
    </div>
    {pageStats ? (pageStats.status === "ready" ? <CardStatsContent build={build} stats={pageStats.value} />
      : <div className="card-stats unavailable">{pageStats.status === "pending" ? "Loading stats…"
        : pageStats.status === "failed" ? "Couldn’t load stats." : "Stats not included in this page."}</div>)
      : stats ? <CardStatsContent build={build} stats={stats} /> : <RequestedCardStats build={build} />}
  </section>;
}

function RequestedCardStats({ build }: { build: Build }) {
  const path = buildStatsPath({ gameVersionId: build.gameVersion?.id ?? null,
    racerId: build.racer.id, frontPartId: build.frontPart.id,
    rearPartId: build.rearPart.id, tirePartId: build.tirePart?.id ?? null });
  const result = useLoad<BuildStatsResult>(path);
  if (!path) return <div className="card-stats unavailable">Stats unavailable · Patch unspecified</div>;
  if (result.loading) return <div className="card-stats unavailable">Loading stats…</div>;
  if (result.error) return <div className="card-stats unavailable">Couldn’t load stats.</div>;
  if (!result.data) return <div className="card-stats unavailable">Stats unavailable</div>;
  return <CardStatsContent build={build} stats={result.data} />;
}

function CardStatsContent({ build, stats }: { build: Build; stats: BuildStatsResult }) {
  const warning = buildStatsWarning(build);
  const unavailable = statNames.every((name) => stats[name] == null);
  return <>{unavailable && <p className="card-stats-unavailable">Stats unavailable{build.gameVersion
    ? ` for Ver. ${build.gameVersion.version}` : " · Patch unspecified"}</p>}<dl className="card-stats" aria-label="Base stats">
    {statNames.map((name) => {
      const { value, hasBreakdown, label, width, characterWidth, machineWidth } = statPresentation(name, stats, stats);
      return <div className={`card-stat stat-${name}`} key={name}>
        <div><dt>{label}</dt><dd>{value ?? "—"}</dd></div>
        <span className={`card-stat-track${value == null ? " unknown" : ""}`} aria-hidden="true">
          {hasBreakdown ? <>
            <span className="card-stat-character-fill" style={{ width: `${characterWidth}%` }} />
            <span className="card-stat-machine-fill" style={{ width: `${machineWidth}%` }} />
          </> : <span style={{ width: `${width}%` }} />}
        </span>
      </div>;
    })}
  </dl>{warning && <p className="card-stats-warning" role="note">⚠ {warning}</p>}</>;
}
