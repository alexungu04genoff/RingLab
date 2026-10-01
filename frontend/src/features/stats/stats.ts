import type { BaseStats, Build, BuildDraft } from "../../shared/types";
import { savedMachineSetupError } from "../builds/buildForm";

export const statNames = ["speed", "acceleration", "handling", "power", "boost"] as const;
// Changes with the reviewed server table; only calculation inputs participate in these keys.
export const passiveRuleset = "crossworlds-1.4.1-passive-2026-09-28.4";
export function passiveStatsPath(draft: Pick<BuildDraft,
  "gameVersionId" | "racerId" | "frontPartId" | "rearPartId" | "tirePartId" | "gadgetIds">) {
  const base = buildStatsPath(draft);
  if (!base) return "";
  const params = new URLSearchParams(base.split("?")[1]);
  params.set("ruleset", passiveRuleset);
  [...draft.gadgetIds].sort().forEach(id => params.append("gadgetId", id));
  return `/stats/passive-build?${params}`;
}
export function persistedStatsPath(build: Build) {
  const params = new URLSearchParams({ ruleset: passiveRuleset });
  params.set("selection", [build.gameVersion?.id, build.racer.id, build.frontPart.id,
    build.rearPart.id, build.tirePart?.id, ...build.gadgets.map(g => g.id).sort()].join(","));
  return `/stats/persisted/${encodeURIComponent(build.id)}?${params}`;
}
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
