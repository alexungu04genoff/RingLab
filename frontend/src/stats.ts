import type { BuildDraft } from "./types";

export const statNames = ["speed", "acceleration", "handling", "power", "boost"] as const;
export type StatName = typeof statNames[number];

export function buildStatsPath(draft: Pick<BuildDraft,
  "gameVersionId" | "racerId" | "frontPartId" | "rearPartId" | "tirePartId">) {
  if (!draft.gameVersionId) return "";
  const params = new URLSearchParams();
  for (const key of ["gameVersionId", "racerId", "frontPartId", "rearPartId", "tirePartId"] as const) {
    if (draft[key]) params.set(key, draft[key]);
  }
  return `/stats/build?${params}`;
}
