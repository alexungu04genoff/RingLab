import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, it } from "vitest";
import { GadgetMetadata, GadgetMetadataLegend, GadgetAcquisitionDetails } from "./GadgetMetadata";
import { acquisitionDescription, gadgetEffectKinds, unsupportedGadgetGuidance, unsupportedGadgetsLast } from "./gadgetPresentation";
import type { Gadget, GadgetRulesCatalog } from "../../shared/types";

afterEach(cleanup);
const gadget: Gadget = { id: "kit", name: "4th Stage Charge Kit", slotCost: 3, imagePath: null, description: null };
const points = { speed: 0, acceleration: 0, handling: 0, power: 0, boost: 0 };
const rules = (kinds: ("PASSIVE" | "CONDITIONAL" | "NON_STAT" | "UNSUPPORTED")[]): GadgetRulesCatalog => ({
  supportedVersion: "1.4.1", ruleset: "reviewed", note: "", gadgets: [{ gadgetId: gadget.id,
    effects: kinds.map((kind, i) => ({ effectId: `${i}`, kind, label: "", subject: "ANY", requiredType: null,
      matching: points, nonMatching: points, explanation: "", sources: [], stackingGroup: null })) }],
});

it.each([
  ["PASSIVE", "Passive stats"], ["CONDITIONAL", "Race condition"], ["NON_STAT", "Utility"], ["UNSUPPORTED", "Effect unsupported"],
] as const)("presents %s from reviewed metadata", (kind, label) => {
  render(<GadgetMetadata gadget={gadget} rules={rules([kind])} />);
  expect(screen.getByText(label)).toBeTruthy();
  expect(screen.queryByText("Scenario modeled")).toBeNull();
});

it("deduplicates mixed kinds and shows Scenario modeled only for a real reviewed control", () => {
  const mixed = rules(["PASSIVE", "CONDITIONAL", "PASSIVE"]);
  const { rerender } = render(<GadgetMetadata gadget={gadget} rules={mixed} />);
  expect(screen.getAllByText("Passive stats")).toHaveLength(1); expect(screen.getByText("Race condition")).toBeTruthy();
  expect(screen.queryByText("Scenario modeled")).toBeNull();
  rerender(<GadgetMetadata gadget={gadget} rules={mixed} scenarios={{ supportedVersion: "1.4.1",
    controls: [{ gadgetId: "other", field: "LAP", statEffect: true }] }} />);
  expect(screen.queryByText("Scenario modeled")).toBeNull();
  rerender(<GadgetMetadata gadget={gadget} rules={mixed} scenarios={{ supportedVersion: "1.4.1",
    controls: [{ gadgetId: "kit", field: "LAP", statEffect: true }] }} />);
  expect(screen.getByText("Scenario modeled")).toBeTruthy();
  expect(gadgetEffectKinds("missing")).toEqual([]);
});

it("shows acquisition history and event tooltip without claiming permanent exclusivity", () => {
  const festival: Gadget = { ...gadget, acquisitionKind: "FESTIVAL_REWARD", acquisitionLabel: "Samba de Amigo Festival" };
  render(<><GadgetMetadata gadget={festival} /><GadgetAcquisitionDetails gadget={festival} /><GadgetMetadataLegend /></>);
  expect(screen.getByTitle("Originally awarded during the Samba de Amigo Festival.")).toBeTruthy();
  fireEvent.click(screen.getByText("Acquisition details"));
  expect(screen.getByText("This is acquisition history; other unlock methods may also exist.")).toBeTruthy();
  fireEvent.click(screen.getByText("Gadget badge guide"));
  expect(screen.getByText("Counted by recommendations when supported.")).toBeTruthy();
  expect(document.body.textContent).not.toMatch(/festival-only|exclusive/i);
  expect(acquisitionDescription({ ...festival, acquisitionLabel: null })).toContain("festival reward");
});

it("places reviewed effects before acquisition and unsupported uncertainty last", () => {
  const festival: Gadget = { ...gadget, acquisitionKind: "FESTIVAL_REWARD" };
  const { container, rerender } = render(<GadgetMetadata gadget={festival}
    rules={rules(["UNSUPPORTED", "NON_STAT", "CONDITIONAL", "PASSIVE"])}
    scenarios={{ supportedVersion: "1.4.1", controls: [{ gadgetId: "kit", field: "LAP", statEffect: true }] }} />);
  const labels = () => Array.from(container.querySelectorAll(".gadget-effect-badge")).map(chip => chip.textContent);
  expect(labels()).toEqual(["Passive stats", "Race condition", "Scenario modeled", "Utility", "Festival reward", "Effect unsupported"]);
  rerender(<GadgetMetadata gadget={festival} rules={rules(["UNSUPPORTED"])} />);
  expect(labels()).toEqual(["Festival reward", "Effect unsupported"]);
  rerender(<GadgetMetadata gadget={festival} rules={rules(["CONDITIONAL"])}
    scenarios={{ supportedVersion: "1.4.1", controls: [{ gadgetId: "kit", field: "LAP", statEffect: true }] }} />);
  expect(labels()).toEqual(["Race condition", "Scenario modeled", "Festival reward"]);
});

it.each(["STANDARD_UNLOCK", "UNKNOWN"] as const)("keeps %s acquisition out of the card badges", kind => {
  const item = { ...gadget, acquisitionKind: kind };
  render(<><GadgetMetadata gadget={item} /><GadgetAcquisitionDetails gadget={item} /></>);
  expect(screen.queryByText("Festival reward")).toBeNull();
  expect(screen.queryByText("Unknown", { exact: true })).toBeNull();
  expect(screen.getByText(acquisitionDescription(item))).toBeTruthy();
  expect(acquisitionDescription(item)).toBe(kind === "UNKNOWN" ? "Acquisition has not been reviewed." : "Standard in-game unlock.");
  expect(acquisitionDescription({ ...gadget, acquisitionKind: "STANDARD_UNLOCK", acquisitionLabel: "Race reward" })).toBe("Race reward");
});

it("names every unsupported gadget and responds to scope, locks, and selection changes", () => {
  const catalog = rules(["UNSUPPORTED"]);
  const guide = (scope: "KEEP_CURRENT" | "OPTIMIZE_UNLOCKED", locked: string[] = [], selected = ["kit"]) =>
    unsupportedGadgetGuidance(selected, locked, scope, [gadget], catalog);
  expect(guide("KEEP_CURRENT").blocker).toBe("4th Stage Charge Kit has an effect RingLab cannot evaluate yet. Remove it or choose Optimize unlocked gadgets.");
  expect(guide("OPTIMIZE_UNLOCKED").blocker).toBe("");
  expect(guide("OPTIMIZE_UNLOCKED").notice).toContain("optimizer may remove");
  expect(guide("OPTIMIZE_UNLOCKED", ["kit"]).blocker).toContain("Unlock or remove");
  expect(guide("OPTIMIZE_UNLOCKED", ["kit"]).notice).toBe("");
  expect(guide("KEEP_CURRENT", [], []).blocker).toBe("");
  const more = { ...catalog, gadgets: [...catalog.gadgets, { ...catalog.gadgets[0], gadgetId: "another" }] };
  expect(unsupportedGadgetGuidance(["kit", "another"], [], "KEEP_CURRENT", [gadget,
    { ...gadget, id: "another", name: "Perfect Charge Kit" }], more).blocker)
    .toContain("4th Stage Charge Kit, Perfect Charge Kit have effects");
  expect(unsupportedGadgetGuidance(["another"], [], "KEEP_CURRENT", [], more).blocker).toContain("Unknown gadget (another)");
});

it("leaves eligibility to the backend while rule metadata is unavailable", () => {
  expect(unsupportedGadgetGuidance(["kit"], [], "KEEP_CURRENT", [gadget]).blocker).toBe("");
});

it("moves unsupported and mixed-effect gadgets last without mutating catalog order", () => {
  const mixed = { ...gadget, id: "mixed" };
  const reviewed = { ...gadget, id: "reviewed" };
  const missing = { ...gadget, id: "missing" };
  const catalog = [gadget, reviewed, mixed, missing];
  const metadata: GadgetRulesCatalog = { ...rules(["UNSUPPORTED"]), gadgets: [
    ...rules(["UNSUPPORTED"]).gadgets,
    { ...rules(["PASSIVE", "UNSUPPORTED"]).gadgets[0], gadgetId: mixed.id },
    { ...rules(["PASSIVE"]).gadgets[0], gadgetId: reviewed.id },
  ] };
  expect(unsupportedGadgetsLast(catalog, metadata)).toEqual([reviewed, missing, gadget, mixed]);
  expect(catalog).toEqual([gadget, reviewed, mixed, missing]);
  expect(unsupportedGadgetsLast(catalog)).toEqual(catalog);
});
