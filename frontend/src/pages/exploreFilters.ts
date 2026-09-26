import type { GameVersion, Machine, Racer } from "../types";

export const PUBLIC_SORT_PREFERENCE = "ringlab.explore.sort";
export const PUBLIC_PATCH_PREFERENCE = "ringlab.explore.gameVersionId";
const SORT_VALUES = ["newest", "score", "rated"] as const;
export type BuildSort = (typeof SORT_VALUES)[number];
type FilterKey = "search" | "racerId" | "machineId" | "gameVersionId" | "sort";
interface ActiveFilterChip { key: FilterKey; label: string }

export function readPreference(key: string) {
  try {
    return typeof localStorage === "undefined" ? null : localStorage.getItem(key);
  } catch {
    return null;
  }
}

function isBuildSort(value: string | null): value is BuildSort {
  return SORT_VALUES.some((sort) => sort === value);
}

export function resolveSort(urlValue: string | null, savedValue: string | null, mine: boolean): BuildSort {
  if (isBuildSort(urlValue)) return urlValue;
  if (!mine && isBuildSort(savedValue)) return savedValue;
  return mine ? "newest" : "rated";
}

export function resolvePage(value: string | null) {
  if (!value || !/^\d+$/.test(value)) return 0;
  const page = Number(value);
  return Number.isSafeInteger(page) ? page : 0;
}

export function resolveGameVersion(
  urlValue: string | null,
  savedValue: string | null,
  knownIds: string[] | undefined,
  mine: boolean,
) {
  if (!knownIds) return "";
  const preferred = urlValue || (mine ? null : savedValue) || "";
  return knownIds.includes(preferred) ? preferred : "";
}

export function updateExploreParams(
  current: URLSearchParams,
  changes: Record<string, string>,
  resetPage = true,
) {
  const next = new URLSearchParams(current);
  Object.entries(changes).forEach(([key, value]) => {
    if (value) next.set(key, value);
    else next.delete(key);
  });
  if (resetPage) next.delete("page");
  return next;
}

export function clearExploreFilters(current: URLSearchParams) {
  const next = new URLSearchParams(current);
  ["search", "racerId", "machineId", "gameVersionId", "page"].forEach((key) => next.delete(key));
  return next;
}

export function activeExploreFilterChips(
  params: URLSearchParams,
  racers: Racer[],
  machines: Machine[],
  versions: GameVersion[],
  mine: boolean,
): ActiveFilterChip[] {
  const chips: ActiveFilterChip[] = [];
  const search = params.get("search");
  const racer = racers.find(({ id }) => id === params.get("racerId"));
  const machine = machines.find(({ id }) => id === params.get("machineId"));
  const version = versions.find(({ id }) => id === params.get("gameVersionId"));
  const sort = params.get("sort");
  if (search) chips.push({ key: "search", label: `Search: ${search}` });
  if (racer) chips.push({ key: "racerId", label: `Racer: ${racer.name}` });
  if (machine) chips.push({ key: "machineId", label: `Parts from: ${machine.name}` });
  if (version) chips.push({ key: "gameVersionId", label: `Patch: Ver. ${version.version}` });
  const defaultSort = mine ? "newest" : "rated";
  if (sort && sort !== defaultSort && isBuildSort(sort)) {
    const labels: Record<BuildSort, string> = {
      newest: "Newest first", score: "Highest score", rated: "Best rated",
    };
    chips.push({ key: "sort", label: `Sort: ${labels[sort]}` });
  }
  return chips;
}
