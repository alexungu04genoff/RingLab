import { useState, type Dispatch, type SetStateAction } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/auth";
import { api, json } from "../../shared/api/api";
import { ErrorNotice } from "../../shared/ui/ErrorNotice";
import { date } from "../../shared/lib/format";
import { COMMENT_PAGE_SIZE, hasNextCommentPage, lastCommentPage } from "./commentPagination";
import type { CommentPage } from "../../shared/types";

export function BuildCommentsSection({ buildId, comments, page, onPageChange, onRefresh, busy, act }: {
  buildId: string; comments: { data?: CommentPage; error: string; loading: boolean }; page: number;
  onPageChange: Dispatch<SetStateAction<number>>; onRefresh: () => void; busy: boolean;
  act: (task: () => Promise<void>, reportError?: (message: string) => void) => Promise<void>;
}) {
  const { user } = useAuth();
  const [text, setText] = useState("");
  const [commentError, setCommentError] = useState("");
  return (
    <section className="panel comments">
      <h2>Comments</h2>
      <ErrorNotice message={comments.error} />
      {user ? (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            act(async () => {
              await api(`/builds/${buildId}/comments`, json("POST", { text }));
              setText("");
              const updatedComments = await api<CommentPage>(
                `/builds/${buildId}/comments?page=0&size=${COMMENT_PAGE_SIZE}`,
              );
              const lastPage = lastCommentPage(
                updatedComments.total,
                updatedComments.size,
              );
              if (lastPage === page) onRefresh();
              else onPageChange(lastPage);
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
          <Link to="/login" state={{ from: `/builds/${buildId}` }}>
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
                    if (lastPage < page) onPageChange(lastPage);
                    else onRefresh();
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
            onClick={() => onPageChange((p) => p - 1)}
          >
            Previous
          </button>
          <span>Page {comments.data.page + 1}</span>
          <button
            disabled={!hasNextCommentPage(comments.data)}
            onClick={() => onPageChange((p) => p + 1)}
          >
            Next
          </button>
        </div>
        )}
    </section>
  );
}
