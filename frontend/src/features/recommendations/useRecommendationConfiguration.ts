import { useState } from "react";
import { defaultPriorities, lockTypeConflict, validPriorities, validBalanced, recommendationIdentity } from "./recommendation";
import type { RecommendationSelection, RecommendationCatalog } from "./recommendation";
import type { BaseStats, BuildDraft, BuildStatsResult, GameVersion, RacingType } from "../../shared/types";

/** Settings and eligibility for a recommendation against a captured draft (presentation and convenience only). */
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
  const [gadgetScope, setGadgetScope] = useState<"KEEP_CURRENT" | "OPTIMIZE_UNLOCKED">("KEEP_CURRENT");
  const [ignored, setIgnored] = useState<RacingType[]>([]);
  const [losses, setLosses] = useState<Record<RacingType, string>>({ SPEED: "0", ACCELERATION: "0", HANDLING: "0", BOOST: "0", POWER: "0" });
  const [reference] = useState(() => ({ ...draft, gadgetIds: [...draft.gadgetIds] }));
  // Freeze the selected patch ID, but allow its catalog entry to finish loading.
  const referenceVersion = version?.id === reference.gameVersionId ? version : null;
  const [referenceIdentity] = useState(() => recommendationIdentity(draft, locks));
  const passiveReference = referenceStats?.passive;
  const referenceValues = passiveReference?.coverage === "CALCULATED" ? passiveReference.adjusted : null;
  const activePriorities = mode === "BALANCED" ? priorities.filter(priority => !ignored.includes(priority)) : priorities;
  const identity = JSON.stringify([mode, machineType, priorities, ignored, losses, gadgetScope]);
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
  else if (mode === "BALANCED" && !validBalanced(activePriorities, ignored, losses))
    calculationBlocker = "Enter a maximum sacrifice from 0 to less than 100 for each active stat, or choose Ignore.";
  return { machineType, setMachineType, priorities, setPriorities, mode, setMode, gadgetScope, setGadgetScope, ignored, setIgnored,
    losses, setLosses, reference, referenceVersion, referenceIdentity, passiveReference, referenceValues,
    activePriorities, identity, calculationBlocker };
}
