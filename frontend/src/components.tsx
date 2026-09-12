import { useState } from "react";
import { Link, useLocation } from "react-router-dom";
import type { ReactNode } from "react";
import type { Build, Gadget, GameVersion, Machine, Racer, RacingType } from "./types";
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
export const browseOrigin = (pathname: string, search: string) => `${pathname}${search}`;
export function buildDetailsOrigin(from: unknown) {
  return typeof from === "string" &&
    (from === "/" || from.startsWith("/?") ||
      from === "/my-builds" || from.startsWith("/my-builds?"))
    ? from
    : "/";
}
export type PatchAge = "latest" | "older" | "unspecified" | "unknown";

export function patchAge(version: GameVersion | null, versions: GameVersion[]): PatchAge {
  if (!version) return "unspecified";
  const catalogIndex = versions.findIndex(({ id }) => id === version.id);
  if (catalogIndex === 0) return "latest";
  return catalogIndex > 0 ? "older" : "unknown";
}

export function racingTypeClass(racingType: RacingType | null): string {
  return `racing-type-${racingType?.toLowerCase() ?? "unknown"}`;
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
        <span className="score" aria-label={`${build.upvotes} upvotes, ${build.downvotes} downvotes`}>
          <span className="upvote-count">↑ {build.upvotes}</span>
          <span aria-hidden="true"> · </span>
          <span className="downvote-count">↓ {build.downvotes}</span>
        </span>
      </div>
      <div className="card-body">
        <span className="eyebrow">{build.racer.name}</span>
        <h2>{build.title}</h2>
        <p className="machine-name">
          {build.frontPart.sourceMachineId === build.rearPart.sourceMachineId &&
          build.frontPart.sourceMachineId === build.tirePart.sourceMachineId
            ? build.frontPart.sourceMachineName
            : "Mixed machine"}
        </p>
        <div className="tags">
          {build.gameVersion ? (
            <span className={`patch-badge patch-${versionAge}`}>
              Ver. {build.gameVersion.version}{versionAge === "older" ? " · Older patch" : ""}
            </span>
          ) : (
            <span className="patch-badge patch-unspecified">Patch unspecified</span>
          )}
          {build.gadgets.slice(0, 2).map((g, i) => (
            <span key={`${g.id}-${i}`}>{g.name}</span>
          ))}
          {build.gadgets.length > 2 && <span>+{build.gadgets.length - 2}</span>}
          {build.gadgets.length === 0 && <span>No gadgets</span>}
        </div>
        <div className="card-meta">
          <span>@{build.author.username}</span>
          <time>{date(build.createdAt)}</time>
        </div>
      </div>
    </Link>
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
          <option key={i.id} value={i.id}>
            {i.name}
            {` · ${i.racingType?.toLowerCase() ?? "Unknown"}`}
          </option>
        ))}
      </select>
    </label>
  );
}
