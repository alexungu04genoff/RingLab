import { useEffect, useState } from "react";
import { api, currentSessionGeneration } from "../../shared/api/api";
import type { Build, BuildDraft } from "../../shared/types";

const emptyDraft = (): BuildDraft => ({
  visibility: "PUBLIC",
  title: "", description: "", racerId: "", frontPartId: "", rearPartId: "", tirePartId: null,
  machineType: null, gameVersionId: null, remixedFromBuildId: null, gadgetIds: [],
  recommendedMapIds: [], mapRecommendationMode: "ALL",
});

function draftFromBuild(build: Build): BuildDraft {
  return {
    // An incomplete/legacy edit response must never silently publish a private build.
    visibility: build.visibility ?? "PRIVATE",
    expectedUpdatedAt: build.updatedAt,
    title: build.title,
    description: build.description,
    racerId: build.racer.id,
    frontPartId: build.frontPart.id,
    rearPartId: build.rearPart.id,
    tirePartId: build.tirePart?.id ?? null,
    machineType: build.frontPart.racingType,
    gameVersionId: build.gameVersion?.id ?? null,
    remixedFromBuildId: build.remixedFrom?.id ?? null,
    gadgetIds: build.gadgets.map((gadget) => gadget.id),
    recommendedMapIds: build.mapRecommendations.maps.map(map => map.id),
    mapRecommendationMode: build.mapRecommendations.mode,
  };
}

export function draftFromRemix(build: Build): BuildDraft {
  return { ...draftFromBuild(build), visibility: "PUBLIC", expectedUpdatedAt: undefined,
    title: `Remix of ${build.title}`, remixedFromBuildId: build.id };
}

/** Loads an owned edit or an accessible remix; route changes always start a fresh draft. */
export function useBuildDraft({ id, remixSourceId, userId }: {
  id?: string;
  remixSourceId: string | null;
  userId?: string;
}) {
  const [draft, setDraft] = useState<BuildDraft>(emptyDraft);
  const generation = currentSessionGeneration();
  const [loadedBuild, setLoadedBuild] = useState<Build>();
  const [loading, setLoading] = useState(!!(id || remixSourceId));
  const [loadError, setLoadError] = useState("");

  useEffect(() => {
    const sourceId = id || remixSourceId;
    setDraft(emptyDraft());
    setLoadedBuild(undefined);
    setLoading(!!sourceId);
    setLoadError("");
    if (!sourceId) return;

    const controller = new AbortController();
    api<Build>(`/builds/${sourceId}`, { signal: controller.signal })
      .then((build) => {
        if (controller.signal.aborted) return;
        if (id && build.author.id !== userId) {
          setLoadError("Only the author may edit this build.");
          return;
        }
        setLoadedBuild(build);
        setDraft(id ? draftFromBuild(build) : draftFromRemix(build));
      })
      .catch((error: Error) => {
        if (!controller.signal.aborted) setLoadError(error.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [id, remixSourceId, userId, generation]);

  const canSubmit = !loading && !loadError
    && (!id || (loadedBuild?.id === id && loadedBuild.author.id === userId));
  return { draft, setDraft, loading, loadError, canSubmit, originalVisibility: loadedBuild?.visibility ?? "PRIVATE" };
}
