import { useState } from "react";
import { cleanup, fireEvent, render, screen, within, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import type { BuildDraft, Gadget, GadgetRulesCatalog } from "../../../shared/types";
import { GadgetSelectionSection } from "./GadgetSelectionSection";
import type { RecommendationSelection } from "../../recommendations/recommendation";

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
const collection = vi.hoisted(() => ({ status: "anonymous", busy: false,
  data: { racers: [] as string[], machines: [] as string[], gadgets: [] as string[] } }));
vi.mock("../../collection/Collection", async original => ({
  ...await original<typeof import("../../collection/Collection")>(), useCollection: () => collection,
}));
afterEach(() => { collection.status = "anonymous"; collection.data.gadgets = []; });
const gadgets: Gadget[] = [
  { id: "a", name: "Alpha", slotCost: 1, imagePath: null, description: null, acquisitionKind: "STANDARD_UNLOCK" },
  { id: "b", name: "Beta", slotCost: 2, imagePath: null, description: null, acquisitionKind: "FESTIVAL_REWARD", acquisitionLabel: "Test Festival" },
  { id: "c", name: "Gamma", slotCost: 3, imagePath: null, description: null, acquisitionKind: "UNKNOWN" },
];
const draft: BuildDraft = { title: "Keep draft", description: "Keep note", racerId: "r", machineType: "SPEED",
  frontPartId: "front", rearPartId: "rear", tirePartId: "tire", gameVersionId: "v", remixedFromBuildId: null,
  gadgetIds: ["c", "a"], recommendedMapIds: [], mapRecommendationMode: "ALL" };

function Harness({ context = "draft-a", initial = draft.gadgetIds }: { context?: string; initial?: string[] }) {
  const [ids, setIds] = useState(initial);
  const [locks, setLocks] = useState<RecommendationSelection>({ racerId: null, frontPartId: null, rearPartId: null, tirePartId: null, gadgetIds: ["c"] });
  return <><GadgetSelectionSection draft={{ ...draft, gadgetIds: ids }} gadgets={gadgets}
    gadgetTypeSelection={{ racerType: null, machineType: null }} locks={locks} setLocks={setLocks}
    onChange={setIds} draftContext={context} />
    <output aria-label="Draft and locks">{JSON.stringify({ ...draft, gadgetIds: ids, locks })}</output></>;
}

it("orders slot, type, effects and acquisition together while keeping stat adjustments separate", () => {
  const kit: Gadget = { id: "kit", name: "Acceleration Character Kit", slotCost: 3, imagePath: null,
    description: null, acquisitionKind: "FESTIVAL_REWARD", acquisitionLabel: "PAC-MAN Festival" };
  const points = { speed: 0, acceleration: 7, handling: 0, power: 0, boost: -5 };
  const rules: GadgetRulesCatalog = { supportedVersion: "1.4.1", ruleset: "reviewed", note: "",
    gadgets: [{ gadgetId: kit.id, effects: (["PASSIVE", "CONDITIONAL"] as const).map(kind => ({
      effectId: kind, kind, label: "", subject: "RACER", requiredType: "ACCELERATION",
      matching: points, nonMatching: points, explanation: "", sources: [], stackingGroup: null,
    })) }] };
  render(<GadgetSelectionSection draft={{ ...draft, gadgetIds: [] }} gadgets={[kit]} gadgetRules={rules}
    gadgetTypeSelection={{ racerType: "ACCELERATION", machineType: null }}
    locks={{ racerId: null, frontPartId: null, rearPartId: null, tirePartId: null, gadgetIds: [] }}
    setLocks={vi.fn()} onChange={vi.fn()} draftContext="metadata" />);
  const card = screen.getByRole("checkbox", { name: /Acceleration Character Kit/ }).closest(".gadget-option")!;
  const row = card.querySelector(".gadget-option-meta")!;
  expect(Array.from(row.querySelectorAll(".gadget-slot-badge, .gadget-type-badge, .gadget-effect-badge"))
    .map(chip => chip.textContent)).toEqual([
      "3 slots", "Acceleration racer", "Passive stats", "Race condition", "Festival reward",
    ]);
  expect(row.querySelector(".gadget-adjustment")).toBeNull();
  expect(within(card as HTMLElement).getByText("Acceleration +7")).toBeTruthy();
});

it("shows an accessible ownership chip first, keeps names clean and retains grayscale only for known unowned items", () => {
  collection.status = "ready";
  collection.data.gadgets = ["a"];
  const { rerender } = render(<Harness initial={[]} />);
  const card = screen.getByRole("checkbox", { name: /Alpha/ }).closest(".gadget-option")!;
  expect(card.querySelector("strong")?.textContent).toBe("Alpha");
  expect(card.querySelector(".gadget-option-meta")?.firstElementChild?.textContent).toBe("Not owned");
  expect(within(card as HTMLElement).getByRole("img", { name: "Warning" })).toBeTruthy();
  expect(card.querySelector(".not-owned-artwork")).toBeTruthy();
  collection.data.gadgets = [];
  rerender(<Harness initial={[]} />);
  expect(screen.queryByText("Not owned")).toBeNull();
  expect(card.querySelector(".not-owned-artwork")).toBeNull();
  collection.status = "anonymous";
  collection.data.gadgets = ["a"];
  rerender(<Harness initial={[]} />);
  expect(screen.queryByText("Not owned")).toBeNull();
  expect(card.querySelector(".not-owned-artwork")).toBeNull();
});

it("keeps selected gadgets ordered and unique while search and filters restrict available gadgets", () => {
  render(<Harness />);
  const selected = within(screen.getByRole("region", { name: "Selected gadgets" }));
  const available = within(screen.getByRole("region", { name: "Available gadgets" }));
  const before = screen.getByLabelText("Draft and locks").textContent;
  expect(selected.getAllByRole("checkbox").map(input => input.closest("label")?.textContent)).toEqual([
    expect.stringContaining("Gamma"), expect.stringContaining("Alpha"),
  ]);
  expect(available.getAllByRole("checkbox")).toHaveLength(1);
  fireEvent.click(screen.getByRole("checkbox", { name: "1 slot" }));
  expect(available.queryAllByRole("checkbox")).toHaveLength(0);
  expect(screen.getByText(/Showing 2 of 3 gadgets/)).toBeTruthy();
  fireEvent.change(screen.getByRole("searchbox"), { target: { value: "BETA" } });
  fireEvent.click(screen.getByRole("checkbox", { name: "2 slots" }));
  expect(available.getByRole("checkbox", { name: /Beta/ })).toBeTruthy();
  expect(selected.getAllByRole("checkbox")).toHaveLength(2);
  expect(screen.getByRole("button", { name: "Unlock Gamma" }).getAttribute("aria-pressed")).toBe("true");
  expect(screen.getByLabelText("Draft and locks").textContent).toBe(before);
});

it("puts unsupported available gadgets last while preserving selected build order and filtering", () => {
  const points = { speed: 0, acceleration: 0, handling: 0, power: 0, boost: 0 };
  const rules: GadgetRulesCatalog = { supportedVersion: "1.4.1", ruleset: "reviewed", note: "",
    gadgets: [{ gadgetId: "a", effects: [{ effectId: "unsupported", kind: "UNSUPPORTED", label: "",
      subject: "ANY", requiredType: null, matching: points, nonMatching: points, explanation: "",
      sources: [], stackingGroup: null }] }] };
  const props = { gadgets, gadgetRules: rules, gadgetTypeSelection: { racerType: null, machineType: null },
    locks: { racerId: null, frontPartId: null, rearPartId: null, tirePartId: null, gadgetIds: [] },
    setLocks: vi.fn(), onChange: vi.fn(), draftContext: "ordering" };
  const { rerender } = render(<GadgetSelectionSection {...props} draft={{ ...draft, gadgetIds: [] }} />);
  const names = (region: string) => within(screen.getByRole("region", { name: region })).getAllByRole("checkbox")
    .map(input => input.closest("label")?.querySelector("strong")?.textContent);
  expect(names("Available gadgets")).toEqual(["Beta", "Gamma", "Alpha"]);
  fireEvent.click(screen.getByRole("checkbox", { name: "Effect unsupported" }));
  expect(names("Available gadgets")).toEqual(["Alpha"]);
  rerender(<GadgetSelectionSection {...props} draft={{ ...draft, gadgetIds: ["a", "b"] }} />);
  expect(names("Selected gadgets")).toEqual(["Alpha", "Beta"]);
});

it("clears all structured groups while retaining text search and the selected setup", () => {
  render(<Harness />);
  expect((screen.getByRole("button", { name: "Clear filters" }) as HTMLButtonElement).disabled).toBe(true);
  for (const type of ["Speed", "Acceleration", "Handling", "Power", "Boost"]) {
    expect(screen.getByRole("checkbox", { name: type }).closest("label")?.classList.contains(`racing-type-${type.toLowerCase()}`)).toBe(true);
  }
  fireEvent.change(screen.getByRole("searchbox"), { target: { value: "Beta" } });
  for (const name of ["1 slot", "Passive stats", "Speed", "Standard unlock"]) {
    fireEvent.click(screen.getByRole("checkbox", { name }));
  }
  expect(screen.getByText("Filters · 4 active")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Clear filters" }));
  expect((screen.getByRole("searchbox") as HTMLInputElement).value).toBe("Beta");
  expect(screen.getByRole("button", { name: "Any" }).getAttribute("aria-pressed")).toBe("true");
  expect(within(screen.getByRole("region", { name: "Available gadgets" })).getByRole("checkbox", { name: /Beta/ })).toBeTruthy();
  expect(within(screen.getByRole("region", { name: "Selected gadgets" })).getAllByRole("checkbox")).toHaveLength(2);
});

it("moves a newly checked result into Selected and resets only presentation state for another draft", () => {
  const { rerender } = render(<Harness />);
  fireEvent.click(within(screen.getByRole("region", { name: "Available gadgets" })).getByRole("checkbox", { name: /Beta/ }));
  expect(within(screen.getByRole("region", { name: "Selected gadgets" })).getAllByRole("checkbox")).toHaveLength(3);
  expect(within(screen.getByRole("region", { name: "Available gadgets" })).queryAllByRole("checkbox")).toHaveLength(0);
  fireEvent.click(screen.getByRole("checkbox", { name: "2 slots" }));
  fireEvent.change(screen.getByRole("searchbox"), { target: { value: "Beta" } });
  rerender(<Harness context="draft-b" />);
  expect((screen.getByRole("searchbox") as HTMLInputElement).value).toBe("");
  expect((screen.getByRole("checkbox", { name: "2 slots" }) as HTMLInputElement).checked).toBe(false);
});

it("starts mobile filters collapsed and exposes keyboard-accessible grouped controls", async () => {
  vi.stubGlobal("matchMedia", () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
  render(<Harness initial={[]} />);
  const summary = screen.getByText("Filters");
  const details = summary.closest("details")!;
  expect(details.open).toBe(false);
  const user = userEvent.setup();
  await user.click(summary);
  await waitFor(() => expect(details.open).toBe(true));
  for (const name of ["Slot cost", "Effect", "Applies to", "Acquisition"]) {
    expect(screen.getByRole("group", { name })).toBeTruthy();
  }
  await user.tab();
  expect(document.activeElement).toBe(screen.getByRole("checkbox", { name: "1 slot" }));
  await user.keyboard(" ");
  expect((screen.getByRole("checkbox", { name: "1 slot" }) as HTMLInputElement).checked).toBe(true);
  await user.keyboard(" ");
  await user.click(screen.getByRole("checkbox", { name: "2 slots" }));
  await user.click(screen.getByRole("checkbox", { name: "Festival reward" }));
  expect(screen.getByText("Filters · 2 active")).toBeTruthy();
  expect(within(screen.getByRole("region", { name: "Available gadgets" })).getAllByRole("checkbox")).toHaveLength(1);
  await user.click(summary);
  await waitFor(() => expect(details.open).toBe(false));
});
