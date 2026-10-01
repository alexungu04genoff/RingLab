import { useEffect, useId, useState } from "react";
import { api, json } from "../../shared/api/api";
import { statNames } from "./stats";
import { StatBar, StatLegend } from "../../shared/ui/StatBar";
import type { BaseStats, Build, BuildDraft, PassiveStatsResult, ScenarioRulesCatalog } from "../../shared/types";

export type ScenarioSelection = Pick<BuildDraft,
  "gameVersionId" | "racerId" | "frontPartId" | "rearPartId" | "tirePartId" | "gadgetIds">;
type Selection = { label: string; selection: ScenarioSelection };
export interface ScenarioContext {
  lap: number | null;
  vehicleForm: "NORMAL" | "WATER" | "FLIGHT" | null;
  ringsHeld: number | null;
  landingBoostActive: boolean | null;
  distanceToFinish: number | null;
}
export interface ScenarioResult {
  passive: PassiveStatsResult;
  adjustments: BaseStats;
  knownSubtotal: BaseStats;
  total: BaseStats | null;
  coverage: "CALCULATED" | "PARTIAL" | "UNAVAILABLE";
  ruleset: string; supportedVersion: string; note: string;
  effects: {
    gadgetId: string; gadgetName: string; effectId: string; label: string;
    status: "ACTIVE_AND_APPLIED" | "CONDITION_NOT_MET" | "CONDITION_UNKNOWN" | "ACTIVE_NON_STAT" | "UNSUPPORTED";
    statEffect: boolean; adjustment: BaseStats | null; explanation: string; sources: string[];
  }[];
}
const unspecified: ScenarioContext = {
  lap: null, vehicleForm: null, ringsHeld: null, landingBoostActive: null, distanceToFinish: null,
};

export function scenarioSelection(build: Build): ScenarioSelection {
  return { gameVersionId: build.gameVersion?.id ?? null, racerId: build.racer.id,
    frontPartId: build.frontPart.id, rearPartId: build.rearPart.id, tirePartId: build.tirePart?.id ?? null,
    gadgetIds: build.gadgets.map(g => g.id) };
}

export function ScenarioPreview({ selections }: { selections: Selection[] }) {
  const [enabled, setEnabled] = useState(false);
  const id = useId();
  return <section className="scenario-preview" aria-labelledby={id}>
    <div className="scenario-heading"><h3 id={id}>Scenario Preview</h3>
      <button type="button" className="small" aria-expanded={enabled} onClick={() => setEnabled(!enabled)}>
        {enabled ? "Reset to Passive only" : "Try a scenario"}
      </button></div>
    {!enabled ? <p className="muted">Passive only. Try supported race conditions without changing this build.</p>
      : <CustomScenario selections={selections} />}
  </section>;
}

function CustomScenario({ selections }: { selections: Selection[] }) {
  // An unmatched form ID intentionally detaches these ephemeral controls from an
  // enclosing build editor form: invalid preview input must never block saving.
  const detachedFormId = useId();
  const [context, setContext] = useState<ScenarioContext>(unspecified);
  const [catalog, setCatalog] = useState<ScenarioRulesCatalog>();
  const [catalogError, setCatalogError] = useState("");
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setCatalogError("");
    api<ScenarioRulesCatalog>("/stats/scenario-rules", { anonymous: true, signal: controller.signal })
      .then(data => { if (!controller.signal.aborted) setCatalog(data); })
      .catch(error => { if (!controller.signal.aborted) setCatalogError(error.message); });
    return () => controller.abort();
  }, [retry]);
  const ids = new Set(selections.flatMap(s => s.selection.gadgetIds));
  const relevant = catalog?.controls.filter(c => ids.has(c.gadgetId)) ?? [];
  const fields = new Set(relevant.map(c => c.field));
  const set = <K extends keyof ScenarioContext>(field: K, value: ScenarioContext[K]) =>
    setContext(current => ({ ...current, [field]: value }));
  return <>
    <p className="muted">Custom scenario · {selections.length > 1 ? "The same conditions apply to both builds. " : ""}
      These choices are not saved. Recommendations still use passive stats.</p>
    {catalogError ? <p role="alert">Scenario controls unavailable: {catalogError} <button type="button"
      onClick={() => setRetry(n => n + 1)}>Retry controls</button></p>
      : !catalog ? <p role="status">Loading scenario controls…</p> : <>
        {!relevant.some(c => c.statEffect) && <p>No selected gadget has a supported scenario stat effect.</p>}
        <div className="scenario-controls">
          {fields.has("LAP") && <label>Lap<select form={detachedFormId} value={context.lap ?? ""}
            onChange={e => set("lap", e.target.value ? Number(e.target.value) : null)}>
            <option value="">Not specified</option>{[1, 2, 3].map(n => <option key={n} value={n}>Lap {n}</option>)}
          </select></label>}
          {fields.has("VEHICLE_FORM") && <label>Vehicle form<select form={detachedFormId} value={context.vehicleForm ?? ""}
            onChange={e => set("vehicleForm", (e.target.value || null) as ScenarioContext["vehicleForm"])}>
            <option value="">Not specified</option><option value="NORMAL">Normal</option>
            <option value="WATER">Water</option><option value="FLIGHT">Flight</option>
          </select></label>}
          {fields.has("RINGS_HELD") && <label>Rings held now<input form={detachedFormId} type="number" min="0" max="999" step="1"
            placeholder="Not specified" value={context.ringsHeld ?? ""}
            onChange={e => set("ringsHeld", e.target.value === "" ? null : Number(e.target.value))} />
            <small>Current rings, not total rings collected.</small></label>}
          {fields.has("LANDING_BOOST_ACTIVE") && <label>Successful landing boost<select form={detachedFormId}
            value={context.landingBoostActive === null ? "" : String(context.landingBoostActive)}
            onChange={e => set("landingBoostActive", e.target.value === "" ? null : e.target.value === "true")}>
            <option value="">Not specified</option><option value="false">Not active</option><option value="true">Active now</option>
          </select></label>}
          {fields.has("DISTANCE_TO_FINISH") && <label>Metres to finish<input form={detachedFormId} type="number" min="0" max="50000" step="1"
            placeholder="Not specified" value={context.distanceToFinish ?? ""}
            onChange={e => set("distanceToFinish", e.target.value === "" ? null : Number(e.target.value))} /></label>}
        </div>
        <ScenarioRequests selections={selections} context={context} />
      </>}
  </>;
}

function ScenarioRequests({ selections, context }: { selections: Selection[]; context: ScenarioContext }) {
  // Serialize only calculation inputs. No titles, maps, ownership, build ID or remix provenance enter the API.
  const requests = JSON.stringify(selections.map(({ selection: s }) => ({
    gameVersionId: s.gameVersionId || null, racerId: s.racerId || null, frontPartId: s.frontPartId || null,
    rearPartId: s.rearPartId || null, tirePartId: s.tirePartId || null, gadgetIds: s.gadgetIds, scenario: context,
  })));
  const [retry, setRetry] = useState(0);
  const [state, setState] = useState<{ key: string; retry: number; results: PromiseSettledResult<ScenarioResult>[] }>();
  const valid = (context.ringsHeld === null || Number.isInteger(context.ringsHeld) && context.ringsHeld >= 0 && context.ringsHeld <= 999)
    && (context.distanceToFinish === null || Number.isInteger(context.distanceToFinish) && context.distanceToFinish >= 0 && context.distanceToFinish <= 50000);
  useEffect(() => {
    if (!valid) return;
    const controller = new AbortController();
    Promise.allSettled((JSON.parse(requests) as unknown[]).map(body =>
      api<ScenarioResult>("/stats/scenario-build", { ...json("POST", body), anonymous: true, signal: controller.signal })))
      .then(results => { if (!controller.signal.aborted) setState({ key: requests, retry, results }); });
    return () => controller.abort();
  }, [requests, retry, valid]);
  if (!valid) return <p role="alert">Use whole numbers: rings held 0–999 and metres to finish 0–50,000.</p>;
  // Hide obsolete results in the render before effect cleanup, even if fetch ignores abort.
  if (state?.key !== requests || state.retry !== retry) return <p role="status">Calculating scenario preview…</p>;
  const conditions = [context.lap != null && `Lap ${context.lap}`,
    context.vehicleForm && context.vehicleForm[0] + context.vehicleForm.slice(1).toLowerCase(),
    context.ringsHeld != null && `${context.ringsHeld} rings held`,
    context.landingBoostActive != null && (context.landingBoostActive ? "Landing boost active" : "Landing boost inactive"),
    context.distanceToFinish != null && `${context.distanceToFinish} m to finish`].filter(Boolean);
  // Keep both comparison columns on the same visual scale, including raw values above 100.
  const maximum = Math.max(100, ...state.results.flatMap(result => result.status === "fulfilled"
    ? statNames.flatMap(stat => [result.value.passive.base[stat] ?? 0, result.value.passive.adjusted[stat] ?? 0,
      result.value.knownSubtotal[stat] ?? 0, result.value.total?.[stat] ?? 0]) : []));
  return <>
    <p className="scenario-condition-summary">{conditions.join(" · ") || "Conditions not specified"}</p>
    <StatLegend scenario />
    <div className={selections.length > 1 ? "scenario-results compare-grid" : "scenario-results"}>
    {state.results.map((result, i) => <div key={i} aria-label={`Scenario result: ${selections[i].label}`}>
      {selections.length > 1 && <h3>{selections[i].label}</h3>}
      {result.status === "rejected" ? <p role="alert">Scenario unavailable: {result.reason.message}
        {" "}<button type="button" onClick={() => setRetry(n => n + 1)}>Retry preview</button></p>
        : <ScenarioValues result={result.value} maximum={maximum} />}
    </div>)}
  </div></>;
}

const groups: [ScenarioResult["effects"][number]["status"], string][] = [
  ["ACTIVE_AND_APPLIED", "Applied in this scenario"], ["CONDITION_NOT_MET", "Not active in this scenario"],
  ["CONDITION_UNKNOWN", "Condition not specified"], ["ACTIVE_NON_STAT", "Active non-stat effects"],
  ["UNSUPPORTED", "Unsupported effects"],
];
const number = (value: number | null) => value == null ? "Unknown" : String(value);
const signed = (value: number | null) => value == null ? "Unknown" : value >= 0 ? `+${value}` : `−${Math.abs(value)}`;

function ScenarioValues({ result, maximum }: { result: ScenarioResult; maximum: number }) {
  const complete = result.coverage === "CALCULATED";
  const applied = [...new Set(result.effects.filter(e => e.status === "ACTIVE_AND_APPLIED").map(e => e.gadgetName))];
  const utility = [...new Set(result.effects.filter(e => e.status === "ACTIVE_NON_STAT").map(e => e.gadgetName))];
  return <>
    <p className="scenario-coverage" role="status">{complete ? "Scenario total — supported stat effects calculated."
      : result.coverage === "UNAVAILABLE" ? "Scenario unavailable — passive information shown below."
        : "Scenario not fully calculated — final total unavailable."}</p>
    {applied.length > 0 && <div className="scenario-active-effects" aria-label="Applied scenario effects">
      <span>Applied:</span><ul>{applied.map(name => <li key={name}><span aria-hidden="true">✓ </span>{name}</li>)}</ul>
    </div>}
    {utility.length > 0 && <p>Active without stat points: {utility.join(" · ")}</p>}
    {result.passive.coverage !== "CALCULATED" && <p className="muted">Passive calculation is also incomplete
      ({result.passive.coverage.toLowerCase().replaceAll("_", " ")}). {result.passive.note}</p>}
    <dl className="passive-stat-bars scenario-stat-bars">
      {statNames.map(stat => {
        const label = stat[0].toUpperCase() + stat.slice(1);
        const passive = result.passive.adjusted[stat];
        const delta = result.adjustments[stat];
        const exact = complete && result.total?.[stat] != null;
        const known = !exact && result.coverage === "PARTIAL" && result.knownSubtotal[stat] != null && delta != null;
        const endpoint = exact ? result.total![stat] : known ? result.knownSubtotal[stat] : null;
        const adjustment = delta == null ? "unknown" : delta < 0 ? `minus ${Math.abs(delta)}` : `plus ${delta}`;
        const description = `${label}: passive ${number(passive)}; ${exact || known ? `scenario adjustment ${adjustment}; ${exact ? "scenario total" : "known subtotal"} ${endpoint}${known ? "; final total unavailable" : ""}` : "not fully calculated; final total unavailable"}.`;
        return <div className={`stat-row stat-${stat}${known ? " scenario-subtotal" : ""}`} key={stat}>
          <div className="stat-label"><dt>{label}</dt><dd aria-label={known ? `${label} known subtotal` : undefined}>{endpoint ?? "—"}</dd></div>
          <div className="scenario-stat-comparison">
            <span>Passive {number(passive)}{exact ? ` → Scenario ${endpoint}` : known ? ` → Known subtotal ${endpoint}` : ""}</span>
            <span className="stat-adjustment">{exact || known ? signed(delta) : "Not fully calculated"}</span>
          </div>
          {known && <p className="scenario-known-subtotal">Known effects only · final total unavailable</p>}
          <StatBar base={result.passive.base[stat]} racer={result.passive.base.character?.[stat] ?? null}
            machine={result.passive.base.machine?.[stat] ?? null} passive={passive}
            gadgetDelta={result.passive.adjustments[stat]} scenario={endpoint} scenarioDelta={delta}
            maximum={maximum} description={description} />
        </div>;
      })}
    </dl>
    <details className="scenario-effects"><summary>Scenario effects &amp; details</summary>
      <p className="muted">{result.note}</p>
      {groups.map(([status, label]) => {
        const effects = result.effects.filter(e => e.status === status);
        return effects.length ? <section key={status}><h4>{label}</h4><ul>{effects.map(e => <li key={`${e.gadgetId}/${e.effectId}`}>
          <strong>{e.gadgetName} · {e.label}</strong><p>{e.explanation}</p>
        </li>)}</ul></section> : null;
      })}
      {!result.effects.length && <p>No conditional gadget effects selected.</p>}
      <p className="muted">Ver. {result.supportedVersion} · {result.ruleset}</p>
    </details>
  </>;
}
