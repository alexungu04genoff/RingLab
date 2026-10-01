import { useLoad } from "../../../shared/hooks/useLoad";
import type { Racer, MachinePart, Gadget, GadgetRulesCatalog, GameVersion, RaceMap } from "../../../shared/types";

/** Catalog requests used by the build editor; each retains its own loading/error state. */
export function useBuildEditorCatalog() {
  const racers = useLoad<Racer[]>("/racers");
  const parts = useLoad<MachinePart[]>("/machine-parts");
  const gadgets = useLoad<Gadget[]>("/gadgets");
  const gadgetRules = useLoad<GadgetRulesCatalog>("/stats/gadget-rules");
  const versions = useLoad<GameVersion[]>("/game-versions");
  const maps = useLoad<RaceMap[]>("/maps");
  return { racers, parts, gadgets, gadgetRules, versions, maps };
}
