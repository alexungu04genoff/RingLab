import { useEffect, useRef, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { api, json } from "../api";
import { useAuth } from "../auth";
import { COMMENT_PAGE_SIZE, hasNextCommentPage, lastCommentPage } from "../commentPagination";
import { Artwork, buildDetailsOrigin, BuildGadgetIcon, countLabel, date, ErrorNotice, MachineSetup, patchAge, racingTypeClass } from "../components";
import { BuildStats } from "../BaseStats";
import { gadgetPlateStatus, savedBuildSetupIssues } from "../buildForm";
import { formatBuildForSharing } from "../buildSharing";
import { useLoad } from "../useLoad";
import type { Build, CommentPage, GameVersion, Vote } from "../types";


export function BuildDetails() {
  const { id } = useParams();
  return <BuildDetailsContent key={id} />;
}

// A new build gets fresh local state. Pending mutations can only update their old instance.
function BuildDetailsContent() {
  const { id } = useParams();
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);
  const { user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const origin = buildDetailsOrigin(location.state?.from);
  const build = useLoad<Build>(`/builds/${id}`);
  const versions = useLoad<GameVersion[]>("/game-versions");
  const [revision, setRevision] = useState(0);
  const [page, setPage] = useState(0);
  const comments = useLoad<CommentPage>(
    `/builds/${id}/comments?page=${page}&size=${COMMENT_PAGE_SIZE}`,
    revision,
  );
  const [vote, setVote] = useState<Vote>();
  const [text, setText] = useState("");
  const [error, setError] = useState("");
  const [commentError, setCommentError] = useState("");
  const [busy, setBusy] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [copyStatus, setCopyStatus] = useState<"idle" | "copied" | "error">("idle");
  useEffect(() => {
    setVote(undefined);
    if (!user) return;
    const controller = new AbortController();
    api<Vote>(`/builds/${id}/vote`, { signal: controller.signal })
      .then(setVote)
      .catch((e) => {
        if (!controller.signal.aborted) setError(e.message);
      });
    return () => controller.abort();
  }, [id, user]);
  async function act(task: () => Promise<void>, reportError = setError) {
    setBusy(true);
    reportError("");
    try {
      await task();
    } catch (e) {
      reportError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  const b = build.data;
  const summary = vote ?? b;
  if (!b)
    return (
      <>
        <ErrorNotice message={build.error} />
        {build.loading && <p role="status">Loading build…</p>}
      </>
    );
  const plateStatus = gadgetPlateStatus(b.gadgets);
  const setupIssues = savedBuildSetupIssues(b);
  const versionAge = patchAge(b.gameVersion, versions.data || []);
  const shareBuild = b;
  const shareVoteSummary = vote ?? shareBuild;
  async function copySetup() {
    try {
      if (!navigator.clipboard) throw new Error("Clipboard is unavailable");
      await navigator.clipboard.writeText(formatBuildForSharing({
        ...shareBuild,
        upvotes: shareVoteSummary.upvotes,
        downvotes: shareVoteSummary.downvotes,
      }, window.location.href));
      setCopyStatus("copied");
    } catch {
      setCopyStatus("error");
    }
    window.setTimeout(() => setCopyStatus("idle"), 2500);
  }
  return (
    <>
      <div className="build-details">
        <ErrorNotice message={error} />
        <div className="detail-heading">
        <div>
          <div className="detail-context">
            <div className="eyebrow accent">COMMUNITY BUILD</div>
            <Link className="detail-back" to={origin}>
              ← {origin.startsWith("/my-builds") ? "My builds" : "Explore builds"}
            </Link>
          </div>
          <h1>{b.title}</h1>
          <p>
            by <strong>@{b.author.username}</strong>{" "}
            <span className="muted">· {date(b.createdAt)}</span>
          </p>
          {b.remixedFrom && (
            <p className="muted">
              Remixed from <Link to={`/builds/${b.remixedFrom.id}`}>{b.remixedFrom.title}</Link>
            </p>
          )}
          {setupIssues.length > 0 && <div className="invalid-setup-notice" role="note">
            <strong>INVALID SETUP</strong>
            <span>{setupIssues.join(" ")}</span>
          </div>}
        </div>
        <div className="actions">
          {user ? (
            <Link className="button" to={`/builds/new?remixFrom=${encodeURIComponent(b.id)}`}>
              Remix this build
            </Link>
          ) : (
            <Link className="button" to="/login" state={{ from: `/builds/${b.id}` }}>
              Log in to remix
            </Link>
          )}
          <Link className="button" to={`/compare?left=${encodeURIComponent(b.id)}`}>
            Compare
          </Link>
          <button onClick={copySetup}>
            {copyStatus === "copied" ? "✓ Copied" : copyStatus === "error" ? "Could not copy setup" : "Copy setup"}
          </button>
          {user?.id === b.author.id && (
            <>
            <Link className="button" to={`/builds/${id}/edit`}>
              Edit build
            </Link>
            <button className="danger" onClick={() => setConfirmDelete(true)}>
              Delete
            </button>
            </>
          )}
          <span className="sr-only" aria-live="polite">
            {copyStatus === "copied" ? "Setup copied to clipboard" : copyStatus === "error" ? "Could not copy setup" : ""}
          </span>
        </div>
        </div>
        {confirmDelete && (
        <div className="confirm panel" role="alert">
          <p>Delete this build and all its votes and comments?</p>
          <button
            className="danger"
            disabled={busy}
            onClick={() =>
              act(async () => {
                await api(`/builds/${id}`, json("DELETE"));
                if (mounted.current) navigate("/my-builds");
              })
            }
          >
            Yes, delete build
          </button>{" "}
          <button onClick={() => setConfirmDelete(false)}>Cancel</button>
        </div>
        )}
        <div className="detail-grid">
        <div>
          <div className="loadout detail-loadout">
            <section className="panel loadout-item racer-loadout">
              <div className="racer-banner">
                <Artwork item={b.racer} portrait />
                <div className="racer-identity">
                  <div className="racer-nameplate">
                    <div className="eyebrow">RACER</div>
                    <h2>{b.racer.name}</h2>
                  </div>
                  <span className={`type-badge inline racing-type ${racingTypeClass(b.racer.racingType)}`}>
                    {b.racer.racingType ?? "Unknown"}
                  </span>
                </div>
              </div>
              <div className="racer-build-summary">
                <div className="hero-details">
                  <section className="vote-panel" aria-label="Community score and voting">
                    <div className="eyebrow score-heading">
                      COMMUNITY SCORE
                      <span className="score-help" tabIndex={0} aria-label="How Best rated works">
                        <span aria-hidden="true">i</span>
                        <span role="tooltip">Best rated uses Wilson vote confidence. Exact ties favor newer patches, then fewer downvotes at zero confidence, then newer submissions.</span>
                      </span>
                    </div>
                    <strong className="big-score" aria-live="polite">
                      {summary?.score}
                    </strong>
                    <p
                      className="muted vote-summary"
                      aria-live="polite"
                      aria-label={`Vote breakdown: ${countLabel(summary?.upvotes ?? 0, "upvote")}, ${countLabel(summary?.downvotes ?? 0, "downvote")}`}
                    >
                      <span className="upvote-count">↑ {summary?.upvotes}</span>
                      <span aria-hidden="true"> · </span>
                      <span className="downvote-count">↓ {summary?.downvotes}</span>
                    </p>
                    <div className="vote-buttons">
                      {[1, -1].map((v) => (
                        <button
                          key={v}
                          className={`${v === 1 ? "upvote-action" : "downvote-action"}${vote?.myVote === v ? " selected" : ""}`}
                          aria-pressed={vote?.myVote === v}
                          disabled={!user || busy || !vote}
                          onClick={() =>
                            act(async () =>
                              {
                                const updatedVote = await api<Vote>(
                                  `/builds/${id}/vote`,
                                  json(
                                    vote?.myVote === v ? "DELETE" : "PUT",
                                    vote?.myVote === v ? undefined : { value: v },
                                  ),
                                );
                                setVote(updatedVote);
                              }
                            )
                          }
                        >
                          {v === 1 ? "↑ Upvote" : "↓ Downvote"}
                        </button>
                      ))}
                    </div>
                    {user ? (
                      <p className="muted">Click your active vote to remove it.</p>
                    ) : (
                      <p>
                        <Link to="/login" state={{ from: `/builds/${b.id}` }}>
                          Log in
                        </Link>{" "}
                        to vote.
                      </p>
                    )}
                    <div className="score-description">
                      <div className="eyebrow">BUILD NOTE</div>
                      <p>{b.description || "No description provided."}</p>
                      {b.description && <a href="#build-setup">Read full setup</a>}
                    </div>
                  </section>
                  <div className="hero-stats">
                    <BuildStats build={b} />
                  </div>
                  <div className="hero-context">
                    <section className="hero-gadgets" aria-label="Selected gadgets">
                      <div className="eyebrow">GADGETS</div>
                      <div className="tags">
                        {b.gadgets.map((g, i) => (
                          <BuildGadgetIcon key={`${g.id}-${i}`} gadget={g} />
                        ))}
                        {b.gadgets.length === 0 && <span>No gadgets</span>}
                      </div>
                    </section>
                    <section className="hero-machine-setup" aria-label="Machine setup">
                      <MachineSetup build={b} compact />
                    </section>
                  </div>
                  <section className="hero-build-info" aria-label="Build information">
                    <div className="eyebrow">BUILD INFO</div>
                    {b.gameVersion ? (
                      <>
                        <p className="build-version-row">
                          <span className={`detail-version patch-${versionAge}`}>Ver. {b.gameVersion.version}</span>
                          <span className="muted">Released {date(`${b.gameVersion.releasedAt}T00:00:00`)}</span>
                        </p>
                        {versionAge === "latest" && <p className="patch-status">Latest known patch</p>}
                        {versionAge === "older" && (
                          <p className="older-patch-notice">Built for an older patch. Behavior may differ in newer versions.</p>
                        )}
                      </>
                    ) : (
                      <p className="build-version-row">
                        <span className="detail-version patch-unspecified">Patch unspecified</span>
                      </p>
                    )}
                  </section>
                </div>
              </div>
            </section>
          </div>
          <section className="panel" id="build-setup">
            <h2>The setup</h2>
            <p className="prose">
              {b.description || "The author has not added a description."}
            </p>
          </section>
          <section className="panel">
            <div className="gadget-heading">
              <h2>
                Gadgets <span className="muted">· {b.gadgets.length}</span>
              </h2>
              <div className="gadget-status">
                <span
                  className={`gadget-plate-status ${plateStatus.valid ? "valid" : "invalid"}`}
                  aria-label={`Gadget Plate status: ${plateStatus.summary}`}
                >
                  {plateStatus.valid
                    ? `Valid · ${plateStatus.totalCost} / 6 slots`
                    : plateStatus.summary.replace("Gadget Plate · ", "")}
                </span>
                <button className="gadget-info" type="button" aria-label="About Gadget Plate validation">
                  <span aria-hidden="true">i</span>
                  <span role="tooltip">Gadget Plate capacity is validated using current catalog costs. Other gadget compatibility rules are not modeled.</span>
                </button>
              </div>
            </div>
            {b.gadgets.length ? (
              <ol className="detail-gadgets">
                {b.gadgets.map((g, i) => (
                  <li key={`${g.id}-${i}`}>
                    <Artwork item={g} compact />
                    <div className="gadget-copy">
                      <strong>{g.name}</strong>
                      <span className="gadget-cost">{g.slotCost === null
                        ? "Cost unknown"
                        : `${g.slotCost} ${g.slotCost === 1 ? "slot" : "slots"}`}</span>
                      {g.description && <p>{g.description}</p>}
                    </div>
                  </li>
                ))}
              </ol>
            ) : (
              <p className="muted">No gadgets selected.</p>
            )}
          </section>
          <section className="panel comments">
            <h2>Comments</h2>
            <ErrorNotice message={comments.error} />
            {user ? (
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  act(async () => {
                    await api(`/builds/${id}/comments`, json("POST", { text }));
                    setText("");
                    const updatedComments = await api<CommentPage>(
                      `/builds/${id}/comments?page=0&size=${COMMENT_PAGE_SIZE}`,
                    );
                    const lastPage = lastCommentPage(
                      updatedComments.total,
                      updatedComments.size,
                    );
                    if (lastPage === page) setRevision((r) => r + 1);
                    else setPage(lastPage);
                  }, setCommentError);
                }}
              >
                <label>
                  Join the conversation
                  <textarea
                    required
                    maxLength={2000}
                    rows={3}
                    value={text}
                    aria-describedby={commentError ? "comment-error" : undefined}
                    onChange={(e) => {
                      setText(e.target.value);
                      setCommentError("");
                    }}
                    placeholder="Share your thoughts on this setup…"
                  />
                </label>
                <div id="comment-error">
                  <ErrorNotice message={commentError} />
                </div>
                <button className="primary" disabled={busy || !text.trim()}>
                  Post comment
                </button>
              </form>
            ) : (
              <p>
                <Link to="/login" state={{ from: `/builds/${b.id}` }}>
                  Log in
                </Link>{" "}
                to join the conversation.
              </p>
            )}
            {comments.loading && <p role="status">Loading comments…</p>}
            {comments.data?.items.length === 0 && (
              <p className="muted">No comments on this page yet.</p>
            )}
            {comments.data?.items.map((c) => (
              <article className="comment" key={c.id}>
                <div className="comment-meta">
                  <strong>@{c.author}</strong>
                  <time>{date(c.createdAt)}</time>
                  {user?.id === c.authorId && (
                    <button
                      className="text-button"
                      disabled={busy}
                      onClick={() =>
                        act(async () => {
                          await api(`/comments/${c.id}`, json("DELETE"));
                          const lastPage = lastCommentPage(
                            Math.max(0, (comments.data?.total ?? 1) - 1),
                            comments.data?.size ?? COMMENT_PAGE_SIZE,
                          );
                          if (lastPage < page) setPage(lastPage);
                          else setRevision((r) => r + 1);
                        })
                      }
                    >
                      Delete
                    </button>
                  )}
                </div>
                <p className="prose">{c.text}</p>
              </article>
            ))}
            {comments.data &&
              (comments.data.page > 0 || hasNextCommentPage(comments.data)) && (
              <div className="pagination">
                <button
                  disabled={comments.data.page === 0}
                  onClick={() => setPage((p) => p - 1)}
                >
                  Previous
                </button>
                <span>Page {comments.data.page + 1}</span>
                <button
                  disabled={!hasNextCommentPage(comments.data)}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
                </button>
              </div>
              )}
          </section>
        </div>
        </div>
      </div>
    </>
  );
}
