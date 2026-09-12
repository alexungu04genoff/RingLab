import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { api, json } from "../api";
import { useAuth } from "../auth";
import { hasNextCommentPage, lastCommentPage } from "../commentPagination";
import { Artwork, buildDetailsOrigin, countLabel, date, ErrorNotice, patchAge, racingTypeClass } from "../components";
import { gadgetPlateStatus } from "../buildForm";
import { useLoad } from "../useLoad";
import type { Build, CommentPage, GameVersion, Vote } from "../types";

const COMMENT_PAGE_SIZE = 20;

export function BuildDetails() {
  const { id } = useParams();
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
  const [busy, setBusy] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
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
  async function act(task: () => Promise<void>) {
    setBusy(true);
    setError("");
    try {
      await task();
    } catch (e) {
      setError((e as Error).message);
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
  const stockSetup = b.frontPart.sourceMachineId === b.rearPart.sourceMachineId
    && b.frontPart.sourceMachineId === b.tirePart.sourceMachineId;
  const plateStatus = gadgetPlateStatus(b.gadgets);
  const versionAge = patchAge(b.gameVersion, versions.data || []);
  return (
    <>
      <Link className="back" to={origin}>
        ← {origin.startsWith("/my-builds") ? "My builds" : "Explore builds"}
      </Link>
      <ErrorNotice message={error} />
      <div className="detail-heading">
        <div>
          <div className="eyebrow accent">COMMUNITY BUILD</div>
          <h1>{b.title}</h1>
          <p>
            by <strong>@{b.author.username}</strong>{" "}
            <span className="muted">· {date(b.createdAt)}</span>
            {b.gameVersion && (
              <>
                <span className={`detail-version patch-${versionAge}`}>
                  · Ver. {b.gameVersion.version}
                </span>
                <span className="muted">· Released {date(`${b.gameVersion.releasedAt}T00:00:00`)}</span>
              </>
            )}
          </p>
          {versionAge === "older" && (
            <p className="older-patch-notice">Built for an older patch. Behavior may differ in newer versions.</p>
          )}
        </div>
        {user?.id === b.author.id && (
          <div className="actions">
            <Link className="button" to={`/builds/${id}/edit`}>
              Edit build
            </Link>
            <button className="danger" onClick={() => setConfirmDelete(true)}>
              Delete
            </button>
          </div>
        )}
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
                navigate("/my-builds");
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
          <div className="loadout">
            <section className="panel loadout-item racer-loadout">
              <Artwork item={b.racer} portrait />
              <div>
                <div className="eyebrow">RACER</div>
                <div className="racer-name">
                  <h2>{b.racer.name}</h2>
                  <span className={`type-badge inline racing-type ${racingTypeClass(b.racer.racingType)}`}>
                    {b.racer.racingType ?? "Unknown"}
                  </span>
                </div>
              </div>
            </section>
            <section className="panel loadout-item">
              <div className="machine-setup-heading">
                <div>
                  <div className="eyebrow">MACHINE SETUP</div>
                  <h2>Parts by source machine</h2>
                </div>
                <span className={`setup-indicator ${stockSetup ? "stock-setup" : "mixed-setup"}`}>
                  {stockSetup ? "Stock setup" : "Mixed setup"}
                </span>
              </div>
              <div className="machine-setup-grid">
                {[["FRONT", b.frontPart], ["REAR", b.rearPart], ["TIRES", b.tirePart]]
                  .map(([label, part]) => {
                    const machinePart = part as typeof b.frontPart;
                    return <article className="machine-part-card" key={label as string}>
                      <Artwork item={{ id: machinePart.sourceMachineId, name: machinePart.sourceMachineName,
                        imagePath: machinePart.sourceMachineImagePath, racingType: machinePart.racingType }} compact />
                      <div className="machine-part-copy">
                        <strong>{machinePart.sourceMachineName}</strong>
                        {machinePart.racingType && (
                          <span className={`part-type racing-type ${racingTypeClass(machinePart.racingType)}`}>
                            {machinePart.racingType}
                          </span>
                        )}
                      </div>
                      <span className="eyebrow machine-part-slot">{label as string}</span>
                    </article>;
                  })}
              </div>
            </section>
          </div>
          <section className="panel">
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
              <span
                className={`gadget-plate-status ${plateStatus.valid ? "valid" : "invalid"}`}
                aria-label={`Gadget Plate status: ${plateStatus.summary}`}
              >
                {plateStatus.valid
                  ? `Valid · ${plateStatus.totalCost} / 6 slots`
                  : plateStatus.summary.replace("Gadget Plate · ", "")}
              </span>
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
                  });
                }}
              >
                <label>
                  Join the conversation
                  <textarea
                    required
                    maxLength={2000}
                    rows={3}
                    value={text}
                    onChange={(e) => setText(e.target.value)}
                    placeholder="Share your thoughts on this setup…"
                  />
                </label>
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
        <aside>
          <section className="panel vote-panel">
            <div className="eyebrow score-heading">
              COMMUNITY SCORE
              <span className="score-help" tabIndex={0} aria-label="How Best rated works">
                <span aria-hidden="true">i</span>
                <span role="tooltip">Best rated shows positive-score builds first, then neutral, then negative. Within each group, builds with more consistently positive votes rank higher.</span>
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
          </section>
          <div className="detail-note">
            <span className="eyebrow">VALIDATION</span>
            <p>
              Gadget Plate capacity is validated using current catalog costs. Other gadget compatibility rules are not modeled.
            </p>
          </div>
        </aside>
      </div>
    </>
  );
}
