import { useEffect, useState } from "react";
import { api } from "./api";
import { BuildCard } from "./components";
import type { GameVersion, TopCommunitySnapshot } from "./types";

export function topCommunitySummary(snapshot: TopCommunitySnapshot) {
  const text = (value: string) => value.replace(/[\r\n\t]/g, " ");
  return ["Top 3 community builds · Overall best rated · All patches",
    ...snapshot.items.map(({ rank, build, buildUrl }) =>
      `#${rank} ${text(build.title)} — ${text(build.author.username)}\n↑ ${build.upvotes} · ↓ ${build.downvotes} · Score ${build.score}\n${buildUrl}`),
  ].join("\n\n");
}

export function TopCommunityBuilds({ versions = [] }: { versions?: GameVersion[] }) {
  const [snapshot, setSnapshot] = useState<TopCommunitySnapshot>();
  const [refresh, setRefresh] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [copyFeedback, setCopyFeedback] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");
    setCopyFeedback("");
    api<TopCommunitySnapshot>("/community/top-builds", {
      signal: controller.signal, anonymous: true, credentials: "omit", cache: "no-cache",
    }).then(value => {
      if (!controller.signal.aborted) setSnapshot(value);
    }).catch(() => {
      if (!controller.signal.aborted) setError("Couldn’t refresh top community builds. Please retry.");
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false);
    });
    return () => controller.abort();
  }, [refresh]);

  async function copy() {
    if (!snapshot) return;
    try {
      await navigator.clipboard.writeText(topCommunitySummary(snapshot));
      setCopyFeedback("Top builds copied to clipboard.");
    } catch {
      setCopyFeedback("Couldn’t copy. Clipboard access may be unavailable; try again.");
    }
  }

  return <section className="top-community" aria-labelledby="top-community-heading">
    <div className="section-heading">
      <div className="top-community-title-line">
        <h2 id="top-community-heading">Top 3 community builds</h2>
        <span>Overall best rated · All patches</span>
        {snapshot && <small>Updated <time dateTime={snapshot.snapshotAt}>
          {new Date(snapshot.snapshotAt).toLocaleString()}
        </time></small>}
      </div>
      <div className="top-community-actions">
        <button type="button" disabled={loading} onClick={() => setRefresh(value => value + 1)}>
          {error ? "Retry top builds" : "Refresh top builds"}
        </button>
        <button type="button" disabled={!snapshot?.items.length} onClick={copy}>Copy top 3</button>
      </div>
    </div>
    {loading && <p role="status">Loading top community builds…</p>}
    {error && <p role="alert">{error}{snapshot && " Showing the last loaded snapshot."}</p>}
    <p role="status" aria-live="polite">{copyFeedback}</p>
    {snapshot && (snapshot.items.length ? <ol className="top-community-grid">
      {snapshot.items.map(item => <li key={item.build.id}>
        <BuildCard build={item.build} versions={versions} stats={item.stats} rank={item.rank} />
      </li>)}
    </ol> : <p>No eligible community builds yet.</p>)}
  </section>;
}
