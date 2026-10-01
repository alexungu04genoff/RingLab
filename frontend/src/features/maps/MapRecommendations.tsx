import { useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { CompassIcon } from "../../shared/ui/icons";
import { filterMaps, mapCategoryLabel, orderedMaps } from "./mapSelection";
import { SearchableFilter } from "../../shared/ui/SearchableFilter";
import type { MapRecommendations, RaceMap } from "../../shared/types";

export function MapThumbnail({ map }: { map: RaceMap }) {
  const [failedPath, setFailedPath] = useState<string>();
  const available = map.imagePath?.startsWith("/assets/maps/") && failedPath !== map.imagePath;
  return <span className="map-thumbnail">
    {available ? <img src={map.imagePath!} alt={map.name} loading="lazy"
      onError={() => setFailedPath(map.imagePath!)} />
      : <span role="img" aria-label={`${map.name}: artwork unavailable`} className="map-art-fallback">
        <CompassIcon /><span>Artwork unavailable</span></span>}
  </span>;
}

export function RecommendedMapList({ recommendations }: { recommendations: MapRecommendations }) {
  return recommendations.mode === "ALL"
    ? <div className="all-maps-copy"><strong>All maps</strong><p>No specific maps selected.</p>
      <p className="muted">The author has not supplied a map-specific preference.</p></div>
    : <ul className="recommended-map-list">{orderedMaps(recommendations.maps).map(map => <li key={map.id}>
      <MapThumbnail map={map} /><span><strong>{map.name}</strong><small>{mapCategoryLabel(map.category)}
        {map.contentPack && ` · ${map.contentPack}`}</small></span>
    </li>)}</ul>;
}

function RecommendationDialog({ recommendations, title, onClose, returnFocus }: {
  recommendations: MapRecommendations; title: string; onClose: () => void; returnFocus: HTMLButtonElement | null;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const headingId = useId();
  useEffect(() => {
    const previousOverflow = document.body.style.overflow;
    const element = dialog.current!;
    element.showModal();
    document.body.style.overflow = "hidden";
    return () => {
      element.close();
      document.body.style.overflow = previousOverflow;
      returnFocus?.focus();
    };
  }, [returnFocus]);
  return createPortal(<dialog ref={dialog} className="map-dialog" aria-labelledby={headingId}
    onClick={event => {
      if (event.target !== event.currentTarget) return;
      const bounds = event.currentTarget.getBoundingClientRect();
      if (event.clientX < bounds.left || event.clientX > bounds.right
        || event.clientY < bounds.top || event.clientY > bounds.bottom) onClose();
    }}
    onCancel={event => { event.preventDefault(); onClose(); }}>
    <div className="map-dialog-heading"><h2 id={headingId}>Recommended maps</h2>
      <button type="button" autoFocus onClick={onClose} aria-label="Close recommended maps">×</button></div>
    <p className="muted">{title}</p>
    <div className="map-dialog-scroll"><RecommendedMapList recommendations={recommendations} /></div>
  </dialog>, document.body);
}

export function MapRecommendationControl({ recommendations, title }: {
  recommendations: MapRecommendations; title: string;
}) {
  const [open, setOpen] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);
  const count = recommendations.mode === "ALL" ? "All" : String(recommendations.maps.length);
  return <>
    <button ref={trigger} type="button" className="map-control" aria-haspopup="dialog" aria-expanded={open}
      aria-label={`Recommended maps for ${title}: ${count}`} title="View recommended maps"
      onClick={() => setOpen(true)}><span className="map-control-badge"><CompassIcon /><span>Map {count}</span></span></button>
    {open && <RecommendationDialog recommendations={recommendations} title={title} returnFocus={trigger.current} onClose={() => setOpen(false)} />}
  </>;
}

export function MapRecommendationPicker({ maps, mode, selectedIds, onChange, loading, error }: {
  maps: RaceMap[]; mode: MapRecommendations["mode"]; selectedIds: string[];
  onChange: (mode: MapRecommendations["mode"], ids: string[]) => void;
  loading: boolean; error: string;
}) {
  const [search, setSearch] = useState("");
  const [category, setCategory] = useState("");
  const group = useId();
  const visible = filterMaps(maps, search, category);
  return <section className="panel map-picker" aria-label="Recommended maps">
    <h2>Recommended maps</h2>
    <p>Share where you like to race this setup. This does not change stats or ranking.</p>
    <div className="map-mode-controls">
      <label><input type="radio" name={group} checked={mode === "ALL"}
        onChange={() => onChange("ALL", [])} />All maps</label>
      <label><input type="radio" name={group} checked={mode === "SELECTED"}
        onChange={() => onChange("SELECTED", selectedIds)} />Specific maps</label>
    </div>
    {mode === "ALL" ? <p className="muted">No map-specific preference supplied.</p> : <>
      <p role="status">{selectedIds.length} selected</p>
      <div className="map-picker-filters">
        <label>Search maps<input type="search" value={search} onChange={event => setSearch(event.target.value)} /></label>
        <label>Course category<select value={category} onChange={event => setCategory(event.target.value)}>
          <option value="">All categories</option><option value="MAIN_COURSE">Main courses</option>
          <option value="CROSSWORLD">CrossWorlds</option></select></label>
      </div>
      {loading && <p role="status">Loading maps…</p>}
      {error && <p role="alert">Could not load maps. {error} Your selection is preserved.</p>}
      <div className="map-picker-options">{visible.map(map => <label key={map.id}
        className={`map-picker-option ${selectedIds.includes(map.id) ? "selected" : ""}`}>
        <input type="checkbox" checked={selectedIds.includes(map.id)}
          onChange={() => onChange("SELECTED", selectedIds.includes(map.id)
            ? selectedIds.filter(id => id !== map.id) : [...selectedIds, map.id])} />
        <MapThumbnail map={map} /><span><strong>{map.name}</strong><small>{mapCategoryLabel(map.category)}
          {map.contentPack && ` · ${map.contentPack}`}</small></span>
      </label>)}</div>
      {!loading && !error && visible.length === 0 && <p>No maps match your search.</p>}
      {selectedIds.length === 0 && <p className="field-warning">Select at least one map, or choose All maps.</p>}
    </>}
  </section>;
}

export function MapFilter({ maps, mapId, includeAllMaps, onChange }: {
  maps: RaceMap[]; mapId: string; includeAllMaps: boolean;
  onChange: (mapId: string, includeAllMaps: boolean) => void;
}) {
  return <div className="map-filter">
    <SearchableFilter label="Map" icon={<CompassIcon />} allLabel="Any map" value={mapId}
      options={maps.map(map => ({ value: map.id, label: map.name }))}
      onChange={id => onChange(id, id ? includeAllMaps : true)} />
    <label className="map-specific-filter"><input type="checkbox" disabled={!mapId}
      checked={!!mapId && !includeAllMaps} onChange={event => onChange(mapId, !event.target.checked)} />
      Specific recommendations only</label>
  </div>;
}
