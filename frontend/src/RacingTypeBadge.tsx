import { RacerIcon, SteeringWheelIcon } from "./icons";
import type { RacingType } from "./types";

export function racingTypeClass(type: RacingType | null): string {
  return `racing-type-${type?.toLowerCase() ?? "unknown"}`;
}

export function racingTypeLabel(type: RacingType | null): string {
  if (!type) return "Unknown";
  return type[0] + type.slice(1).toLowerCase();
}

export function RacingTypeBadge({ kind, type, className = "type-badge" }: {
  kind: "racer" | "machine"; type: RacingType | null; className?: string;
}) {
  const label = `${kind === "racer" ? "Racer" : "Machine"} type: ${racingTypeLabel(type)}`;
  return <span className={`${className} racing-type ${racingTypeClass(type)}`} role="img" aria-label={label} title={label}>
    {kind === "racer" ? <RacerIcon /> : <SteeringWheelIcon />}<span>{type ?? "Unknown"}</span>
  </span>;
}
