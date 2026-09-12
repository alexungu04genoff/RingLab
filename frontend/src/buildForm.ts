import type { BuildDraft, Gadget, MachinePart, MachinePartType } from "./types";

export function toggleGadget(ids: string[], id: string): string[] {
  return ids.includes(id) ? ids.filter((value) => value !== id) : [...ids, id];
}

export function filterGadgets(gadgets: Gadget[], search: string): Gadget[] {
  const query = search.trim().toLocaleLowerCase();
  return query
    ? gadgets.filter((gadget) => gadget.name.toLocaleLowerCase().includes(query))
    : gadgets;
}

export function gadgetCostSummary(gadgets: Gadget[]): string | null {
  if (gadgets.length === 0) return null;
  const knownCost = gadgets.reduce((total, gadget) => total + (gadget.slotCost ?? 0), 0);
  const unknownCount = gadgets.filter((gadget) => gadget.slotCost === null).length;
  if (unknownCount === 0) return `Current catalog cost: ${knownCost} ${knownCost === 1 ? "slot" : "slots"}`;
  return `Known cost: ${knownCost} ${knownCost === 1 ? "slot" : "slots"} · ${unknownCount} ${unknownCount === 1 ? "gadget" : "gadgets"} with unknown cost`;
}

export function stockMachineSources(parts: MachinePart[]): MachinePart[] {
  const bySource = new Map<string, MachinePart[]>();
  parts.forEach((part) => bySource.set(part.sourceMachineId, [...(bySource.get(part.sourceMachineId) ?? []), part]));
  return [...bySource.values()]
    .filter((sourceParts) => new Set(sourceParts.map((part) => part.type)).size === 3)
    .map((sourceParts) => sourceParts[0])
    .sort((a, b) => a.sourceMachineName.localeCompare(b.sourceMachineName));
}

export function applyStockMachine(draft: BuildDraft, parts: MachinePart[], sourceMachineId: string): BuildDraft {
  const partId = (type: MachinePartType) =>
    parts.find((part) => part.sourceMachineId === sourceMachineId && part.type === type)?.id;
  const frontPartId = partId("FRONT");
  const rearPartId = partId("REAR");
  const tirePartId = partId("TIRE");
  return frontPartId && rearPartId && tirePartId
    ? { ...draft, frontPartId, rearPartId, tirePartId }
    : draft;
}
export function moveGadget(
  ids: string[],
  index: number,
  direction: -1 | 1,
): string[] {
  const target = index + direction;
  if (target < 0 || target >= ids.length) return ids;
  const copy = [...ids];
  [copy[index], copy[target]] = [copy[target], copy[index]];
  return copy;
}
