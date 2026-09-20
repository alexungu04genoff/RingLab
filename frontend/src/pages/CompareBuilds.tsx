import { useEffect, useId, useRef, useState } from "react";
import type { ReactNode } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { gadgetPlateStatus } from "../buildForm";
import { Artwork, countLabel, date, ErrorNotice, isStockSetup, MachineSetup, racingTypeClass } from "../components";
import { useLoad } from "../useLoad";
import { BuildStats } from "../BaseStats";
import type { Build, BuildPage, Gadget } from "../types";

const SELECTOR_PAGE_SIZE = 8;

export function compareUrl(leftId: string, rightId: string) {
  const params = new URLSearchParams({ left: leftId, right: rightId });
  return `/compare?${params}`;
}

export function buildDiff(left: Build, right: Build) {
  return {
    racer: left.racer.id !== right.racer.id,
    patch: left.gameVersion?.id !== right.gameVersion?.id,
    front: left.frontPart.id !== right.frontPart.id,
    rear: left.rearPart.id !== right.rearPart.id,
    machineType: left.frontPart.racingType !== right.frontPart.racingType,
    tires: left.tirePart?.id !== right.tirePart?.id,
    composition: isStockSetup(left) !== isStockSetup(right),
    plate: gadgetPlateStatus(left.gadgets).totalCost !== gadgetPlateStatus(right.gadgets).totalCost,
    gadgetAt: (index: number) => left.gadgets[index]?.id !== right.gadgets[index]?.id,
  };
}

function BuildSelector({ leftId }: { leftId: string }) {
  const navigate = useNavigate();
  const listboxId = useId();
  const root = useRef<HTMLDivElement>(null);
  const [query, setQuery] = useState("");
  const [search, setSearch] = useState("");
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(0);
  useEffect(() => {
    const timeout = window.setTimeout(() => setSearch(query.trim()), 250);
    return () => window.clearTimeout(timeout);
  }, [query]);
  const params = new URLSearchParams({ page: "0", size: String(SELECTOR_PAGE_SIZE), sort: "newest" });
  if (search) params.set("search", search);
  const builds = useLoad<BuildPage>(`/builds?${params}`);
  const choices = (builds.data?.items ?? []).filter(({ id }) => id !== leftId);
  const choose = (build: Build) => navigate(compareUrl(leftId, build.id));
  return (
    <section className="panel compare-selector" aria-labelledby="compare-selector-heading">
      <div className="eyebrow">SECOND BUILD</div>
      <h2 id="compare-selector-heading">Choose a build to compare</h2>
      <label>
        Search community builds
        <div className="compare-combobox" ref={root} onBlur={(event) => {
          if (!root.current?.contains(event.relatedTarget)) setOpen(false);
        }}>
          <input type="search" role="combobox" aria-autocomplete="list" aria-controls={listboxId}
            aria-expanded={open} aria-activedescendant={open && choices[activeIndex]
              ? `${listboxId}-option-${activeIndex}` : undefined}
            placeholder="Search by build title" value={query}
            onFocus={() => setOpen(true)} onChange={(event) => {
              setQuery(event.target.value); setOpen(true); setActiveIndex(0);
            }} onKeyDown={(event) => {
              if (event.key === "Escape") return setOpen(false);
              if (event.key === "ArrowDown" || event.key === "ArrowUp") {
                event.preventDefault();
                const direction = event.key === "ArrowDown" ? 1 : -1;
                setActiveIndex((current) => choices.length
                  ? (current + direction + choices.length) % choices.length : 0);
              }
              if (event.key === "Enter" && open && choices[activeIndex]) {
                event.preventDefault(); choose(choices[activeIndex]);
              }
            }} />
          <div id={listboxId} role="listbox" hidden={!open}>
            {choices.map((build, index) => (
              <button type="button" role="option" aria-selected={index === activeIndex}
                id={`${listboxId}-option-${index}`} className={index === activeIndex ? "active" : undefined}
                key={build.id} onMouseDown={(event) => event.preventDefault()}
                onMouseEnter={() => setActiveIndex(index)} onClick={() => choose(build)}>
                <strong>{build.title}</strong>
                <span>@{build.author.username} · {build.racer.name} · {build.gameVersion
                  ? `Ver. ${build.gameVersion.version}` : "Patch unspecified"}</span>
              </button>
            ))}
            {!builds.loading && !builds.error && choices.length === 0 && (
              <span className="no-filter-results">No other builds found.</span>
            )}
          </div>
        </div>
      </label>
      {builds.loading && <p role="status" className="muted">Searching builds…</p>}
      <ErrorNotice message={builds.error} />
      {builds.data && builds.data.total > SELECTOR_PAGE_SIZE && (
        <p className="muted">Showing the newest matches. Refine the title search to find another build.</p>
      )}
    </section>
  );
}

function DiffValue({ different, children, label }: {
  different: boolean; children: ReactNode; label: string;
}) {
  return <div className={different ? "compare-value different" : "compare-value"}
    data-difference={different ? label : undefined}>{children}</div>;
}

function GadgetList({ build, other, side }: { build: Build; other: Build; side: "Left" | "Right" }) {
  return build.gadgets.length ? (
    <ol className="compare-gadgets">
      {build.gadgets.map((gadget: Gadget, index) => (
        <li className={gadget.id !== other.gadgets[index]?.id ? "different" : undefined}
          data-difference={gadget.id !== other.gadgets[index]?.id ? `Gadget ${index + 1}` : undefined}
          key={`${gadget.id}-${index}`}>
          <span className="gadget-number">{index + 1}</span><Artwork item={gadget} compact />
          <span><strong>{gadget.name}</strong><small>{gadget.slotCost === null
            ? "Cost unknown" : `${gadget.slotCost} ${gadget.slotCost === 1 ? "slot" : "slots"}`}</small></span>
        </li>
      ))}
    </ol>
  ) : <p className="muted">No gadgets selected for the {side.toLowerCase()} build.</p>;
}

function BuildColumn({ build, other, side }: { build: Build; other: Build; side: "Left" | "Right" }) {
  const diff = buildDiff(build, other);
  const plate = gadgetPlateStatus(build.gadgets);
  return (
    <article className="compare-column" aria-label={`${side} build: ${build.title}`}>
      <div className="compare-side-label">{side} build</div>
      <section className="panel compare-summary">
        <Artwork item={build.racer} portrait />
        <div><h2>{build.title}</h2><p>by <strong>@{build.author.username}</strong></p>
          <Link to={`/builds/${build.id}`}>Open build details →</Link></div>
      </section>
      <DiffValue different={diff.racer} label="Racer">
        <span className="eyebrow">RACER</span><strong>{build.racer.name}</strong>
        <span className={`type-badge inline racing-type ${racingTypeClass(build.racer.racingType)}`}>
          {build.racer.racingType ?? "Unknown"}</span>
      </DiffValue>
      <DiffValue different={diff.patch} label="Patch"><span className="eyebrow">GAME VERSION / PATCH</span>
        <strong>{build.gameVersion ? `Ver. ${build.gameVersion.version}` : "Unspecified"}</strong></DiffValue>
      <DiffValue different={diff.composition || diff.machineType} label="Machine composition"><MachineSetup build={build} compact
        differences={{ FRONT: diff.front, REAR: diff.rear, TIRE: diff.tires }} /></DiffValue>
      <section className="panel">
        <BuildStats build={build} />
      </section>
      <section className="panel compare-gadget-panel">
        <div className="gadget-heading"><h2>Gadgets</h2>
          <span className={`gadget-plate-status ${plate.valid ? "valid" : "invalid"} ${diff.plate ? "different" : ""}`}
            data-difference={diff.plate ? "Gadget Plate usage" : undefined}> 
            {plate.valid ? `${plate.totalCost} / 6 slots` : plate.summary.replace("Gadget Plate · ", "")}
          </span></div>
        <GadgetList build={build} other={other} side={side} />
      </section>
      <section className="panel compare-meta"><h2>Community & metadata</h2>
        <p aria-label={`${countLabel(build.upvotes, "upvote")}, ${countLabel(build.downvotes, "downvote")}`}>
          <span className="upvote-count">↑ {build.upvotes}</span> · <span className="downvote-count">↓ {build.downvotes}</span>
          <strong> · Net {build.score}</strong></p>
        <p className="muted">Created {date(build.createdAt)}{build.updatedAt !== build.createdAt
          ? ` · Updated ${date(build.updatedAt)}` : ""}</p>
      </section>
    </article>
  );
}

export function CompareBuilds() {
  const [params] = useSearchParams();
  const leftId = params.get("left") ?? "";
  const rightId = params.get("right") ?? "";
  const left = useLoad<Build>(leftId ? `/builds/${leftId}` : "");
  const right = useLoad<Build>(rightId ? `/builds/${rightId}` : "");
  if (!leftId) return <div className="empty"><h1>Choose a build first.</h1>
    <p>Open a build and use its Compare action to start a comparison.</p><Link to="/">Explore builds</Link></div>;
  if (rightId && rightId === leftId) return <div className="empty"><h1>Choose two different builds.</h1>
    <p>A build cannot be compared with itself.</p><Link to={`/compare?left=${encodeURIComponent(leftId)}`}>Choose another build</Link></div>;
  if (!left.data) return <><ErrorNotice message={left.error} />{left.loading && <p role="status">Loading build…</p>}</>;
  return <>
    <Link className="back" to={`/builds/${leftId}`}>← Back to build details</Link>
    <div className="page-heading"><div><div className="eyebrow accent">STRUCTURED COMPARISON</div>
      <h1>Compare builds</h1><p>Compare racers, machine parts, gadgets, patches, and community response.</p></div></div>
    {!rightId && <BuildSelector leftId={leftId} />}
    {rightId && !right.data && <><ErrorNotice message={right.error} />
      {right.loading && <p role="status">Loading comparison…</p>}</>}
    {right.data && <div className="compare-grid"><BuildColumn build={left.data} other={right.data} side="Left" />
      <BuildColumn build={right.data} other={left.data} side="Right" /></div>}
  </>;
}
