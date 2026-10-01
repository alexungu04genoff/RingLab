import type { MachinePartType, RacingType } from "../../shared/types";

export const machineTypes: RacingType[] = ["SPEED", "ACCELERATION", "HANDLING", "POWER", "BOOST"];

export function requiredMachineSlots(type: RacingType | null): MachinePartType[] {
  if (!type) return [];
  return type === "BOOST" ? ["FRONT", "REAR"] : ["FRONT", "REAR", "TIRE"];
}

export function machineTypeLabel(type: RacingType | null): string {
  if (!type) return "Unknown";
  return type === "BOOST" ? "Boost · Extreme Gear" : type[0] + type.slice(1).toLowerCase();
}

const partSlots = [
  { key: "frontPartId", type: "FRONT", label: "Front" },
  { key: "rearPartId", type: "REAR", label: "Rear" },
  { key: "tirePartId", type: "TIRE", label: "Tires" },
] as const;

export function visibleMachinePartSlots(machineType: RacingType | null) {
  return partSlots.filter(slot => !machineType ? slot.type !== "TIRE" : requiredMachineSlots(machineType).includes(slot.type));
}
