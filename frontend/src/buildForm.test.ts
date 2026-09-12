import { describe, expect, it } from "vitest";
import { applyStockMachine, filterGadgets, gadgetPlateStatus, moveGadget, toggleGadget } from "./buildForm";
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

it("validates Gadget Plate partitions independently of display order", () => {
  for (const costs of [[], [1], [3], [1, 1, 1], [2, 1], [3, 3], [3, 2, 1],
    [3, 1, 1, 1], [2, 2, 1, 1], [1, 1, 1, 1, 1, 1]]) {
    expect(gadgetPlateStatus(costs.map((cost, index) => gadget(`${index}`, `${index}`, cost))).valid)
      .toBe(true);
  }
  expect(gadgetPlateStatus([gadget("a", "A", 1), gadget("b", "B", 2),
    gadget("c", "C", 2), gadget("d", "D", 1)]).valid).toBe(true);
  expect(gadgetPlateStatus([gadget("c", "C", 2), gadget("a", "A", 1),
    gadget("d", "D", 1), gadget("b", "B", 2)]).valid).toBe(true);
});

it("rejects totals that cannot be partitioned into two three-slot rows", () => {
  for (const costs of [[2, 2, 2], [3, 3, 1], [3, 2, 2],
    [2, 2, 1, 1, 1], [1, 1, 1, 1, 1, 1, 1]]) {
    expect(gadgetPlateStatus(costs.map((cost, index) => gadget(`${index}`, `${index}`, cost))).valid)
      .toBe(false);
  }
});

it("rejects duplicate, unknown, missing, and out-of-range gadget data", () => {
  const duplicate = gadget("a", "Duplicate", 1);
  expect(gadgetPlateStatus([duplicate, duplicate]).summary).toBe("Gadget Plate · duplicate gadget");
  expect(gadgetPlateStatus([gadget("a", "Unknown", null)]).summary)
    .toBe("Gadget Plate · cost unknown: Unknown");
  expect(gadgetPlateStatus([undefined]).summary).toBe("Gadget Plate · unknown gadget");
  expect(gadgetPlateStatus([gadget("a", "Invalid", 4)]).summary)
    .toBe("Gadget Plate · invalid cost: Invalid");
  expect(gadgetPlateStatus([gadget("a", "One", 1), gadget("b", "Two", 2)]).summary)
    .toBe("Gadget Plate · valid · 3 / 6 slots");
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
