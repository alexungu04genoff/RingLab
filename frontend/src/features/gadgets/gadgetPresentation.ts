import type { Gadget, GadgetRulesCatalog, ScenarioRulesCatalog } from "../../shared/types";

export const effectBadges = {
  PASSIVE: { label: "Passive stats", explanation: "Counted by recommendations when supported." },
  CONDITIONAL: { label: "Race condition", explanation: "Activates only during a race condition; excluded from general recommendations." },
  NON_STAT: { label: "Utility", explanation: "Changes items, limits, timing or mechanics instead of the five displayed stats." },
  UNSUPPORTED: { label: "Effect unsupported", explanation: "The gadget exists, but RingLab does not yet claim its complete effect." },
  SCENARIO: { label: "Scenario modeled", explanation: "Can be inspected with Scenario Preview." },
} as const;

export function gadgetEffectKinds(id: string, rules?: GadgetRulesCatalog, scenarios?: ScenarioRulesCatalog) {
  const kinds = new Set<keyof typeof effectBadges>(rules?.gadgets.find(gadget => gadget.gadgetId === id)?.effects.map(effect => effect.kind));
  if (scenarios?.controls.some(control => control.gadgetId === id)) kinds.add("SCENARIO");
  return (Object.keys(effectBadges) as (keyof typeof effectBadges)[]).filter(kind => kinds.has(kind));
}

export function acquisitionDescription(gadget: Gadget) {
  if (gadget.acquisitionKind === "FESTIVAL_REWARD") return gadget.acquisitionLabel
    ? `Originally awarded during the ${gadget.acquisitionLabel}.` : "Originally awarded as a festival reward.";
  if (gadget.acquisitionKind === "STANDARD_UNLOCK") return gadget.acquisitionLabel ?? "Standard in-game unlock.";
  return "Acquisition has not been reviewed.";
}

/** Guidance only: eligibility and complete passive evaluation remain authoritative on the server. */
export function unsupportedGadgetGuidance(selected: string[], locked: string[], scope: "KEEP_CURRENT" | "OPTIMIZE_UNLOCKED",
  gadgets: Gadget[], rules?: GadgetRulesCatalog) {
  const unsupported = selected.filter(id => gadgetEffectKinds(id, rules).includes("UNSUPPORTED"));
  const blocking = unsupported.filter(id => scope === "KEEP_CURRENT" || locked.includes(id));
  const names = (ids: string[]) => ids.map(id => gadgets.find(gadget => gadget.id === id)?.name ?? `Unknown gadget (${id})`).join(", ");
  const issue = (ids: string[]) => `${names(ids)} ${ids.length === 1 ? "has an effect" : "have effects"} RingLab cannot evaluate yet.`;
  return {
    blocker: blocking.length ? `${issue(blocking)} ${scope === "KEEP_CURRENT"
      ? `Remove ${blocking.length === 1 ? "it" : "them"} or choose Optimize unlocked gadgets.` : "Unlock or remove the listed gadget(s) in the editor, then reopen recommendations."}` : "",
    notice: scope === "OPTIMIZE_UNLOCKED" && unsupported.some(id => !locked.includes(id))
      ? `${issue(unsupported.filter(id => !locked.includes(id)))} The optimizer may remove these unlocked gadgets.` : "",
  };
}
