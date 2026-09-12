import { describe, expect, it } from "vitest";
import { applyStockMachine, filterGadgets, gadgetCostSummary, moveGadget, toggleGadget } from "./buildForm";
import type { BuildDraft, Gadget, MachinePart } from "./types";
describe("ordered gadget selection", () => {
  it("preserves selection order when adding and removing gadgets", () => {
    expect(toggleGadget(["b", "a"], "c")).toEqual(["b", "a", "c"]);
    expect(toggleGadget(["b", "a", "c"], "a")).toEqual(["b", "c"]);
  });
  it("moves a gadget without mutating the saved draft or crossing boundaries", () => {
    const ids = ["b", "a", "c"];
    expect(moveGadget(ids, 1, -1)).toEqual(["a", "b", "c"]);
    expect(moveGadget(ids, 1, 1)).toEqual(["b", "c", "a"]);
    expect(moveGadget(ids, 0, -1)).toEqual(ids);
    expect(moveGadget(ids, 2, 1)).toEqual(ids);
    expect(ids).toEqual(["b", "a", "c"]);
  });
});

const gadget = (id: string, name: string, slotCost: number | null): Gadget =>
  ({ id, name, slotCost, description: null, imagePath: null });

it("filters available gadgets without changing selected gadget IDs", () => {
  const selected = ["a", "b"];
  expect(filterGadgets([gadget("a", "Ring Engine", 1), gadget("b", "Quick Starter", 2)], "RING"))
    .toEqual([gadget("a", "Ring Engine", 1)]);
  expect(selected).toEqual(["a", "b"]);
});

it("summarizes known costs and reports unknown costs without treating them as zero", () => {
  expect(gadgetCostSummary([gadget("a", "One", 1), gadget("b", "Two", 2)]))
    .toBe("Current catalog cost: 3 slots");
  expect(gadgetCostSummary([gadget("a", "One", 1), gadget("b", "Unknown", null)]))
    .toBe("Known cost: 1 slot · 1 gadget with unknown cost");
});

it("applies all stock parts while preserving other fields and permits a later individual change", () => {
  const parts: MachinePart[] = (["FRONT", "REAR", "TIRE"] as const).map((type) => ({
    id: `stock-${type}`, type, sourceMachineId: "stock", sourceMachineName: "Stock",
    sourceMachineImagePath: null, racingType: "SPEED",
  }));
  const draft: BuildDraft = { title: "Keep", description: "Keep", racerId: "racer",
    frontPartId: "old-front", rearPartId: "old-rear", tirePartId: "old-tire",
    gameVersionId: "version", gadgetIds: ["gadget"] };
  const stocked = applyStockMachine(draft, parts, "stock");
  expect(stocked).toEqual({ ...draft, frontPartId: "stock-FRONT", rearPartId: "stock-REAR", tirePartId: "stock-TIRE" });
  expect({ ...stocked, rearPartId: "custom-rear" }).toMatchObject({ frontPartId: "stock-FRONT", rearPartId: "custom-rear", tirePartId: "stock-TIRE" });
});
