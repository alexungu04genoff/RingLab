import { GearIcon, RacerIcon, SteeringWheelIcon } from "./icons";

export function StatLegend({ scenario = false }: { scenario?: boolean }) {
  return <div className="stat-legend" aria-label="Stat bar breakdown">
    <span><RacerIcon /><i className="character-swatch" />Racer</span>
    <span><SteeringWheelIcon /><i className="machine-swatch" />Machine</span>
    <span><GearIcon /><i className="gadget-swatch" />{scenario ? "Passive gadget" : "Gadget"}</span>
    {scenario && <span><i className="scenario-swatch" />Scenario</span>}
  </div>;
}

/** Geometry only: all endpoints and adjustments come from the API. */
export function StatBar({ base, racer, machine, passive, gadgetDelta, scenario, scenarioDelta, maximum = 100, description }: {
  base: number | null; racer: number | null; machine: number | null;
  passive: number | null; gadgetDelta: number | null;
  scenario?: number | null; scenarioDelta?: number | null; maximum?: number; description: string;
}) {
  const scale = 100 / Math.max(100, maximum, base ?? 0, passive ?? 0, scenario ?? 0);
  const position = (value: number) => Math.max(0, value) * scale;
  const racerWidth = Math.min(100, position(racer ?? 0));
  const machineWidth = Math.min(100 - racerWidth, position(machine ?? 0));
  const segment = (from: number, to: number) => ({
    left: `${position(Math.min(from, to))}%`, width: `${Math.abs(position(to) - position(from))}%`,
  });
  return <div className={`card-stat-track${passive == null ? " unknown" : ""}`} role="img" aria-label={description}>
    {racer != null && machine != null ? <>
      <span className="card-stat-character-fill" style={{ width: `${racerWidth}%` }} />
      <span className="card-stat-machine-fill" style={{ width: `${machineWidth}%` }} />
    </> : <span style={{ width: `${position(base ?? passive ?? 0)}%` }} />}
    {base != null && passive != null && gadgetDelta != null && gadgetDelta !== 0 &&
      <span className={`card-stat-gadget-segment ${gadgetDelta > 0 ? "bonus" : "penalty"}`} style={segment(base, passive)} />}
    {passive != null && scenario != null && scenarioDelta != null && scenarioDelta !== 0 && <>
      <span className={`card-stat-scenario-segment ${scenarioDelta > 0 ? "bonus" : "penalty"}`} style={segment(passive, scenario)} />
      <span className="card-stat-scenario-end" style={{ left: `${position(scenario)}%` }} />
    </>}
  </div>;
}
