import { useEffect, useRef } from "react";
import type { BuildVisibility } from "../../shared/types";

export function VisibilityConfirmation({ visibility, busy, onConfirm, onCancel }: {
  visibility: BuildVisibility; busy: boolean; onConfirm: () => void; onCancel: () => void;
}) {
  const panel = useRef<HTMLElement>(null);
  useEffect(() => { panel.current?.focus(); }, []);
  return <section ref={panel} tabIndex={-1} className="confirm panel" role="alert" aria-label="Confirm visibility change">
    <h2>{visibility === "PUBLIC" ? "Publish this build?" : "Make this build private?"}</h2>
    {visibility === "PUBLIC" ? <>
      <p>It will be eligible for public Explore, search and rankings. Other users may vote, comment, save and remix it.</p>
      <p>Unowned items are allowed. Unsupported calculation coverage will still be shown.</p>
    </> : <>
      <p>This removes the build from RingLab’s public pages. Votes, comments and bookmarks are hidden and inactive; existing engagement is retained.</p>
      <p>Copies already viewed or shared outside RingLab cannot be recalled.</p>
    </>}
    <button type="button" className="primary" disabled={busy} onClick={onConfirm}>
      {visibility === "PUBLIC" ? "Confirm publish" : "Confirm make private"}
    </button>{" "}
    <button type="button" disabled={busy} onClick={onCancel}>Cancel</button>
  </section>;
}
