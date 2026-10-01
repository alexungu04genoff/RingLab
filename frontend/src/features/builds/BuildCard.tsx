import { Link, useLocation } from "react-router-dom";
import { MissingItems } from "../collection/Collection";
import { CollectionArtwork as Artwork } from "../collection/CollectionArtwork";
import { RacingTypeBadge, racingTypeClass, racingTypeLabel } from "../../shared/ui/RacingTypeBadge";
import { date, countLabel } from "../../shared/lib/format";
import { useLoad } from "../../shared/hooks/useLoad";
import { adjustmentSummary, coverageLabel, GadgetAdjustmentBadges } from "../stats/PassiveStats";
import { persistedStatsPath } from "../stats/stats";
import { CardStats, type PageCardStats } from "../stats/CardStats";
import { savedBuildSetupIssues } from "./buildForm";
import { browseOrigin } from "./buildNavigation";
import { CompareToggle } from "./BuildComparison";
import { SaveBuildButton } from "../saved-builds/SavedBuilds";
import { MapRecommendationControl } from "../maps/MapRecommendations";
import type { Build, BuildStatsResult, Gadget, GameVersion, MachinePart, PassiveStatsResult } from "../../shared/types";

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

export function BuildGadgetIcon({ gadget, passive }: { gadget: Gadget; passive?: PassiveStatsResult }) {
  const supported = passive && !["UNSUPPORTED_VERSION", "INVALID_LOADOUT"].includes(passive.coverage);
  const applied = supported ? passive.effects.filter(effect => effect.gadgetId === gadget.id && effect.status === "APPLIED") : [];
  const appliedText = applied.map(effect => adjustmentSummary(effect.adjustment)).join("; ");
  const tooltip = `${gadget.name}${appliedText ? `: ${appliedText}` : gadget.description ? `: ${gadget.description}` : ""}`;
  return (
    <span className="card-gadget-icon" tabIndex={0} aria-label={tooltip}>
      <Artwork item={gadget} compact />
      <span role="tooltip">
        <strong>{gadget.name}</strong>
        {applied.length > 0 && <><span>Applied to this setup</span><GadgetAdjustmentBadges gadgetId={gadget.id} value={passive} /></>}
        {passive && (!supported || passive.coverage === "PARTIAL") && <span>{coverageLabel(passive)}</span>}
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
  const requestedStats = useLoad<BuildStatsResult>(!pageStats && !stats ? persistedStatsPath(build) : "");
  const resolvedPageStats: PageCardStats | undefined = pageStats ?? (stats ? undefined
    : requestedStats.loading ? {status:"pending"}
      : requestedStats.data ? {status:"ready",value:requestedStats.data} : {status:"failed"});
  const passive = resolvedPageStats ? (resolvedPageStats.status === "ready" ? resolvedPageStats.value.passive : undefined) : stats?.passive;
  return (
    <article className="build-card">
      <MissingItems build={build} />
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
          <div className="card-machine-badges">
          <MapRecommendationControl recommendations={build.mapRecommendations} title={build.title} />
          <RacingTypeBadge kind="machine" type={build.frontPart.racingType} className="type-badge machine-type" />
          </div>
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
        <CardStats build={build} stats={stats} pageStats={resolvedPageStats} />
        <div className="tags">
          {build.gadgets.slice(0, CARD_GADGET_LIMIT).map((g, i) => (
            <BuildGadgetIcon key={`${g.id}-${i}`} gadget={g} passive={passive} />
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
