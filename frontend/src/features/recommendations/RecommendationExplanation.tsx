import { racingTypeLabel } from "../../shared/ui/RacingTypeBadge";
import type { RecommendationResult } from "./recommendation";
import type { RacingType } from "../../shared/types";

export const recommendationOutcomes: Record<RecommendationResult["outcome"], { title: string; description: string }> = {
  ESTABLISHED: { title: "Recommended setup", description: "The search proved this choice under your settings and the reviewed passive stats." },
  BEST_FOUND: { title: "Best setup found so far", description: "The search reached its limit. This is a usable build, but a better match may exist." },
  LIMIT_WITHOUT_CANDIDATE: { title: "Search limit reached", description: "The search stopped before finding a usable build. This does not mean no legal build exists. Try keeping your current gadgets or locking more selections." },
  NO_LEGAL_COMPLETION: { title: "No legal setup", description: "No complete build fits these locks, owned items and gadget settings. Review them and try again." },
  NO_FEASIBLE_CANDIDATE: { title: "No setup meets these limits", description: "Legal builds exist, but none meet the established recommendation constraints." },
  UNAVAILABLE: { title: "Recommendation unavailable", description: "Required stats or rules are missing or unsupported. Details below explain what could not be evaluated." },
};

export function RecommendationExplanation({ result, machineType, mode, priorities, ignored, losses }: {
  result: RecommendationResult; machineType: RacingType | null; mode: "STRICT" | "BALANCED";
  priorities: RacingType[]; ignored: RacingType[]; losses: Record<RacingType, string>;
}) {
  const active = priorities.filter(stat => mode === "STRICT" || !ignored.includes(stat));
  return <section aria-label="Recommendation explanation">
    <p role="status">{recommendationOutcomes[result.outcome].description}</p>
    <p>{result.reason}</p>
    <p>Machine type: {racingTypeLabel(machineType)}. Locked selections stay.</p>
    <p><strong>Priority order: </strong>{active.length ? active.map(stat =>
      `${racingTypeLabel(stat)}${mode === "BALANCED" ? ` (${losses[stat]}% sacrifice)` : ""}`).join(" → ") : "All stats ignored"}</p>
    {mode === "BALANCED" && ignored.length > 0 && <p>Ignored: {priorities.filter(stat => ignored.includes(stat)).map(racingTypeLabel).join(", ")}. These stats do not affect the choice or break ties.</p>}
    {mode === "STRICT" ? <p>The first differing stat in your priority order decides. Earlier stats always take priority.</p>
      : <>
        {result.balanced && <details className="balanced-explanation"><summary>Balanced filtering stages</summary>
          {!result.balanced.proven ? <p>{result.outcome === "BEST_FOUND" || result.outcome === "LIMIT_WITHOUT_CANDIDATE"
            ? "The search limit prevented proof. Stage thresholds are withheld because they are not proven."
            : "Stage thresholds are unavailable because the search could not establish them."}</p>
            : !result.balanced.stages.length ? <p>All stats were ignored. Convenience ordering selected this setup.</p>
            : <div className="balanced-priorities-scroll" tabIndex={0} role="region" aria-label="Scrollable filtering stages"><table className="balanced-stage-table" aria-label="Balanced filtering stages">
              <thead><tr><th scope="col">Stat</th><th scope="col">Allowed loss</th><th scope="col">Best among survivors</th><th scope="col">Minimum</th><th scope="col">Builds remaining</th></tr></thead>
              <tbody>{result.balanced.stages.map(stage => <tr key={stage.stat}><th scope="row">{racingTypeLabel(stage.stat)}</th>
                <td>{stage.lossPercent}%</td><td>{stage.best}</td><td>{stage.threshold}</td><td>{stage.candidatesBefore} → {stage.candidatesAfter}</td></tr>)}</tbody>
            </table></div>}
        </details>}
        {active.length > 0 && <p>After filtering, the first differing active stat in your original priority order chooses between the remaining builds.</p>}
      </>}
    <p>Exact ties prefer fewer changed selections, fewer added gadgets, then lower gadget slot cost. A stable selection order resolves any remaining tie.</p>
    <details className="balanced-explanation"><summary>Search details</summary>
      <ul>{result.restrictions.map(restriction => <li key={restriction}>{restriction}</li>)}</ul>
      <p>{result.note}</p><small>Ruleset: {result.ruleset} · {result.work} search steps · {result.elapsedMillis} ms</small>
    </details>
  </section>;
}
