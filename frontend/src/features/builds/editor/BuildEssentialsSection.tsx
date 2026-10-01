import { ErrorNotice } from "../../../shared/ui/ErrorNotice";
import type { BuildDraft, GameVersion } from "../../../shared/types";

export type BuildEssentialsErrors = { title?: string; description?: string; gameVersionId?: string };

export function BuildEssentialsSection({ draft, versions, versionsLoading, fieldErrors, onChange }: {
  draft: BuildDraft; versions?: GameVersion[]; versionsLoading: boolean; fieldErrors: BuildEssentialsErrors;
  onChange: <K extends "title" | "description" | "gameVersionId">(key: K, value: BuildDraft[K]) => void;
}) {
  return (
    <section className="panel">
      <h2>
        <span className="step">01</span> The essentials
      </h2>
      <label>
        Game version / Patch
        <select value={draft.gameVersionId ?? ""}
          required
          className={!draft.gameVersionId ? "invalid-select" : undefined}
          aria-invalid={!draft.gameVersionId}
          aria-describedby={!draft.gameVersionId ? "build-version-error" : undefined}
          disabled={versionsLoading}
          onChange={(e) => onChange("gameVersionId", e.target.value || null)}>
          <option value="" disabled>Select a patch</option>
          {versions?.map((version) => (
            <option key={version.id} value={version.id}>Ver. {version.version}</option>
          ))}
        </select>
      </label>
      {!draft.gameVersionId && (
        <p id="build-version-error" className="field-warning" role="alert">
          {fieldErrors.gameVersionId ?? "A game version / patch is required."}
        </p>
      )}
      <label>
        Build title
        <input
          required
          maxLength={120}
          value={draft.title}
          aria-invalid={!!fieldErrors.title}
          aria-describedby={fieldErrors.title ? "build-title-error" : undefined}
          onChange={(e) => onChange("title", e.target.value)}
          placeholder="Give your setup a name"
        />
      </label>
      <div id="build-title-error"><ErrorNotice message={fieldErrors.title ?? ""} /></div>
      <label>
        Description
        <textarea
          maxLength={10000}
          rows={6}
          value={draft.description}
          aria-invalid={!!fieldErrors.description}
          aria-describedby={fieldErrors.description ? "build-description-error" : undefined}
          onChange={(e) => onChange("description", e.target.value)}
          placeholder="What makes this combination work for you?"
        />
      </label>
      <div id="build-description-error"><ErrorNotice message={fieldErrors.description ?? ""} /></div>
    </section>
  );
}
