import { useState } from "react";
import { useLoad } from "../../shared/hooks/useLoad";
import { passiveStatsPath } from "../stats/stats";
import { defaultPriorities, recommendationSlots as slots, lockTypeConflict, validPriorities, validBalanced, recommendationIdentity } from "./recommendation";
import type { RecommendationSelection, RecommendationCatalog } from "./recommendation";
import type { BaseStats, BuildDraft, BuildStatsResult, GameVersion, RacingType } from "../../shared/types";

/** Settings and eligibility for a recommendation against one frozen starting setup. */
export function useRecommendationConfiguration(draft: BuildDraft, locks: RecommendationSelection,
  catalog: RecommendationCatalog, version: GameVersion | null, referenceStats?: BuildStatsResult | null) {
  const [machineType, setMachineType] = useState<RacingType | null>(draft.machineType);
  const [priorities, setPriorities] = useState(() => {
    const current = referenceStats?.passive?.coverage === "CALCULATED" ? referenceStats.passive.adjusted : null;
    const value = (priority: RacingType) => {
      const points = current?.[priority.toLowerCase() as keyof BaseStats];
      return points != null && Number.isFinite(points) ? points : -Infinity;
    };
    return [...defaultPriorities].sort((left, right) => {
      const a = value(left), b = value(right);
      return a === b ? 0 : a > b ? -1 : 1;
    });
  });
  const [mode, setMode] = useState<"STRICT" | "BALANCED">("STRICT");
  const [secondary, setSecondary] = useState<RacingType[]>([]);
  const [losses, setLosses] = useState<Record<RacingType, string>>({ SPEED: "0", ACCELERATION: "0", HANDLING: "0", BOOST: "0", POWER: "0" });
  const [reference] = useState(() => ({ ...draft, gadgetIds: [...draft.gadgetIds] }));
  // Freeze the selected patch ID, but allow its catalog entry to finish loading.
  const referenceVersion = version?.id === reference.gameVersionId ? version : null;
  const [referenceIdentity] = useState(() => recommendationIdentity(draft, locks));
  const loadedReferenceStats = useLoad<BuildStatsResult>(mode === "BALANCED" && !referenceStats ? passiveStatsPath(reference) : "");
  const passiveReference = referenceStats?.passive ?? loadedReferenceStats.data?.passive;
  const referenceValues = passiveReference?.coverage === "CALCULATED" ? passiveReference.adjusted : null;
  const referenceAvailable = !!referenceValues && defaultPriorities.every(priority => {
    const value = referenceValues[priority.toLowerCase() as keyof BaseStats];
    return value != null && Number.isFinite(value) && value >= 0;
  });
  const activePriorities = mode === "BALANCED" ? priorities.filter(priority => !secondary.includes(priority)) : priorities;
  const identity = JSON.stringify([mode, machineType, priorities, secondary, losses]);
  const conflict = lockTypeConflict(machineType, locks, catalog.parts);
  let calculationBlocker = "";
  if (referenceIdentity !== recommendationIdentity(draft, locks))
    calculationBlocker = "Your draft or locks changed after this popup opened. Close and reopen it to use your current setup.";
  else if (!reference.gameVersionId)
    calculationBlocker = "Close this popup and select Ver. 1.4.1 in the editor's Game version / Patch field.";
  else if (!referenceVersion)
    calculationBlocker = "The selected patch details are not available yet. Wait for the patch list to load, or close and reopen this popup.";
  else if (referenceVersion.version !== "1.4.1")
    calculationBlocker = "Recommendations support Ver. 1.4.1. Close this popup to change the editor's patch.";
  else if (!machineType)
    calculationBlocker = "Choose a recommendation machine type.";
  else if (conflict)
    calculationBlocker = conflict;
  else if (!validPriorities(priorities))
    calculationBlocker = "Rank all five stats before calculating.";
  else if (mode === "BALANCED") {
    if (loadedReferenceStats.loading)
      calculationBlocker = "Loading the current setup's stats…";
    else if (loadedReferenceStats.error)
      calculationBlocker = `Could not load reference stats: ${loadedReferenceStats.error}`;
    else if (!referenceAvailable) {
      const unresolved = passiveReference?.effects?.filter(effect => effect.status === "UNSUPPORTED" || effect.status === "REQUIRES_SELECTION") ?? [];
      const missing = slots.filter(slot => slot.key !== "tirePartId" && !reference[slot.key]).map(slot => slot.label);
      if (missing.length)
        calculationBlocker = `Choose ${missing.join(", ")} in the editor before using Balanced. Strict can recommend a starting setup instead.`;
      else if (passiveReference?.coverage === "INVALID_LOADOUT")
        calculationBlocker = "The starting setup has an invalid part combination or gadget plate. Return to the editor to fix it before using Balanced.";
      else if (unresolved.length)
        calculationBlocker = `Balanced cannot compare this setup yet: ${unresolved.map(effect => `${effect.gadgetName}: ${effect.explanation}`).join(" ")} Return to the editor to change these gadgets, or use Strict to search without this baseline.`;
      else if (passiveReference?.adjusted && Object.values(passiveReference.adjusted).some(value => value != null && value < 0))
        calculationBlocker = "Balanced cannot use a starting stat below zero. Change your setup in the editor, or use Strict.";
      else
        calculationBlocker = "Some starting stats are unavailable for this patch. Check the selected parts in the editor, or use Strict to find a supported setup.";
    }
    else if (!validBalanced(activePriorities, secondary, losses))
      calculationBlocker = "Enter a maximum loss from 0 to 100 for each prioritized stat. Leave 0 to allow no decrease.";
  }
  return { machineType, setMachineType, priorities, setPriorities, mode, setMode, secondary, setSecondary,
    losses, setLosses, reference, referenceVersion, referenceIdentity, passiveReference, referenceValues,
    referenceAvailable, activePriorities, identity, calculationBlocker };
}
