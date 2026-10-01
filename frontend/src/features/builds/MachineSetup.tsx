import { CollectionArtwork as Artwork } from "../collection/CollectionArtwork";
import { RacingTypeBadge } from "../../shared/ui/RacingTypeBadge";
import { machineTypeLabel } from "./machineComposition";
import type { Build, MachinePart } from "../../shared/types";

export function isStockSetup(build: Pick<Build, "frontPart" | "rearPart" | "tirePart">) {
  return build.frontPart.sourceMachineId === build.rearPart.sourceMachineId &&
    (!build.tirePart || build.frontPart.sourceMachineId === build.tirePart.sourceMachineId);
}

export function MachineSetup({ build, compact = false, differences }: {
  build: Pick<Build, "frontPart" | "rearPart" | "tirePart">;
  compact?: boolean;
  differences?: Partial<Record<MachinePart["type"], boolean>>;
}) {
  const stockSetup = isStockSetup(build);
  const machineType = build.frontPart.racingType;
  const setupParts: Array<[string, MachinePart]> = [
    ["FRONT", build.frontPart], ["REAR", build.rearPart],
    ...(build.tirePart ? [["TIRES", build.tirePart] as [string, MachinePart]] : []),
  ];
  return (
    <div className={`machine-setup ${compact ? "compact-machine-setup" : ""}`}>
      <div className="machine-setup-heading">
        <div>
          <div className="eyebrow">MACHINE SETUP</div>
          <h2>{machineTypeLabel(machineType)} parts by source machine</h2>
        </div>
        <span className={`setup-indicator ${stockSetup ? "stock-setup" : "mixed-setup"}`}>
          {stockSetup ? "Stock setup" : "Mixed setup"}
        </span>
      </div>
      <div className="machine-setup-grid">
        {setupParts
          .map(([label, part]) => (
            <article className={`machine-part-card ${differences?.[part.type] ? "different" : ""}`}
              data-difference={differences?.[part.type] ? `${label} part` : undefined} key={label}>
              <Artwork item={{ id: part.sourceMachineId, name: part.sourceMachineName,
                imagePath: part.sourceMachineImagePath, racingType: part.racingType }} compact />
              <div className="machine-part-copy">
                <strong>{part.sourceMachineName}</strong>
                {part.racingType && (
                  <RacingTypeBadge kind="machine" type={part.racingType} className="part-type" />
                )}
              </div>
              <span className="eyebrow machine-part-slot">{label}</span>
            </article>
          ))}
      </div>
    </div>
  );
}
