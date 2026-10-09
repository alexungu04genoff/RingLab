import { expect, it } from "vitest";
import { applyRecommendation, defaultPriorities, draftSelection, emptyLocks, groupLockState, lockTypeConflict,
  movePriority, movePriorityTo, preservesLocks, recommendationIdentity, stockSourceForDraft, toggleMachineLocks, validPriorities } from "./recommendation";
import type { RecommendationCatalog, RecommendationRequest, RecommendationResult } from "./recommendation";
import type { BuildDraft, MachinePart } from "../../shared/types";

const part = (id: string, type: MachinePart["type"], source = "stock", racingType: MachinePart["racingType"] = "SPEED"): MachinePart =>
  ({ id, type, sourceMachineId: source, sourceMachineName: source, sourceMachineImagePath: null, racingType });
const catalog: RecommendationCatalog = { racers: [{ id: "r", name: "Racer", racingType: "BOOST", imagePath: null }],
  parts: [part("f", "FRONT"), part("f2", "FRONT", "other"), part("b", "REAR"), part("t", "TIRE"),
    part("bf", "FRONT", "board", "BOOST"), part("br", "REAR", "board", "BOOST")],
  gadgets: ["a", "b"].map(id => ({ id, name: id, description: null, slotCost: 1, imagePath: null })) };
const draft: BuildDraft = { visibility: "PUBLIC", title: "Keep title", description: "Keep description", racerId: "r", frontPartId: "f", rearPartId: "b", tirePartId: "t",
  machineType: "SPEED", gameVersionId: "patch", remixedFromBuildId: "source", gadgetIds: ["a"], recommendedMapIds: ["map"], mapRecommendationMode: "SELECTED" };
const request: RecommendationRequest = { gameVersionId: "patch", machineType: "SPEED", priorities: defaultPriorities, current: draftSelection(draft), locked: emptyLocks() };
const result: RecommendationResult = { outcome: "ESTABLISHED", selection: { ...draftSelection(draft), frontPartId: "f2", gadgetIds: ["a", "b"] },
  currentStats: null, recommendedStats: null, alreadyBest: false, reason: "Supported", restrictions: [], ruleset: "test", note: "Test", work: 20, elapsedMillis: 1 };

it("moves priorities without loss or duplication and validates complete permutations", () => {
  expect(validPriorities(defaultPriorities)).toBe(true);
  expect(movePriority(defaultPriorities, 1, -1)).toEqual(["SPEED", "ACCELERATION", "HANDLING", "BOOST", "POWER"]);
  expect(movePriority(defaultPriorities, 0, 1)[1]).toBe("ACCELERATION");
  for (const [index, direction] of [[0, -1], [4, 1], [-1, 1], [6, -1]] as const)
    expect(movePriority(defaultPriorities, index, direction)).toBe(defaultPriorities);
  expect(validPriorities(["SPEED"])).toBe(false);
  expect(validPriorities(["SPEED", "SPEED", "HANDLING", "BOOST", "POWER"])).toBe(false);
  expect(movePriorityTo(defaultPriorities, "POWER", "ACCELERATION")).toEqual(["POWER", "ACCELERATION", "SPEED", "HANDLING", "BOOST"]);
  expect(movePriorityTo(defaultPriorities, "ACCELERATION", "POWER")).toEqual(["SPEED", "HANDLING", "BOOST", "POWER", "ACCELERATION"]);
  expect(movePriorityTo(defaultPriorities, "SPEED", "SPEED")).toBe(defaultPriorities);
  expect(movePriorityTo(defaultPriorities.slice(1), "ACCELERATION", "SPEED")).toEqual(defaultPriorities.slice(1));
  expect(movePriorityTo(defaultPriorities.slice(1), "SPEED", "ACCELERATION")).toEqual(defaultPriorities.slice(1));
});

it("individual locks and complete mixed machine group locks use one synchronized state", () => {
  const mixed = { ...draft, frontPartId: "f2" };
  let locks = emptyLocks(); expect(groupLockState(mixed, locks, catalog.parts)).toBe(false);
  locks = { ...locks, tirePartId: "t" }; expect(groupLockState(mixed, locks, catalog.parts)).toBe("mixed");
  locks = toggleMachineLocks(mixed, locks, catalog.parts);
  expect(locks.frontPartId).toBe("f2"); expect(groupLockState(mixed, locks, catalog.parts)).toBe(true);
  expect(toggleMachineLocks(mixed, locks, catalog.parts)).toEqual(emptyLocks());
  expect(toggleMachineLocks({ ...draft, rearPartId: "" }, locks, catalog.parts)).toBe(locks);
  const boost = { ...draft, machineType: "BOOST" as const, frontPartId: "bf", rearPartId: "br", tirePartId: null };
  const boostLocks = toggleMachineLocks(boost, emptyLocks(), catalog.parts);
  expect(boostLocks.tirePartId).toBeNull(); expect(groupLockState(boost, boostLocks, catalog.parts)).toBe(true);
});

it("locked tire constrains machine type while racer type remains independent", () => {
  expect(lockTypeConflict("SPEED", { ...emptyLocks(), racerId: "r" }, catalog.parts)).toBe("");
  const locks = { ...emptyLocks(), tirePartId: "t" };
  expect(lockTypeConflict("SPEED", locks, catalog.parts)).toBe("");
  expect(lockTypeConflict("POWER", locks, catalog.parts)).toContain("Tire requires speed");
  expect(lockTypeConflict("BOOST", locks, catalog.parts)).toContain("Unlock the Tire");
  expect(lockTypeConflict(null, locks, catalog.parts)).toContain("Choose");
  expect(lockTypeConflict("SPEED", { ...locks, frontPartId: "missing" }, catalog.parts)).toContain("known");
});

it("Apply changes only proposed fields and preserves all author metadata and lock state", () => {
  const locks = { ...emptyLocks(), racerId: "r", tirePartId: "t", gadgetIds: ["a"] };
  const identity = recommendationIdentity(draft, locks);
  const next = applyRecommendation({ ...draft, title: "More recent title" }, locks, identity, request, result, catalog);
  expect(next).toEqual({ ...draft, title: "More recent title", frontPartId: "f2", gadgetIds: ["a", "b"] });
  expect(locks).toEqual({ ...emptyLocks(), racerId: "r", tirePartId: "t", gadgetIds: ["a"] });
  expect(stockSourceForDraft(draft, catalog.parts)).toBe("stock"); expect(stockSourceForDraft(next, catalog.parts)).toBe("");
  expect(stockSourceForDraft({ ...draft, frontPartId: "" }, catalog.parts)).toBe("");
});

it("Apply rejects stale patch, selections, type, order or locks", () => {
  const identity = recommendationIdentity(draft, emptyLocks());
  for (const changed of [{ ...draft, gameVersionId: "new" }, { ...draft, racerId: "different" },
    { ...draft, machineType: "POWER" as const }, { ...draft, gadgetIds: ["a", "b"] }])
    expect(() => applyRecommendation(changed, emptyLocks(), identity, request, result, catalog)).toThrow("stale");
  expect(() => applyRecommendation(draft, { ...emptyLocks(), racerId: "r" }, identity, request, result, catalog)).toThrow("stale");
});

it("Apply refuses invalid result types, overwritten locks, unknown IDs and illegal plates", () => {
  const locks = { ...emptyLocks(), frontPartId: "f" }; const identity = recommendationIdentity(draft, locks);
  expect(() => applyRecommendation(draft, locks, identity, request, result, catalog)).toThrow("locked selections");
  expect(preservesLocks({ ...draftSelection(draft), gadgetIds: [] }, { ...emptyLocks(), gadgetIds: ["a"] })).toBe(false);
  for (const bad of [{ ...result, outcome: "UNAVAILABLE" as const }, { ...result, selection: null }])
    expect(() => applyRecommendation(draft, emptyLocks(), recommendationIdentity(draft, emptyLocks()), request, bad, catalog)).toThrow();
  for (const selected of [{ ...result.selection!, racerId: null }, { ...result.selection!, frontPartId: null },
    { ...result.selection!, rearPartId: null }, { ...result.selection!, gadgetIds: ["unknown"] }, { ...result.selection!, gadgetIds: ["a", "a"] }])
    expect(() => applyRecommendation(draft, emptyLocks(), recommendationIdentity(draft, emptyLocks()), request, { ...result, selection: selected }, catalog)).toThrow("catalog");
});

it("Boost apply explicitly removes an unlocked tire and reconciles the board stock preset", () => {
  const next = applyRecommendation(draft, emptyLocks(), recommendationIdentity(draft, emptyLocks()), { ...request, machineType: "BOOST" },
    { ...result, selection: { ...draftSelection(draft), frontPartId: "bf", rearPartId: "br", tirePartId: null } }, catalog);
  expect(next.tirePartId).toBeNull(); expect(stockSourceForDraft(next, catalog.parts)).toBe("board");
  expect(draftSelection({ ...draft, racerId: "", frontPartId: "", rearPartId: "", tirePartId: "" })).toMatchObject({ racerId: null, frontPartId: null, rearPartId: null, tirePartId: null });
});
