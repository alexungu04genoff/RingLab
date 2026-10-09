import { useState } from "react";
import { cleanup, fireEvent, render, screen, within, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import type { BuildDraft, Gadget } from "../../../shared/types";
import { GadgetSelectionSection } from "./GadgetSelectionSection";
import type { RecommendationSelection } from "../../recommendations/recommendation";

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
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
    ownershipLabel={() => ""} onChange={setIds} draftContext={context} />
    <output aria-label="Draft and locks">{JSON.stringify({ ...draft, gadgetIds: ids, locks })}</output></>;
}

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

it("clears all structured groups while retaining text search and the selected setup", () => {
  render(<Harness />);
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
