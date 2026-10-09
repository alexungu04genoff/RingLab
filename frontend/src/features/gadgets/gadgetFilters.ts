import type { Gadget, RacingType } from "../../shared/types";
import type { GadgetEffectKind, gadgetPresentation } from "./gadgetPresentation";

export interface GadgetFilters {
  slots: number[];
  effects: GadgetEffectKind[];
  types: RacingType[];
  acquisition: NonNullable<Gadget["acquisitionKind"]>[];
}

export const emptyGadgetFilters: GadgetFilters = { slots: [], effects: [], types: [], acquisition: [] };

/** OR within each group; an empty group does not restrict the results. */
export function matchesGadgetFilters(metadata: ReturnType<typeof gadgetPresentation>, filters: GadgetFilters) {
  return (filters.slots.length === 0 || (metadata.slotCost !== null && filters.slots.includes(metadata.slotCost)))
    && (filters.effects.length === 0 || filters.effects.some(kind => metadata.effectKinds.includes(kind)))
    && (filters.types.length === 0 || filters.types.some(type => metadata.appliesTo.includes(type)))
    && (filters.acquisition.length === 0 || filters.acquisition.includes(metadata.acquisitionKind));
}
