import { GearIcon, RacerIcon, SteeringWheelIcon } from "../../shared/ui/icons";
import { coverageLabel, GadgetStatImpact, PassiveStatsPanel, signedPoints } from "./PassiveStats";
import { buildStatsWarning, persistedStatsPath, statNames, statPresentation } from "./stats";
import type { Build, BuildPage, BuildStatsResult } from "../../shared/types";
import { useLoad } from "../../shared/hooks/useLoad";

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
    <div className="card-stats-heading"><h3>Stats</h3>
      <details className="stats-explanation"><summary>About stats</summary>
        <div><p>Racer + machine parts, including verified passive gadget adjustments where supported.
          Hover or tap an adjusted bar for its base value and gadget adjustment. Race-dependent effects are excluded.</p>
          <div className="stat-legend"><span><RacerIcon /><i className="character-swatch" />Racer</span>
            <span><SteeringWheelIcon /><i className="machine-swatch" />Machine / Extreme Gear</span>
            <span><GearIcon /><i className="gadget-swatch" />Gadget bonus</span></div>
          <p>Gadget penalties are cross-hatched over the removed portion.</p>
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
  const path = persistedStatsPath(build);
  const result = useLoad<BuildStatsResult>(path);
  if (!path) return <div className="card-stats unavailable">Stats unavailable · Patch unspecified</div>;
  if (result.loading) return <div className="card-stats unavailable">Loading stats…</div>;
  if (result.error) return <div className="card-stats unavailable">Couldn’t load stats.</div>;
  if (!result.data) return <div className="card-stats unavailable">Stats unavailable</div>;
  return <CardStatsContent build={build} stats={result.data} />;
}

function CardStatsContent({ build, stats }: { build: Build; stats: BuildStatsResult }) {
  const warning = buildStatsWarning(build);
  const supported = stats.passive && !["UNSUPPORTED_VERSION", "INVALID_LOADOUT"].includes(stats.passive.coverage);
  const passive = Boolean(supported);
  const values = passive ? stats.passive?.adjusted : stats;
  const unavailable = statNames.every((name) => values?.[name] == null);
  return <>{unavailable && <p className="card-stats-unavailable">Stats unavailable{build.gameVersion
    ? ` for Ver. ${build.gameVersion.version}` : " · Patch unspecified"}</p>}<dl className="card-stats" aria-label={passive ? "Passive stats" : "Base stats"}>
    {statNames.map((name) => {
      const { value, hasBreakdown, label, width, characterWidth, machineWidth } = statPresentation(name, values, stats);
      const gadget = supported ? stats.passive : undefined;
      const baseValue = gadget?.base[name];
      const adjustedValue = gadget?.adjusted[name];
      const adjustment = gadget?.adjustments[name] ?? 0;
      const showAdjustment = baseValue != null && adjustedValue != null && adjustment !== 0
        && gadget?.effects.some(effect => effect.status === "APPLIED" && (effect.adjustment[name] ?? 0) !== 0);
      // Scale only the drawing; the displayed numbers retain their full values, including values above 100.
      const scale = showAdjustment ? 100 / Math.max(100, baseValue!, adjustedValue!) : 1;
      const barWidth = showAdjustment ? Math.max(0, value ?? 0) * scale : width;
      const racerWidth = showAdjustment ? Math.min(100, Math.max(0, stats.character?.[name] ?? 0) * scale) : characterWidth;
      const partsWidth = showAdjustment ? Math.min(100 - racerWidth, Math.max(0, stats.machine?.[name] ?? 0) * scale) : machineWidth;
      return <div className={`card-stat stat-${name}`} key={name}>
        <div><dt>{label}</dt><dd>{value ?? "—"}</dd></div>
        <span className="card-stat-meter">
        <span className={`card-stat-track${value == null ? " unknown" : ""}`}
          role={showAdjustment ? "img" : undefined} aria-hidden={showAdjustment ? undefined : true}
          aria-label={showAdjustment ? `${label}: base ${baseValue}; gadget ${signedPoints(adjustment)}; adjusted ${adjustedValue}. ${adjustment > 0 ? "Outlined extension" : "Hatched reduction"} shows the gadget adjustment.` : undefined}>
          {hasBreakdown ? <>
            <span className="card-stat-character-fill" style={{ width: `${racerWidth}%` }} />
            <span className="card-stat-machine-fill" style={{ width: `${partsWidth}%` }} />
          </> : <span style={{ width: `${barWidth}%` }} />}
          {showAdjustment && <span className={`card-stat-gadget-segment ${adjustment > 0 ? "bonus" : "penalty"}`}
            style={{ left: `${Math.max(0, Math.min(baseValue!, adjustedValue!)) * scale}%`,
              width: `${Math.abs(Math.max(0, adjustedValue!) - Math.max(0, baseValue!)) * scale}%` }} />}
        </span>
        {supported && stats.passive && <GadgetStatImpact name={name} value={stats.passive} />}
        </span>
      </div>;
    })}
  </dl><details className="card-passive-details" onClick={event => event.stopPropagation()}>
    <summary>{!passive && "Base stats · "}{stats.passive ? coverageLabel(stats.passive) : "Gadget information unavailable"} · Details</summary>
    <PassiveStatsPanel value={stats.passive} compact />
  </details>{warning && <p className="card-stats-warning" role="note">⚠ {warning}</p>}</>;
}
