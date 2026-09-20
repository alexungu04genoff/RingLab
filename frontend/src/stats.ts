import type { BaseStats, Build, BuildDraft } from "./types";
import { savedMachineSetupError } from "./buildForm";

export const statNames = ["speed", "acceleration", "handling", "power", "boost"] as const;
export type StatName = typeof statNames[number];

export type StatsBreakdown = { character: BaseStats; machine: BaseStats };

export function buildStatsWarning(build: Pick<Build, "frontPart" | "rearPart" | "tirePart">) {
  const issue = savedMachineSetupError(build);
  return issue
    ? `${issue} Stats are calculated from the selected parts and may not reflect a valid in-game setup.`
    : null;
}

export function statPresentation(name: StatName, stats?: BaseStats, breakdown?: StatsBreakdown) {
  const value = stats?.[name];
  const character = breakdown?.character?.[name];
  const machine = breakdown?.machine?.[name];
  const hasBreakdown = value != null && character != null && machine != null;
  const characterWidth = hasBreakdown ? Math.min(100, Math.max(0, character)) : 0;
  return {
    value,
    character,
    machine,
    hasBreakdown,
    label: name[0].toUpperCase() + name.slice(1),
    width: value == null ? 0 : Math.min(100, Math.max(0, value)),
    characterWidth,
    machineWidth: hasBreakdown ? Math.min(100 - characterWidth, Math.max(0, machine)) : 0,
  };
}

export function buildStatsPath(draft: Pick<BuildDraft,
  "gameVersionId" | "racerId" | "frontPartId" | "rearPartId" | "tirePartId">) {
  if (!draft.gameVersionId) return "";
  const params = new URLSearchParams();
  for (const key of ["gameVersionId", "racerId", "frontPartId", "rearPartId", "tirePartId"] as const) {
    if (draft[key]) params.set(key, draft[key]);
  }
  return `/stats/build?${params}`;
}
