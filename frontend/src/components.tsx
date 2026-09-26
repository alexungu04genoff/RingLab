import { useState } from "react";
import { Link, useLocation } from "react-router-dom";
import type { ReactNode } from "react";
import type { Build, BuildStatsResult, Gadget, GameVersion, Machine, MachinePart, Racer } from "./types";
import { CardStats } from "./CardStats";
import type { PageCardStats } from "./CardStats";
import { RacingTypeBadge, racingTypeClass, racingTypeLabel } from "./RacingTypeBadge";
export { racingTypeClass, racingTypeLabel } from "./RacingTypeBadge";
import { machineTypeLabel } from "./machineComposition";
import { savedBuildSetupIssues } from "./buildForm";
import { LibraryIcon } from "./icons";
import { CompareToggle } from "./BuildComparison";
import { SaveBuildButton } from "./SavedBuilds";

const srcGadgetBuilderUrl = "https://www.srcgadgetbuilder.com/";
const sonicFandomCrossWorldsUrl = "https://sonic.fandom.com/wiki/Sonic_Racing:_CrossWorlds";
const japaneseCrossWorldsWikiUrl = "https://w.atwiki.jp/sonicracingcw/";

export function SiteFooter() {
  return (
    <footer>
      <span className="brand footer-brand">
        RING<span>LAB</span>
      </span>
      <span className="footer-project">A CrossWorlds community build lab · Educational project</span>
      <span className="footer-thanks">
        With thanks to <strong>Meohong</strong> — <a href={srcGadgetBuilderUrl} target="_blank" rel="noreferrer">SRC Gadget Builder</a>
      </span>
      <details className="footer-credits">
        <summary>Data sources &amp; credits</summary>
        <div className="footer-credit-content">
          <p>
            Special thanks to Meohong, creator of <a href={srcGadgetBuilderUrl} target="_blank" rel="noreferrer">SRC Gadget Builder</a>, for explaining character and machine stat calculations, clarifying how gadget effects are handled, and sharing guidance on data sources.
          </p>
          <p>
            RingLab&apos;s catalog and artwork references include the <a href={sonicFandomCrossWorldsUrl} target="_blank" rel="noreferrer">Sonic Fandom Wiki — Sonic Racing: CrossWorlds</a>.
          </p>
          <p>
            Community reference: <a href={japaneseCrossWorldsWikiUrl} target="_blank" rel="noreferrer">Japanese Sonic Racing: CrossWorlds Wiki</a>.
          </p>
        </div>
      </details>
      <Link className="footer-collection-link" to="/game-data"><LibraryIcon /> Game collection ↗</Link>
    </footer>
  );
}

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
  const imageUrl = item.imagePath?.startsWith("/assets/racers/")
    ? `${item.imagePath}?v=left-facing-artwork`
    : item.imagePath;
  const racingType = "racingType" in item ? item.racingType ?? "unknown" : "gadget";
  return (
    <div
      className={`artwork ${compact ? "compact" : ""} ${portrait ? "portrait" : ""} type-${racingType.toLowerCase()}`}
    >
      {local && !failed ? (
        <img
          src={imageUrl!}
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
      from === "/my-builds" || from.startsWith("/my-builds?") ||
      from === "/saved-builds" || from.startsWith("/saved-builds?"))
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

export function BuildCard({ build, versions = [], stats, rank, pageStats }: {
  build: Build; versions?: GameVersion[]; stats?: BuildStatsResult; rank?: number; pageStats?: PageCardStats;
}) {
  const location = useLocation();
  const versionAge = patchAge(build.gameVersion, versions);
  const setupIssues = savedBuildSetupIssues(build);
  return (
    <article className="build-card">
      <div className="card-art">
        {rank != null && <span className="top-community-rank" aria-label={`Rank ${rank}`}>#{rank}</span>}
        <Artwork item={build.racer} portrait />
        <div className="card-art-heading">
          <RacingTypeBadge kind="racer" type={build.racer.racingType} />
          <span className="score" aria-label={`Community score ${build.score}, ${countLabel(build.upvotes, "upvote")}, ${countLabel(build.downvotes, "downvote")}`}>
            <span className="community-score">Score {build.score}</span>
            <span className="score-help" tabIndex={0} aria-label="How Best rated works">
              <span aria-hidden="true">i</span>
              <span role="tooltip">Best rated uses Wilson vote confidence. Exact ties favor newer patches, then fewer downvotes at zero confidence, then newer submissions.</span>
            </span>
            <span className="upvote-count">↑ {build.upvotes}</span>
            <span className="downvote-count">↓ {build.downvotes}</span>
          </span>
        </div>
        <div className="card-equipment">
          <RacingTypeBadge kind="machine" type={build.frontPart.racingType} className="type-badge machine-type" />
          <span className="card-parts" aria-label="Machine parts">
            <BuildPartIcon part={build.frontPart} label="Front" abbreviation="F" />
            <BuildPartIcon part={build.rearPart} label="Rear" abbreviation="R" />
            {build.tirePart && <BuildPartIcon part={build.tirePart} label="Tires" abbreviation="T" />}
          </span>
        </div>
      </div>
      <div className="card-body">
        <div className="card-kicker">
          <span className="eyebrow">{build.racer.name}</span>
          {setupIssues.length > 0 && <span className="invalid-setup-badge"
            aria-label={`Invalid setup: ${setupIssues.join(" ")}`}>INVALID SETUP</span>}
          <div className="card-actions">
            <CompareToggle build={build} />
            <SaveBuildButton build={build} />
          </div>
        </div>
        <h2><Link to={`/builds/${build.id}`} state={{ from: browseOrigin(location.pathname, location.search) }}>{build.title}</Link></h2>
        <CardStats build={build} stats={stats} pageStats={pageStats} />
        <div className="tags">
          {build.gadgets.slice(0, CARD_GADGET_LIMIT).map((g, i) => (
            <BuildGadgetIcon key={`${g.id}-${i}`} gadget={g} />
          ))}
          {build.gadgets.length > CARD_GADGET_LIMIT && <span>+{build.gadgets.length - CARD_GADGET_LIMIT}</span>}
          {build.gadgets.length === 0 && <span>No gadgets</span>}
        </div>
        <div className="card-meta">
          <span title={`@${build.author.username}`}>@{build.author.username}</span>
          {build.gameVersion ? (
            <span className={`patch-badge patch-${versionAge}`}>
              Ver. {build.gameVersion.version}
            </span>
          ) : (
            <span className="patch-badge patch-unspecified">Patch unspecified</span>
          )}
          <time dateTime={build.createdAt}>{date(build.createdAt)}</time>
        </div>
      </div>
    </article>
  );
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
