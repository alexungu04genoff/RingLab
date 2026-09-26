import { useEffect, useState } from "react";
import { api } from "../api";
import type { Build, BuildDraft } from "../types";

const emptyDraft = (): BuildDraft => ({
  title: "", description: "", racerId: "", frontPartId: "", rearPartId: "", tirePartId: null,
  machineType: null, gameVersionId: null, remixedFromBuildId: null, gadgetIds: [],
});

function draftFromBuild(build: Build): BuildDraft {
  return {
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
  };
}

export function draftFromRemix(build: Build): BuildDraft {
  return { ...draftFromBuild(build), title: `Remix of ${build.title}`, remixedFromBuildId: build.id };
}

/** Loads an owned edit or a public remix; route changes always start a fresh draft. */
export function useBuildDraft({ id, remixSourceId, userId, newestVersionId }: {
  id?: string;
  remixSourceId: string | null;
  userId?: string;
  newestVersionId?: string;
}) {
  const [draft, setDraft] = useState<BuildDraft>(emptyDraft);
  const [loadedBuild, setLoadedBuild] = useState<{ id: string; authorId: string }>();
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
        setLoadedBuild({ id: build.id, authorId: build.author.id });
        setDraft(id ? draftFromBuild(build) : draftFromRemix(build));
      })
      .catch((error: Error) => {
        if (!controller.signal.aborted) setLoadError(error.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [id, remixSourceId, userId]);

  useEffect(() => {
    if (id || remixSourceId || !newestVersionId) return;
    setDraft((current) => current.gameVersionId
      ? current : { ...current, gameVersionId: newestVersionId });
  }, [id, remixSourceId, newestVersionId, userId]);

  const canSubmit = !loading && !loadError
    && (!id || (loadedBuild?.id === id && loadedBuild.authorId === userId));
  return { draft, setDraft, loading, loadError, canSubmit };
}
