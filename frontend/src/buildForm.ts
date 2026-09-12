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

export interface GadgetPlateStatus {
  valid: boolean;
  totalCost: number;
  summary: string;
}

export function gadgetPlateStatus(gadgets: Array<Gadget | undefined>): GadgetPlateStatus {
  const resolvedGadgets = gadgets.filter((gadget): gadget is Gadget => gadget !== undefined);
  const totalCost = resolvedGadgets.reduce(
    (total, gadget) => total + (gadget.slotCost ?? 0),
    0,
  );
  if (resolvedGadgets.length !== gadgets.length) {
    return { valid: false, totalCost, summary: "Gadget Plate · unknown gadget" };
  }
  if (new Set(resolvedGadgets.map((gadget) => gadget.id)).size !== resolvedGadgets.length) {
    return { valid: false, totalCost, summary: "Gadget Plate · duplicate gadget" };
  }
  const unknownCost = resolvedGadgets.find((gadget) => gadget.slotCost === null);
  if (unknownCost) {
    return {
      valid: false,
      totalCost,
      summary: `Gadget Plate · cost unknown: ${unknownCost.name}`,
    };
  }
  const invalidCost = resolvedGadgets.find(
    (gadget) => gadget.slotCost! < 1 || gadget.slotCost! > 3,
  );
  if (invalidCost) {
    return {
      valid: false,
      totalCost,
      summary: `Gadget Plate · invalid cost: ${invalidCost.name}`,
    };
  }

  const costs = resolvedGadgets
    .map((gadget) => gadget.slotCost as number)
    .sort((left, right) => right - left);
  const valid = canFitGadget(costs, 0, 0, 0);
  return {
    valid,
    totalCost,
    summary: valid
      ? `Gadget Plate · valid · ${totalCost} / 6 slots`
      : "Gadget Plate · combination does not fit",
  };
}

function canFitGadget(
  costs: number[],
  index: number,
  firstRow: number,
  secondRow: number,
): boolean {
  if (index === costs.length) return true;
  const cost = costs[index];
  return (
    (firstRow + cost <= 3 && canFitGadget(costs, index + 1, firstRow + cost, secondRow))
    || (secondRow + cost <= 3 && canFitGadget(costs, index + 1, firstRow, secondRow + cost))
  );
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
