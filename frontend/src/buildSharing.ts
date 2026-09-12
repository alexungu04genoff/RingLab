import { gadgetPlateStatus } from "./buildForm";
import type { Build } from "./types";

function slotLabel(cost: number): string {
  return `${cost} ${cost === 1 ? "slot" : "slots"}`;
}

export function formatBuildForSharing(build: Build, url: string): string {
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
    `Front: ${build.frontPart.sourceMachineName}`,
    `Rear: ${build.rearPart.sourceMachineName}`,
    `Tires: ${build.tirePart.sourceMachineName}`,
    "",
    ...gadgets,
    "",
    `**Community:** ↑ ${build.upvotes} · ↓ ${build.downvotes}`,
    "",
    url,
  ].join("\n");
}
