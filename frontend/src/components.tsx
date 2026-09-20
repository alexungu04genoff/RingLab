import { useState } from "react";
import { Link, useLocation } from "react-router-dom";
import type { ReactNode } from "react";
import { ComponentsIcon } from "./icons";
import { buildStatsPath, statNames, statPresentation } from "./stats";
import type { Build, BuildStatsResult, Gadget, GameVersion, Machine, MachinePart, Racer, RacingType } from "./types";
import { useLoad } from "./useLoad";
export function ErrorNotice({ message }: { message: string }) {
  return message ? (
    <div className="error" role="alert">
      {message}
    </div>
  ) : null;
}
export function Artwork({
  item,
  compact = false,
  portrait = false,
}: {
  item: Racer | Machine | Gadget;
  compact?: boolean;
  portrait?: boolean;
}) {
  const [failed, setFailed] = useState(false);
  const local = item.imagePath?.startsWith("/assets/");
  const racingType = "racingType" in item ? item.racingType ?? "unknown" : "gadget";
  return (
    <div
      className={`artwork ${compact ? "compact" : ""} ${portrait ? "portrait" : ""} type-${racingType.toLowerCase()}`}
    >
      {local && !failed ? (
        <img
          src={item.imagePath!}
          alt={item.name}
          onError={() => setFailed(true)}
        />
      ) : (
        <span aria-hidden="true">
          {item.name
            .split(" ")
            .map((w) => w[0])
            .slice(0, 2)
            .join("")}
        </span>
      )}
    </div>
  );
}
export const date = (value: string) =>
  new Date(value).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });

export const countLabel = (count: number, singular: string, plural = `${singular}s`) =>
  `${count} ${count === 1 ? singular : plural}`;
export const browseOrigin = (pathname: string, search: string) => `${pathname}${search}`;
export function buildDetailsOrigin(from: unknown) {
  return typeof from === "string" &&
    (from === "/" || from.startsWith("/?") ||
      from === "/my-builds" || from.startsWith("/my-builds?"))
    ? from
    : "/";
}
export type PatchAge = "latest" | "older" | "unspecified" | "unknown";
const CARD_GADGET_LIMIT = 6;

export function patchAge(version: GameVersion | null, versions: GameVersion[]): PatchAge {
  if (!version) return "unspecified";
  const catalogIndex = versions.findIndex(({ id }) => id === version.id);
  if (catalogIndex === 0) return "latest";
  return catalogIndex > 0 ? "older" : "unknown";
}

export function racingTypeClass(racingType: RacingType | null): string {
  return `racing-type-${racingType?.toLowerCase() ?? "unknown"}`;
}

export function racingTypeLabel(racingType: RacingType | null): string {
  if (!racingType) return "Unknown";
  const lower = racingType.toLowerCase();
  return `${lower.charAt(0).toUpperCase()}${lower.slice(1)}`;
}

function BuildPartIcon({ part, label, abbreviation }: {
  part: MachinePart;
  label: string;
  abbreviation: string;
}) {
  const typeLabel = racingTypeLabel(part.racingType);
  const tooltip = `${label} · ${typeLabel}: ${part.sourceMachineName}`;
  return (
    <span className={`card-part-icon ${racingTypeClass(part.racingType)}`} tabIndex={0} aria-label={tooltip}>
      <Artwork item={{
        id: part.sourceMachineId,
        name: part.sourceMachineName,
        imagePath: part.sourceMachineImagePath,
        racingType: part.racingType,
      }} compact />
      <span className="card-part-slot" aria-hidden="true">{abbreviation}</span>
      <span role="tooltip">{tooltip}</span>
    </span>
  );
}

export function BuildGadgetIcon({ gadget }: { gadget: Gadget }) {
  const tooltip = gadget.description ? `${gadget.name}: ${gadget.description}` : gadget.name;
  return (
    <span className="card-gadget-icon" tabIndex={0} aria-label={tooltip}>
      <Artwork item={gadget} compact />
      <span role="tooltip">
        <strong>{gadget.name}</strong>
        {gadget.description && <span>{gadget.description}</span>}
      </span>
    </span>
  );
}

export function BuildCard({ build, versions = [] }: { build: Build; versions?: GameVersion[] }) {
  const location = useLocation();
  const versionAge = patchAge(build.gameVersion, versions);
  return (
    <Link
      to={`/builds/${build.id}`}
      state={{ from: browseOrigin(location.pathname, location.search) }}
      className="build-card"
    >
      <div className="card-art">
        <Artwork item={build.racer} portrait />
        <span className={`type-badge racing-type ${racingTypeClass(build.racer.racingType)}`}>
          {build.racer.racingType ?? "Unknown"}
        </span>
        <span className="score" aria-label={`Community score ${build.score}, ${countLabel(build.upvotes, "upvote")}, ${countLabel(build.downvotes, "downvote")}`}>
          <span className="community-score">Score {build.score}</span>
          <span className="score-help" tabIndex={0} aria-label="How Best rated works">
            <span aria-hidden="true">i</span>
            <span role="tooltip">Best rated shows positive-score builds first, then neutral, then negative. Within each group, builds with more consistently positive votes rank higher.</span>
          </span>
          <span className="upvote-count">↑ {build.upvotes}</span>
          <span className="downvote-count">↓ {build.downvotes}</span>
        </span>
        <span className="card-parts" aria-label="Machine parts">
          <BuildPartIcon part={build.frontPart} label="Front" abbreviation="F" />
          <BuildPartIcon part={build.rearPart} label="Rear" abbreviation="R" />
          {build.tirePart && <BuildPartIcon part={build.tirePart} label="Tires" abbreviation="T" />}
        </span>
      </div>
      <div className="card-body">
        <div className="card-kicker">
          <span className="eyebrow">{build.racer.name}</span>
          <span className="machine-name">
            <ComponentsIcon />
            <span>{build.frontPart.sourceMachineFamily === "BOARD" ? "Board · " : ""}{build.frontPart.sourceMachineId === build.rearPart.sourceMachineId &&
              (!build.tirePart || build.frontPart.sourceMachineId === build.tirePart.sourceMachineId)
                ? build.frontPart.sourceMachineName
                : "Mixed machine"}</span>
          </span>
        </div>
        <h2>{build.title}</h2>
        <BuildCardStats build={build} />
        <div className="tags">
          {build.gadgets.slice(0, CARD_GADGET_LIMIT).map((g, i) => (
            <BuildGadgetIcon key={`${g.id}-${i}`} gadget={g} />
          ))}
          {build.gadgets.length > CARD_GADGET_LIMIT && <span>+{build.gadgets.length - CARD_GADGET_LIMIT}</span>}
          {build.gadgets.length === 0 && <span>No gadgets</span>}
        </div>
        <div className="card-meta">
          <span>@{build.author.username}</span>
          {build.gameVersion ? (
            <span className={`patch-badge patch-${versionAge}`}>
              Ver. {build.gameVersion.version}
            </span>
          ) : (
            <span className="patch-badge patch-unspecified">Patch unspecified</span>
          )}
          <time>{date(build.createdAt)}</time>
        </div>
      </div>
    </Link>
  );
}

function BuildCardStats({ build }: { build: Build }) {
  const path = buildStatsPath({ gameVersionId: build.gameVersion?.id ?? null,
    racerId: build.racer.id, frontPartId: build.frontPart.id,
    rearPartId: build.rearPart.id, tirePartId: build.tirePart?.id ?? null });
  const result = useLoad<BuildStatsResult>(path);
  if (!path) return <div className="card-stats unavailable">Stats unavailable</div>;
  if (result.loading) return <div className="card-stats unavailable">Loading stats…</div>;
  if (result.error || !result.data) return <div className="card-stats unavailable">Stats unavailable</div>;
  return <dl className="card-stats" aria-label="Base stats">
    {statNames.map((name) => {
      const { value, hasBreakdown, label, width, characterWidth, machineWidth }
        = statPresentation(name, result.data, result.data);
      return <div className={`card-stat stat-${name}`} key={name}>
        <div><dt>{label}</dt><dd>{value ?? "—"}</dd></div>
        <span className={`card-stat-track${value == null ? " unknown" : ""}`} aria-hidden="true">
          {hasBreakdown ? <>
            <span className="card-stat-character-fill" style={{ width: `${characterWidth}%` }} />
            <span className="card-stat-machine-fill" style={{ width: `${machineWidth}%` }} />
          </> : <span style={{ width: `${width}%` }} />}
        </span>
      </div>;
    })}
  </dl>;
}
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
  const family = build.frontPart.sourceMachineFamily;
  const setupParts: Array<[string, MachinePart]> = [
    ["FRONT", build.frontPart], ["REAR", build.rearPart],
    ...(build.tirePart ? [["TIRES", build.tirePart] as [string, MachinePart]] : []),
  ];
  return (
    <div className={`machine-setup ${compact ? "compact-machine-setup" : ""}`}>
      <div className="machine-setup-heading">
        <div>
          <div className="eyebrow">MACHINE SETUP</div>
          <h2>{family === "BOARD" ? "Board parts by source machine" : "Parts by source machine"}</h2>
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
                  <span className={`part-type racing-type ${racingTypeClass(part.racingType)}`}>
                    {part.racingType}
                  </span>
                )}
              </div>
              <span className="eyebrow machine-part-slot">{label}</span>
            </article>
          ))}
      </div>
    </div>
  );
}
export function ItemSelect({
  label,
  icon,
  items,
  value,
  onChange,
  optional = false,
  emptyLabel,
}: {
  label: string;
  icon?: ReactNode;
  items: Array<Racer | Machine>;
  value: string;
  onChange: (value: string) => void;
  optional?: boolean;
  emptyLabel?: string;
}) {
  return (
    <label>
      <span className="field-label">{icon}{label}</span>
      <select
        value={value}
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
            {` · ● ${racingTypeLabel(i.racingType)}`}
          </option>
        ))}
      </select>
    </label>
  );
}
