import type { MachinePartType, RacingType } from "./types";

export const machineTypes: RacingType[] = ["SPEED", "ACCELERATION", "HANDLING", "POWER", "BOOST"];

export function requiredMachineSlots(type: RacingType | null): MachinePartType[] {
  if (!type) return [];
  return type === "BOOST" ? ["FRONT", "REAR"] : ["FRONT", "REAR", "TIRE"];
}

export function machineTypeLabel(type: RacingType | null): string {
  if (!type) return "Unknown";
  return type === "BOOST" ? "Boost · Extreme Gear" : type[0] + type.slice(1).toLowerCase();
}
