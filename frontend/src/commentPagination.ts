import type { CommentPage } from "./types";

export function hasNextCommentPage(page: CommentPage) {
  return (page.page + 1) * page.size < page.total;
}

export function lastCommentPage(total: number, size: number) {
  return Math.max(0, Math.ceil(total / size) - 1);
}
