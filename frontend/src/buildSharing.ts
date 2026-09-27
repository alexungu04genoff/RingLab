import { gadgetPlateStatus } from "./buildForm";
import type { Build, BuildStatsResult } from "./types";
import { statNames } from "./stats";
import { coverageLabel, signedPoints } from "./PassiveStats";
import { machineTypeLabel } from "./machineComposition";
import { mapSummary } from "./mapSelection";

function slotLabel(cost: number): string {
  return `${cost} ${cost === 1 ? "slot" : "slots"}`;
}

export function shareStats(stats?: BuildStatsResult): string[] {
  if (!stats) return [];
  const base = `Base stats: ${statNames.map(name => `${name} ${stats[name] ?? "unknown"}`).join(" · ")}`;
  if (!stats.passive) return [base];
  const value = stats.passive;
  const result = value.coverage === "UNSUPPORTED_VERSION" || value.coverage === "INVALID_LOADOUT" ? "unavailable"
    : statNames.map(name => `${name} ${stats[name] ?? "unknown"} ${signedPoints(value.adjustments[name])} = ${value.adjusted[name] ?? "unknown"}`).join(" · ");
  return [base, `Passive stats (${coverageLabel(value)}; Ver. ${value.supportedVersion}): ${result}`,
    "Known passive arithmetic only; excludes race-time effects and unresolved rules."];
}

export function formatBuildForSharing(build: Build, url: string, stats?: BuildStatsResult): string {
  const gadgetLines = build.gadgets.map((gadget) =>
    `• ${gadget.name} — ${gadget.slotCost === null ? "cost unknown" : slotLabel(gadget.slotCost)}`,
  );
  const allGadgetCostsKnown = build.gadgets.every((gadget) => gadget.slotCost !== null);
  const totalCost = gadgetPlateStatus(build.gadgets).totalCost;
  const gadgets = build.gadgets.length === 0
    ? ["**Gadgets**", "None"]
    : [
      allGadgetCostsKnown ? `**Gadgets — ${totalCost}/6 slots**` : "**Gadgets — cost incomplete**",
      ...gadgetLines,
    ];

  return [
    `**${build.title}** — RingLab`,
    "",
    `**Racer:** ${build.racer.name}`,
    `**Patch:** ${build.gameVersion ? `Ver. ${build.gameVersion.version}` : "Unspecified"}`,
    "",
    "**Machine**",
    `Type: ${machineTypeLabel(build.frontPart.racingType)}`,
    `Front: ${build.frontPart.sourceMachineName}`,
    `Rear: ${build.rearPart.sourceMachineName}`,
    ...(build.tirePart ? [`Tires: ${build.tirePart.sourceMachineName}`] : []),
    "",
    ...gadgets,
    ...shareStats(stats),
    "",
    `**Recommended maps:** ${mapSummary(build.mapRecommendations)}`,
    "",
    `**Community:** ↑ ${build.upvotes} · ↓ ${build.downvotes}`,
    "",
    url,
  ].join("\n");
}
