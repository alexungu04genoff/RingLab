import type { Build, BuildDraft, Gadget, RacingType, MachinePart, MachinePartType } from "./types";
import { requiredMachineSlots } from "./machineComposition";

export function toggleGadget(ids: string[], id: string): string[] {
  return ids.includes(id) ? ids.filter((value) => value !== id) : [...ids, id];
}

export function filterGadgets(gadgets: Gadget[], search: string, selectedIds: string[] = []): Gadget[] {
  const query = search.trim().toLocaleLowerCase();
  const filtered = query
    ? gadgets.filter((gadget) => gadget.name.toLocaleLowerCase().includes(query))
    : gadgets;
  const selectedPositions = new Map(selectedIds.map((id, index) => [id, index]));
  return filtered
    .map((gadget, catalogIndex) => ({ gadget, catalogIndex }))
    .sort((left, right) => {
      const leftSelectedIndex = selectedPositions.get(left.gadget.id);
      const rightSelectedIndex = selectedPositions.get(right.gadget.id);
      if (leftSelectedIndex !== undefined && rightSelectedIndex !== undefined) {
        return leftSelectedIndex - rightSelectedIndex;
      }
      if (leftSelectedIndex !== undefined) return -1;
      if (rightSelectedIndex !== undefined) return 1;
      return left.catalogIndex - right.catalogIndex;
    })
    .map(({ gadget }) => gadget);
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

export function savedMachineSetupError(
  build: Pick<Build, "frontPart" | "rearPart" | "tirePart">,
): string {
  const machineType = build.frontPart.racingType;
  if (!machineType) return "Machine-part compatibility cannot be verified.";
  if (build.frontPart.type !== "FRONT") return "The stored front part has the wrong slot type.";
  if (build.rearPart.type !== "REAR" || build.rearPart.racingType !== machineType) {
    return `Rear parts do not match the ${machineType.toLowerCase()} front.`;
  }
  if (machineType === "BOOST") {
    return build.tirePart ? "Boost setups cannot include tires." : "";
  }
  if (!build.tirePart) return `${machineType.toLowerCase()} setups require tires.`;
  if (build.tirePart.type !== "TIRE" || build.tirePart.racingType !== machineType) {
    return `Tires do not match the ${machineType.toLowerCase()} front.`;
  }
  return "";
}

export function savedBuildSetupIssues(
  build: Pick<Build, "frontPart" | "rearPart" | "tirePart" | "gadgets">,
): string[] {
  const issues: string[] = [];
  const machineIssue = savedMachineSetupError(build);
  if (machineIssue) issues.push(machineIssue);
  const plate = gadgetPlateStatus(build.gadgets);
  if (!plate.valid) issues.push(plate.summary.replace("Gadget Plate · ", "Gadgets: "));
  return issues;
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

export function machinePartsForType(
  parts: MachinePart[], machineType: RacingType | null, type: MachinePartType,
): MachinePart[] {
  return machineType ? parts.filter((part) => part.racingType === machineType && part.type === type) : [];
}

export function stockMachineSources(parts: MachinePart[], machineType: RacingType | null): MachinePart[] {
  if (!machineType) return [];
  const bySource = new Map<string, MachinePart[]>();
  parts
    .forEach((part) => bySource.set(part.sourceMachineId, [...(bySource.get(part.sourceMachineId) ?? []), part]));
  return [...bySource.values()]
    .filter((sourceParts) => {
      const required = requiredMachineSlots(machineType);
      return sourceParts.every((part) => part.racingType === machineType)
        && sourceParts.length === required.length
        && required.every((slot) => sourceParts.filter((part) => part.type === slot).length === 1);
    })
    .map((sourceParts) => sourceParts[0])
    .sort((a, b) => a.sourceMachineName.localeCompare(b.sourceMachineName));
}

export function applyStockMachine(draft: BuildDraft, parts: MachinePart[], sourceMachineId: string): BuildDraft {
  if (!stockMachineSources(parts, draft.machineType).some((part) => part.sourceMachineId === sourceMachineId)) return draft;
  const partId = (type: MachinePartType) =>
    parts.find((part) => part.sourceMachineId === sourceMachineId && part.type === type)?.id;
  const frontPartId = partId("FRONT");
  const rearPartId = partId("REAR");
  const tirePartId = partId("TIRE");
  return frontPartId && rearPartId
    ? { ...draft, frontPartId, rearPartId,
      tirePartId: requiredMachineSlots(draft.machineType).includes("TIRE") ? tirePartId! : null }
    : draft;
}

export function switchMachineType(draft: BuildDraft, machineType: RacingType | null, parts: MachinePart[] = []): BuildDraft {
  if (machineType === draft.machineType) return draft;
  const keep = (id: string) => parts.some((part) => part.id === id && part.racingType === machineType) ? id : "";
  return { ...draft, machineType, frontPartId: keep(draft.frontPartId), rearPartId: keep(draft.rearPartId), tirePartId: null };
}

export function machineSetupError(draft: BuildDraft, parts: MachinePart[]): string {
  if (!draft.machineType) return "Select a machine type.";
  const slots = { FRONT: draft.frontPartId, REAR: draft.rearPartId, TIRE: draft.tirePartId };
  const required = requiredMachineSlots(draft.machineType);
  for (const slot of ["FRONT", "REAR", "TIRE"] as const) {
    const id = slots[slot];
    if (!required.includes(slot)) {
      if (id) return "Boost machines cannot have tires. Remove the stored tire to continue.";
      continue;
    }
    const part = parts.find((part) => part.id === id);
    if (!id) return `Select ${slot.toLowerCase()} parts.`;
    if (!part || !part.racingType) return `${slot}: compatibility cannot be verified.`;
    if (part.type !== slot || part.racingType !== draft.machineType) return `${slot}: ${part.sourceMachineName} does not match the selected machine type.`;
  }
  return "";
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
