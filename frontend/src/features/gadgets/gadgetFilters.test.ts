import { expect, it } from "vitest";
import type { Gadget, GadgetRulesCatalog, ScenarioRulesCatalog } from "../../shared/types";
import { gadgetPresentation } from "./gadgetPresentation";
import { emptyGadgetFilters, matchesGadgetFilters, type GadgetFilters } from "./gadgetFilters";

const points = { speed: 0, acceleration: 0, handling: 0, power: 0, boost: 0 };
const effect = (kind: "PASSIVE" | "CONDITIONAL" | "NON_STAT" | "UNSUPPORTED",
  subject: "ANY" | "RACER" | "MACHINE" = "ANY", requiredType: "SPEED" | "BOOST" | null = null) => ({
  kind, subject, requiredType, effectId: kind, label: "", matching: points, nonMatching: points,
  explanation: "", sources: [], stackingGroup: null,
});
const gadgets: Gadget[] = [
  { id: "mixed", name: "Neutral name", slotCost: 1, acquisitionKind: "FESTIVAL_REWARD", acquisitionLabel: "Reviewed Festival", description: null, imagePath: null },
  { id: "race", name: "Race gadget", slotCost: 2, acquisitionKind: "STANDARD_UNLOCK", description: null, imagePath: null },
  { id: "utility", name: "Utility gadget", slotCost: 3, acquisitionKind: "STANDARD_UNLOCK", description: null, imagePath: null },
  { id: "unsupported", name: "Speed in name is not evidence", slotCost: 1, acquisitionKind: "UNKNOWN", description: null, imagePath: null },
  { id: "missing", name: "Missing metadata", slotCost: null, description: null, imagePath: null },
];
const rules: GadgetRulesCatalog = { ruleset: "reviewed", supportedVersion: "1.4.1", note: "", gadgets: [
  { gadgetId: "mixed", effects: [effect("PASSIVE", "RACER", "SPEED"), effect("CONDITIONAL", "MACHINE", "BOOST")] },
  { gadgetId: "race", effects: [effect("CONDITIONAL")] },
  { gadgetId: "utility", effects: [effect("NON_STAT")] },
  { gadgetId: "unsupported", effects: [effect("UNSUPPORTED")] },
] };
const scenarios: ScenarioRulesCatalog = { supportedVersion: "1.4.1", controls: [
  { gadgetId: "utility", field: "LANDING_BOOST_ACTIVE", statEffect: false },
] };
const results = (filters: Partial<GadgetFilters>) => gadgets
  .filter(gadget => matchesGadgetFilters(gadgetPresentation(gadget, rules, scenarios), { ...emptyGadgetFilters, ...filters }))
  .map(gadget => gadget.id);

it("filters one or multiple costs and does not treat unknown cost as zero", () => {
  expect(results({ slots: [1] })).toEqual(["mixed", "unsupported"]);
  expect(results({ slots: [1, 2] })).toEqual(["mixed", "race", "unsupported"]);
  expect(results({})).toHaveLength(5);
});

it.each([
  ["PASSIVE", ["mixed"]], ["CONDITIONAL", ["mixed", "race"]], ["NON_STAT", ["utility"]],
  ["UNSUPPORTED", ["unsupported"]], ["SCENARIO", ["utility"]],
] as const)("filters %s using reviewed effects and actual scenario controls", (kind, expected) => {
  expect(results({ effects: [kind] })).toEqual(expected);
});

it("uses OR within effects, types and acquisition, and AND between groups", () => {
  expect(results({ effects: ["PASSIVE", "NON_STAT"] })).toEqual(["mixed", "utility"]);
  expect(results({ types: ["SPEED", "BOOST"] })).toEqual(["mixed"]);
  expect(results({ acquisition: ["FESTIVAL_REWARD", "STANDARD_UNLOCK"] })).toEqual(["mixed", "race", "utility"]);
  expect(results({ slots: [1, 2], effects: ["PASSIVE", "NON_STAT"], acquisition: ["FESTIVAL_REWARD"] })).toEqual(["mixed"]);
  expect(results({ slots: [2], effects: ["PASSIVE"] })).toEqual([]);
});

it("derives type requirements from subject metadata, never names or generic effects", () => {
  expect(results({ types: ["SPEED"] })).toEqual(["mixed"]);
  const inconsistent = { ...rules, gadgets: [{ gadgetId: "mixed", effects: [effect("PASSIVE", "ANY", "SPEED")] }] };
  expect(gadgetPresentation(gadgets[0], inconsistent).appliesTo).toEqual([]);
});

it("keeps missing acquisition unknown and excludes it from standard unlocks", () => {
  expect(results({ acquisition: ["FESTIVAL_REWARD"] })).toEqual(["mixed"]);
  expect(results({ acquisition: ["STANDARD_UNLOCK"] })).toEqual(["race", "utility"]);
  expect(results({ acquisition: ["UNKNOWN"] })).toEqual(["unsupported", "missing"]);
});
