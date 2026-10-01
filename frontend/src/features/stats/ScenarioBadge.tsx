import type { ScenarioRulesCatalog } from "../../shared/types";

// The API groups these three current conditions under VEHICLE_FORM. These are
// display labels only; membership in the scenario catalog determines support.
const formLabels: Record<string, string> = {
  "70000000-0000-4000-8000-000000000015": "Water",
  "70000000-0000-4000-8000-000000000017": "Flight",
  "70000000-0000-4000-8000-000000000018": "Form",
};
const fieldLabels = {
  LAP: "Lap", VEHICLE_FORM: "Form", RINGS_HELD: "Rings",
  LANDING_BOOST_ACTIVE: "Event", DISTANCE_TO_FINISH: "Event",
};

export function ScenarioBadge({ gadgetId, catalog }: { gadgetId: string; catalog?: ScenarioRulesCatalog }) {
  const labels = [...new Set((catalog?.controls ?? []).filter(control => control.gadgetId === gadgetId)
    .map(control => control.field === "VEHICLE_FORM"
      ? formLabels[gadgetId] ?? "Form" : fieldLabels[control.field]))];
  return labels.length ? <span className="scenario-badge" title="Supported by Scenario Preview">
    Scenario · {labels.join(" / ")}
  </span> : null;
}
