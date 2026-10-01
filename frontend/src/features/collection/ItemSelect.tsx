import { useId, type ReactNode } from "react";
import { isExcluded, useCollection } from "./Collection";
import { racingTypeClass, racingTypeLabel } from "../../shared/ui/RacingTypeBadge";
import type { Racer, Machine } from "../../shared/types";

export function ItemSelect({
  label,
  icon,
  items,
  value,
  onChange,
  optional = false,
  disabled = false,
  emptyLabel,
  labelAction,
}: {
  label: string;
  icon?: ReactNode;
  items: Array<Racer | Machine>;
  value: string;
  onChange: (value: string) => void;
  optional?: boolean;
  disabled?: boolean;
  emptyLabel?: string;
  labelAction?: ReactNode;
}) {
  const selectId = useId();
  const collection = useCollection();
  const select = <select
        id={selectId}
        value={value}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value)}
        required={!optional}
      >
        <option value="">
          {emptyLabel ?? (optional
            ? `All ${label.toLowerCase()}s`
            : `Choose a ${label.toLowerCase()}`)}
        </option>
        {items.map((i) => (
          <option key={i.id} value={i.id}
            className={`typed-option ${racingTypeClass(i.racingType)}`}>
            {i.name}
            {collection.status === "ready" && isExcluded(collection.data, label === "Racer" ? "RACER" : "MACHINE", i.id) ? " — Not owned" : ""}
            {` · ● ${racingTypeLabel(i.racingType)}`}
          </option>
        ))}
      </select>;
  return labelAction ? <div><div className="selection-field-heading">
    {labelAction}<label htmlFor={selectId}><span className="field-label">{icon}{label}</span></label>
  </div>{select}</div> : <label><span className="field-label">{icon}{label}</span>{select}</label>;
}
