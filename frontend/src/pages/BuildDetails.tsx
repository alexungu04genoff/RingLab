import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api, json } from "../api";
import { useAuth } from "../auth";
import { hasNextCommentPage, lastCommentPage } from "../commentPagination";
import { Artwork, date, ErrorNotice } from "../components";
import { useLoad } from "../useLoad";
import type { Build, CommentPage, Vote } from "../types";

const COMMENT_PAGE_SIZE = 20;

export function BuildDetails() {
  const { id } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const build = useLoad<Build>(`/builds/${id}`);
  const [revision, setRevision] = useState(0);
  const [page, setPage] = useState(0);
  const comments = useLoad<CommentPage>(
    `/builds/${id}/comments?page=${page}&size=${COMMENT_PAGE_SIZE}`,
    revision,
  );
  const [vote, setVote] = useState<Vote>();
  const [displayedScore, setDisplayedScore] = useState<number>();
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
  useEffect(() => {
    if (b) setDisplayedScore(b.score);
  }, [b?.id, b?.score]);
  if (!b)
    return (
      <>
        <ErrorNotice message={build.error} />
        {build.loading && <p role="status">Loading build…</p>}
      </>
    );
  return (
    <>
      <Link className="back" to="/">
        ← Explore builds
      </Link>
      <ErrorNotice message={error} />
      <div className="detail-heading">
        <div>
          <div className="eyebrow accent">COMMUNITY BUILD</div>
          <h1>{b.title}</h1>
          <p>
            by <strong>@{b.author.username}</strong>{" "}
            <span className="muted">· {date(b.createdAt)}</span>
          </p>
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
            <section className="panel loadout-item">
              <Artwork item={b.racer} />
              <div>
                <div className="eyebrow">RACER</div>
                <h2>{b.racer.name}</h2>
                <span className="type-badge inline">{b.racer.racingType ?? "Unknown"}</span>
              </div>
            </section>
            <section className="panel loadout-item">
              <div>
                <h2>Machine setup</h2>
                <dl>
                  <dt>Front</dt><dd>{b.frontPart.sourceMachineName}</dd>
                  <dt>Rear</dt><dd>{b.rearPart.sourceMachineName}</dd>
                  <dt>Tires</dt><dd>{b.tirePart.sourceMachineName}</dd>
                </dl>
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
            <h2>
              Gadgets <span className="muted">· {b.gadgets.length}</span>
            </h2>
            {b.gadgets.length ? (
              <ol className="detail-gadgets">
                {b.gadgets.map((g, i) => (
                  <li key={`${g.id}-${i}`}>
                    <span className="gadget-number">
                      {String(i + 1).padStart(2, "0")}
                    </span>
                    <span>{g.name}</span>
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
          {b.gameVersion && (
            <section className="panel">
              <div className="eyebrow">Game version</div>
              <strong>Ver. {b.gameVersion.version}</strong>
              <p className="muted">Released {date(`${b.gameVersion.releasedAt}T00:00:00`)}</p>
            </section>
          )}
          <section className="panel vote-panel">
            <div className="eyebrow">COMMUNITY SCORE</div>
            <strong className="big-score" aria-live="polite">
              {displayedScore ?? b.score}
            </strong>
            <div className="vote-buttons">
              {[1, -1].map((v) => (
                <button
                  key={v}
                  className={vote?.myVote === v ? "selected" : ""}
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
                        setDisplayedScore(updatedVote.score);
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
            <span className="eyebrow">BUILT FOR CROSSWORLDS</span>
            <p>
              Community combinations. No calculated stats or gadget
              compatibility checks.
            </p>
          </div>
        </aside>
      </div>
    </>
  );
}
