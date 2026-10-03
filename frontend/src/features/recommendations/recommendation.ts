import type { BaseStats, BuildDraft, Gadget, MachinePart, Racer, RacingType } from "../../shared/types";
import { gadgetPlateStatus, machineSetupError } from "../builds/buildForm";

export const desktopRecommendationQuery = "(min-width: 1001px)";
export const defaultPriorities: RacingType[] = ["ACCELERATION", "SPEED", "HANDLING", "BOOST", "POWER"];
export const componentKeys = ["racerId", "frontPartId", "rearPartId", "tirePartId"] as const;
export type ComponentKey = typeof componentKeys[number];
export const recommendationSlots: { key: ComponentKey; label: string }[] = [
  { key: "racerId", label: "Racer" }, { key: "frontPartId", label: "Front" },
  { key: "rearPartId", label: "Rear" }, { key: "tirePartId", label: "Tire" },
];
export interface RecommendationSelection {
  racerId: string | null; frontPartId: string | null; rearPartId: string | null; tirePartId: string | null;
  gadgetIds: string[];
}
export const emptyLocks = (): RecommendationSelection => ({ racerId: null, frontPartId: null, rearPartId: null, tirePartId: null, gadgetIds: [] });
export interface RecommendationRequest {
  gadgetScope?: "KEEP_CURRENT" | "OPTIMIZE_UNLOCKED";
  gameVersionId: string; machineType: RacingType; priorities: RacingType[];
  current: RecommendationSelection; locked: RecommendationSelection;
  mode?: "STRICT" | "BALANCED";
  balanced?: { maximumLossPercent: Partial<Record<RacingType, number>>; secondary: RacingType[] };
}
export interface RecommendationResult {
  outcome: "ESTABLISHED" | "BEST_FOUND" | "NO_LEGAL_COMPLETION" | "NO_FEASIBLE_CANDIDATE" | "UNAVAILABLE" | "LIMIT_WITHOUT_CANDIDATE";
  selection: RecommendationSelection | null; currentStats: BaseStats | null; recommendedStats: BaseStats | null;
  alreadyBest: boolean; reason: string; restrictions: string[]; ruleset: string; note: string; work: number; elapsedMillis: number;
  balanced?: { minimum: Partial<Record<RacingType, number>>; secondaryTieBreakDecided: boolean } | null;
}
export interface RecommendationCatalog { racers: Racer[]; parts: MachinePart[]; gadgets: Gadget[] }

export function draftSelection(draft: BuildDraft): RecommendationSelection {
  return { racerId: draft.racerId || null, frontPartId: draft.frontPartId || null,
    rearPartId: draft.rearPartId || null, tirePartId: draft.tirePartId || null, gadgetIds: [...draft.gadgetIds] };
}

export function recommendationIdentity(draft: BuildDraft, locked: RecommendationSelection): string {
  return JSON.stringify([draft.gameVersionId, draft.machineType, draftSelection(draft), locked]);
}

export function validPriorities(priorities: RacingType[]): boolean {
  return priorities.length === 5 && new Set(priorities).size === 5 && priorities.every(value => defaultPriorities.includes(value));
}

export function validBalanced(priorities: RacingType[], secondary: RacingType[], losses: Record<RacingType, string>): boolean {
  return priorities.length > 0 && validPriorities([...priorities, ...secondary])
    && priorities.every(stat => losses[stat].trim() !== "" && Number.isFinite(Number(losses[stat]))
      && Number(losses[stat]) >= 0 && Number(losses[stat]) <= 100);
}

/** Display only: the server evaluates exact floors and the complete objective. */
export function signedStatChange(before: number | null, after: number | null): string {
  if (before === null || after === null) return "Unavailable";
  const change = before > 0 ? (after - before) / before * 100 : after - before;
  if (change !== 0 && Math.abs(change) < .0001) return `${change > 0 ? "+" : "−"}<0.0001${before > 0 ? "%" : " points"}`;
  return `${change > 0 ? "+" : ""}${Number(change.toFixed(4))}${before > 0 ? "%" : " points"}`;
}

export function movePriority(priorities: RacingType[], index: number, direction: -1 | 1): RacingType[] {
  const next = index + direction;
  if (index < 0 || index >= priorities.length || next < 0 || next >= priorities.length) return priorities;
  const result = [...priorities];
  [result[index], result[next]] = [result[next], result[index]];
  return result;
}

export function movePriorityTo(priorities: RacingType[], source: RacingType, target: RacingType): RacingType[] {
  const from = priorities.indexOf(source), to = priorities.indexOf(target);
  if (from < 0 || to < 0 || from === to) return priorities;
  const result = [...priorities]; result.splice(from, 1); result.splice(to, 0, source);
  return result;
}

export function lockTypeConflict(type: RacingType | null, locked: RecommendationSelection, parts: MachinePart[]): string {
  if (!type) return "Choose a machine type.";
  for (const key of ["frontPartId", "rearPartId", "tirePartId"] as const) {
    const id = locked[key];
    if (!id) continue;
    const slot = key === "frontPartId" ? "Front" : key === "rearPartId" ? "Rear" : "Tire";
    if (type === "BOOST" && key === "tirePartId") return "Unlock the Tire before choosing Boost: Boost has no tire part.";
    const part = parts.find(part => part.id === id);
    if (part?.racingType !== type) return `Locked ${slot} requires ${part?.racingType?.toLowerCase() ?? "a known"} machine type. Unlock it to change type.`;
  }
  return "";
}

export function groupLockState(draft: BuildDraft, locked: RecommendationSelection, parts: MachinePart[]): boolean | "mixed" {
  const keys = draft.machineType === "BOOST" ? ["frontPartId", "rearPartId"] as const
    : ["frontPartId", "rearPartId", "tirePartId"] as const;
  const count = keys.filter(key => !!locked[key]).length;
  return count === 0 ? false : count === keys.length && !machineSetupError(draft, parts) ? true : "mixed";
}

export function toggleMachineLocks(draft: BuildDraft, locked: RecommendationSelection, parts: MachinePart[]): RecommendationSelection {
  if (machineSetupError(draft, parts)) return locked;
  const unlock = groupLockState(draft, locked, parts) === true;
  return { ...locked, frontPartId: unlock ? null : draft.frontPartId, rearPartId: unlock ? null : draft.rearPartId,
    tirePartId: unlock || draft.machineType === "BOOST" ? null : draft.tirePartId };
}

export function preservesLocks(selection: RecommendationSelection, locked: RecommendationSelection): boolean {
  return componentKeys.every(key => !locked[key] || selection[key] === locked[key])
    && locked.gadgetIds.every(id => selection.gadgetIds.includes(id));
}

/** Merge only calculated fields; all author content, maps, patch and provenance come from the live draft. */
export function applyRecommendation(draft: BuildDraft, locked: RecommendationSelection, snapshotIdentity: string,
  request: RecommendationRequest, result: RecommendationResult, catalog: RecommendationCatalog): BuildDraft {
  if (recommendationIdentity(draft, locked) !== snapshotIdentity)
    throw new Error("This proposal is stale. Return to priorities and calculate again.");
  const selected = result.selection;
  if (!selected || !["ESTABLISHED", "BEST_FOUND"].includes(result.outcome) || !preservesLocks(selected, locked))
    throw new Error("The proposal does not preserve your locked selections. Calculate again.");
  const next = { ...draft, machineType: request.machineType, racerId: selected.racerId ?? "",
    frontPartId: selected.frontPartId ?? "", rearPartId: selected.rearPartId ?? "",
    tirePartId: selected.tirePartId, gadgetIds: [...selected.gadgetIds] };
  if (!catalog.racers.some(racer => racer.id === next.racerId) || machineSetupError(next, catalog.parts)
      || !gadgetPlateStatus(next.gadgetIds.map(id => catalog.gadgets.find(gadget => gadget.id === id))).valid)
    throw new Error("The proposal no longer matches the available catalog. Calculate again.");
  return next;
}

export function stockSourceForDraft(draft: BuildDraft, parts: MachinePart[]): string {
  if (machineSetupError(draft, parts)) return "";
  const ids = [draft.frontPartId, draft.rearPartId, ...(draft.machineType === "BOOST" ? [] : [draft.tirePartId])];
  const sources = ids.map(id => parts.find(part => part.id === id)!.sourceMachineId);
  return sources.every(source => source === sources[0]) ? sources[0] : "";
}

export function recommendationStatValue(stats: BaseStats | null | undefined, priority: RacingType): number | null {
  return stats?.[priority.toLowerCase() as keyof BaseStats] ?? null;
}
