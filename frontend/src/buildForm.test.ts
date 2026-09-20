import { describe, expect, it } from "vitest";
import { applyStockMachine, filterGadgets, gadgetPlateStatus, machinePartsForType, machineSetupError, moveGadget, stockMachineSources, switchMachineType, toggleGadget } from "./buildForm";
import { machineTypes, requiredMachineSlots } from "./machineComposition";
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

it.each(machineTypes)("%s filters all slots and presets independently of the racer", (machineType) => {
  const parts: MachinePart[] = machineTypes.flatMap((racingType) => requiredMachineSlots(racingType).map((type) => ({
    id: `${racingType}-${type}`, type, sourceMachineId: racingType, sourceMachineName: racingType,
    sourceMachineImagePath: null, racingType,
  })));
  const draft: BuildDraft = { title: "Keep", description: "Keep", racerId: "independent", machineType,
    frontPartId: "", rearPartId: "", tirePartId: null, gameVersionId: "v", gadgetIds: ["g"], remixedFromBuildId: null };
  expect(stockMachineSources(parts, machineType).map(p => p.sourceMachineId)).toEqual([machineType]);
  const selected = applyStockMachine(draft, parts, machineType);
  expect(machineSetupError(selected, parts)).toBe("");
  expect(selected.racerId).toBe("independent");
  expect(selected.tirePartId).toBe(machineType === "BOOST" ? null : `${machineType}-TIRE`);
  for (const slot of requiredMachineSlots(machineType)) {
    expect(machinePartsForType(parts, machineType, slot).map(p => p.id)).toEqual([`${machineType}-${slot}`]);
  }
  expect(applyStockMachine(draft, parts, "missing")).toBe(draft);
  expect(machineSetupError(draft, parts)).toContain("Select front");
  expect(machineSetupError({ ...selected, rearPartId: "missing" }, parts)).toContain("cannot be verified");
  const other = machineType === "POWER" ? "SPEED" : "POWER";
  expect(machineSetupError({ ...selected, rearPartId: `${other}-REAR` }, parts)).toContain("does not match");
  expect(switchMachineType(selected, machineType, parts)).toBe(selected);
  expect(switchMachineType(selected, other, parts)).toMatchObject({ racerId: "independent", title: "Keep", gadgetIds: ["g"], frontPartId: "", rearPartId: "", tirePartId: null });
  expect(machineSetupError({ ...selected, machineType: null }, parts)).toContain("Select a machine type");
  expect(stockMachineSources(parts, null)).toEqual([]);
  expect(machinePartsForType(parts, null, "FRONT")).toEqual([]);
  expect(machineSetupError({ ...selected, machineType: "BOOST", frontPartId: "BOOST-FRONT", rearPartId: "BOOST-REAR", tirePartId: "POWER-TIRE" }, parts)).toContain("cannot have tires");
});

const gadget = (id: string, name: string, slotCost: number | null): Gadget =>
  ({ id, name, slotCost, description: null, imagePath: null });

it("filters available gadgets without changing selected gadget IDs", () => {
  const selected = ["a", "b"];
  expect(filterGadgets([gadget("a", "Ring Engine", 1), gadget("b", "Quick Starter", 2)], "RING"))
    .toEqual([gadget("a", "Ring Engine", 1)]);
  expect(selected).toEqual(["a", "b"]);
});

it("places selected gadgets first in their chosen order and preserves catalog order for the rest", () => {
  const gadgets = [
    gadget("a", "Alpha", 1),
    gadget("b", "Beta", 1),
    gadget("c", "Gamma", 1),
    gadget("d", "Delta", 1),
  ];
  expect(filterGadgets(gadgets, "", ["c", "a"]).map(({ id }) => id))
    .toEqual(["c", "a", "b", "d"]);
  expect(filterGadgets(gadgets, "a", ["d", "c"]).map(({ id }) => id))
    .toEqual(["d", "c", "a", "b"]);
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
    machineType: "SPEED", gameVersionId: "version", remixedFromBuildId: null, gadgetIds: ["gadget"] };
  const stocked = applyStockMachine(draft, parts, "stock");
  expect(stocked).toEqual({ ...draft, frontPartId: "stock-FRONT", rearPartId: "stock-REAR", tirePartId: "stock-TIRE" });
  expect({ ...stocked, rearPartId: "custom-rear" }).toMatchObject({ frontPartId: "stock-FRONT", rearPartId: "custom-rear", tirePartId: "stock-TIRE" });
});

it("filters stock and individual parts by family and applies the correct family shape", () => {
  const boardParts: MachinePart[] = (["FRONT", "REAR"] as const).map((type) => ({
    id: `board-${type}`, type, sourceMachineId: "board", sourceMachineName: "Board",
    sourceMachineImagePath: null, racingType: "BOOST",
  }));
  const standardParts: MachinePart[] = (["FRONT", "REAR", "TIRE"] as const).map((type) => ({
    id: `standard-${type}`, type, sourceMachineId: "standard", sourceMachineName: "Standard",
    sourceMachineImagePath: null, racingType: "SPEED",
  }));
  const parts = [...boardParts, ...standardParts];
  const draft: BuildDraft = { title: "Keep", description: "", racerId: "racer",
    frontPartId: "front", rearPartId: "rear", tirePartId: "tire", machineType: "SPEED",
    gameVersionId: "version", remixedFromBuildId: null, gadgetIds: [] };

  expect(stockMachineSources(parts, "SPEED").map((part) => part.sourceMachineName))
    .toEqual(["Standard"]);
  expect(stockMachineSources(parts, "BOOST").map((part) => part.sourceMachineName))
    .toEqual(["Board"]);
  expect(machinePartsForType(parts, "SPEED", "FRONT").map((part) => part.id))
    .toEqual(["standard-FRONT"]);
  expect(machinePartsForType(parts, "BOOST", "REAR").map((part) => part.id))
    .toEqual(["board-REAR"]);
  expect(applyStockMachine({ ...draft, machineType: "BOOST" }, parts, "board")).toMatchObject({
    machineType: "BOOST", frontPartId: "board-FRONT", rearPartId: "board-REAR", tirePartId: null,
  });
  expect(applyStockMachine(draft, parts, "standard")).toMatchObject({
    machineType: "SPEED", frontPartId: "standard-FRONT",
    rearPartId: "standard-REAR", tirePartId: "standard-TIRE",
  });
  expect(switchMachineType(draft, "BOOST")).toMatchObject({
    machineType: "BOOST", frontPartId: "", rearPartId: "", tirePartId: null,
  });
  expect(switchMachineType({ ...draft, machineType: "BOOST", tirePartId: null }, "SPEED"))
    .toMatchObject({ machineType: "SPEED", frontPartId: "", rearPartId: "", tirePartId: null });
});
