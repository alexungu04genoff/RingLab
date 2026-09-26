import type { MapRecommendations, RaceMap } from "./types";

export const allMaps: MapRecommendations = { mode: "ALL", maps: [] };
export const mapCategoryLabel = (category: RaceMap["category"]) =>
  category === "MAIN_COURSE" ? "Main course" : "CrossWorld";

export function orderedMaps(maps: RaceMap[]) {
  return [...maps].sort((a, b) => a.catalogOrder - b.catalogOrder || a.id.localeCompare(b.id));
}

export function filterMaps(maps: RaceMap[], search: string, category = "") {
  const query = search.trim().toLocaleLowerCase();
  return orderedMaps(maps).filter(map => (!category || map.category === category)
    && map.name.toLocaleLowerCase().includes(query));
}

export function mapSummary(recommendations: MapRecommendations, maxNames = 5) {
  if (recommendations.mode === "ALL") return "All maps";
  const maps = orderedMaps(recommendations.maps);
  const extra = maps.length > maxNames ? ` +${maps.length - maxNames} more` : "";
  return maps.slice(0, maxNames).map(map => map.name).join(", ") + extra;
}

/** All is an unspecified preference, never the empty selected set or the entire catalog. */
export function compareMapRecommendations(left: MapRecommendations, right: MapRecommendations) {
  const leftIds = new Set(left.maps.map(map => map.id));
  const rightIds = new Set(right.maps.map(map => map.id));
  const bothSelected = left.mode === "SELECTED" && right.mode === "SELECTED";
  return {
    different: left.mode !== right.mode || (bothSelected &&
      (leftIds.size !== rightIds.size || [...leftIds].some(id => !rightIds.has(id)))),
    common: bothSelected ? orderedMaps(left.maps.filter(map => rightIds.has(map.id))) : [],
    onlyLeft: bothSelected ? orderedMaps(left.maps.filter(map => !rightIds.has(map.id))) : [],
    onlyRight: bothSelected ? orderedMaps(right.maps.filter(map => !leftIds.has(map.id))) : [],
  };
}
