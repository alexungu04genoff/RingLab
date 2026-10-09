import { useEffect, useState } from "react";
import type { Gadget, RacingType } from "../../shared/types";
import { effectBadges, type GadgetEffectKind } from "./gadgetPresentation";
import { emptyGadgetFilters, type GadgetFilters } from "./gadgetFilters";
import "../../styles/gadget-filters.css";

const types: RacingType[] = ["SPEED", "ACCELERATION", "HANDLING", "POWER", "BOOST"];
const acquisitions: { value: NonNullable<Gadget["acquisitionKind"]>; label: string }[] = [
  { value: "STANDARD_UNLOCK", label: "Standard unlock" },
  { value: "FESTIVAL_REWARD", label: "Festival reward" },
  { value: "UNKNOWN", label: "Unknown" },
];
const toggle = <T,>(values: T[], value: T) => values.includes(value)
  ? values.filter(current => current !== value) : [...values, value];

export function GadgetFilterControls({ filters, onChange }: {
  filters: GadgetFilters; onChange: (filters: GadgetFilters) => void;
}) {
  const [open, setOpen] = useState(() => window.matchMedia?.("(min-width: 641px)").matches ?? true);
  useEffect(() => {
    const media = window.matchMedia?.("(min-width: 641px)");
    if (!media) return;
    const resize = () => setOpen(media.matches);
    media.addEventListener("change", resize);
    return () => media.removeEventListener("change", resize);
  }, []);
  const active = filters.slots.length + filters.effects.length + filters.types.length + filters.acquisition.length;
  return <details className="gadget-filters" open={open} onToggle={event => setOpen(event.currentTarget.open)}>
    <summary>Filters{active > 0 ? ` · ${active} active` : ""}</summary>
    <div className="gadget-filter-groups">
      <fieldset><legend>Slot cost</legend><div>
        {[1, 2, 3].map(cost => <label key={cost}><input type="checkbox" checked={filters.slots.includes(cost)}
          onChange={() => onChange({ ...filters, slots: toggle(filters.slots, cost) })} />
          {cost} {cost === 1 ? "slot" : "slots"}</label>)}
      </div></fieldset>
      <fieldset><legend>Effect</legend><div>
        {(Object.keys(effectBadges) as GadgetEffectKind[]).map(kind => <label key={kind}>
          <input type="checkbox" checked={filters.effects.includes(kind)}
            onChange={() => onChange({ ...filters, effects: toggle(filters.effects, kind) })} />{effectBadges[kind].label}
        </label>)}
      </div></fieldset>
      <fieldset><legend>Applies to</legend><div>
        <button type="button" aria-pressed={filters.types.length === 0} onClick={() => onChange({ ...filters, types: [] })}>Any</button>
        {types.map(type => <label key={type}><input type="checkbox" checked={filters.types.includes(type)}
          onChange={() => onChange({ ...filters, types: toggle(filters.types, type) })} />
          {type[0] + type.slice(1).toLowerCase()}</label>)}
      </div><small>Reviewed type-specific effects. Any leaves types unrestricted.</small></fieldset>
      <fieldset><legend>Acquisition</legend><div>
        {acquisitions.map(({ value, label }) => <label key={value}><input type="checkbox" checked={filters.acquisition.includes(value)}
          onChange={() => onChange({ ...filters, acquisition: toggle(filters.acquisition, value) })} />{label}</label>)}
      </div></fieldset>
    </div>
    <button type="button" className="small" disabled={active === 0} onClick={() => onChange(emptyGadgetFilters)}>Clear filters</button>
    <span className="gadget-filter-note">Keeps your search and selected gadgets.</span>
  </details>;
}
