import { describe, expect, it } from "vitest";
import { COMMENT_PAGE_SIZE, hasNextCommentPage, lastCommentPage } from "./commentPagination";
import type { CommentPage } from "./types";

const page = (total: number, pageNumber = 0): CommentPage => ({
  items: [],
  total,
  page: pageNumber,
  size: COMMENT_PAGE_SIZE,
});

describe("comment pagination", () => {
  it("does not infer another page when the total is exactly one full page", () => {
    expect(hasNextCommentPage(page(20))).toBe(false);
  });

  it("detects another page from the total", () => {
    expect(hasNextCommentPage(page(21))).toBe(true);
    expect(hasNextCommentPage(page(21, 1))).toBe(false);
  });

  it("finds the page containing a newly appended comment", () => {
    expect(COMMENT_PAGE_SIZE).toBe(20);
    expect(lastCommentPage(20, COMMENT_PAGE_SIZE)).toBe(0);
    expect(lastCommentPage(21, COMMENT_PAGE_SIZE)).toBe(1);
  });
});
