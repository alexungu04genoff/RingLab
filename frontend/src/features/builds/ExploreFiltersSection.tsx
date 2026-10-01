import { Link } from "react-router-dom";
import { ComponentsIcon, RacerIcon, SearchIcon, SortIcon, TagIcon } from "../../shared/ui/icons";
import { SearchableFilter } from "../../shared/ui/SearchableFilter";
import { MapFilter } from "../maps/MapRecommendations";
import type { GameVersion, Machine, Racer, RaceMap } from "../../shared/types";
import type { BuildSort } from "./exploreFilters";

export function ExploreFiltersSection({ search, onSearchChange, selection, racers, machines, versions, maps, onChange }: {
  search: string; onSearchChange: (search: string) => void;
  selection: { racer: string; machine: string; gameVersion: string; mapId: string; includeAllMaps: boolean; sort: BuildSort };
  racers: Racer[]; machines: Machine[]; versions: GameVersion[]; maps: RaceMap[];
  onChange: (changes: Record<string, string>) => void;
}) {
  const { racer, machine, gameVersion, mapId, includeAllMaps, sort } = selection;
  return (
    <section className="filters" aria-label="Filter builds">
      <form
        className="search"
        onSubmit={(e) => {
          e.preventDefault();
          onChange({ search });
        }}
      >
        <label>
          <span className="field-label"><SearchIcon /> Search builds</span>
          <input
            placeholder="Search titles, racers, machines, or gadgets…"
            maxLength={120}
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
          />
        </label>
        <button type="submit">Search</button>
      </form>
      <SearchableFilter
        label="Racer"
        icon={<RacerIcon />}
        options={racers.map(({ id, name }) => ({ value: id, label: name }))}
        value={racer}
        allLabel="All racers"
        onChange={(v) => {
          onChange({ racerId: v });
        }}
      />
      <SearchableFilter
        label="Uses parts from"
        icon={<ComponentsIcon />}
        options={machines.map(({ id, name }) => ({ value: id, label: name }))}
        value={machine}
        allLabel="All source machines"
        onChange={(v) => {
          onChange({ machineId: v });
        }}
      />
      <SearchableFilter
        label="Patch"
        icon={<TagIcon />}
        options={versions.map(({ id, version }) => ({
          value: id,
          label: `Ver. ${version}`,
        }))}
        value={gameVersion}
        allLabel="All versions"
        onChange={(value) => {
          onChange({ gameVersionId: value });
        }}
      />
      <MapFilter maps={maps} mapId={mapId} includeAllMaps={includeAllMaps}
        onChange={(id, include) => onChange({ mapId: id, includeAllMaps: id ? String(include) : "" })} />
      <div className="filter-with-help"><label>
        <span className="sort-label"><SortIcon /> Sort by <span className="sort-help" tabIndex={0} aria-label="How Best rated works"><span aria-hidden="true">i</span><span role="tooltip">Best rated uses Wilson vote confidence. Exact ties favor newer patches, then fewer downvotes at zero confidence, then newer submissions.</span></span></span>
        <select
          value={sort}
          onChange={(e) => {
            onChange({ sort: e.target.value as BuildSort });
          }}
        >
          <option value="newest">Newest first</option>
          <option value="score">Highest score</option>
          <option value="rated">Best rated</option>
        </select>
      </label><Link className="contextual-help" to="/guide#finding-builds">Sorting help →</Link></div>
    </section>
  );
}
