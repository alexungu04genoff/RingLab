import type { Gadget } from "../../shared/types";

export function gadgetOwnershipGuidance(selected: string[], locked: string[], scope: "KEEP_CURRENT" | "OPTIMIZE_UNLOCKED",
  gadgets: Gadget[], excluded: string[]) {
  const unowned = [...new Set(selected)].filter(id => excluded.includes(id));
  const blocking = unowned.filter(id => scope === "KEEP_CURRENT" || locked.includes(id));
  const unlocked = unowned.filter(id => !locked.includes(id));
  const names = (ids: string[]) => {
    const labels = ids.map(id => gadgets.find(gadget => gadget.id === id)?.name ?? `Unknown gadget (${id})`);
    return labels.length > 1 ? `${labels.slice(0, -1).join(", ")} and ${labels.at(-1)}` : labels[0];
  };
  return {
    blocker: blocking.length ? `${names(blocking)} ${blocking.length === 1 ? "is" : "are"} marked Not owned. ${scope === "KEEP_CURRENT"
      ? `Remove ${blocking.length === 1 ? "it" : "them"} or choose Optimize unlocked gadgets.`
      : `Unlock or remove ${blocking.length === 1 ? "it" : "them"} before calculating.`}` : "",
    notice: scope === "OPTIMIZE_UNLOCKED" && unlocked.length
      ? `${names(unlocked)} ${unlocked.length === 1 ? "is" : "are"} marked Not owned. The optimizer may remove unlocked unowned gadgets.` : "",
  };
}
