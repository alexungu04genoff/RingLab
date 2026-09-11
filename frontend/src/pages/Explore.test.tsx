import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import { useLoad } from "../useLoad";
import { Explore } from "./Explore";

vi.mock("../useLoad", () => ({ useLoad: vi.fn() }));
vi.mock("../auth", () => ({ useAuth: () => ({ user: null }) }));

const newsItem = {
  id: "1", title: "CrossWorlds update <b>today</b>",
  url: "https://store.steampowered.com/news/1", publishedAt: "2026-09-01T12:00:00Z",
};
let news = { data: [newsItem], error: "", loading: false };

beforeEach(() => {
  vi.clearAllMocks();
  news = { data: [newsItem], error: "", loading: false };
  vi.mocked(useLoad).mockImplementation((path: string) => {
    if (path === "/news") return news;
    if (path.startsWith("/builds?")) {
      return { data: { items: [], total: 0, page: 0, size: 12 }, error: "", loading: false };
    }
    return { data: [], error: "", loading: false };
  });
});

function render(mine = false) {
  return renderToStaticMarkup(<MemoryRouter><Explore mine={mine} /></MemoryRouter>);
}

it("renders news titles as text with publication dates and original links", () => {
  const html = render();
  expect(html).toContain("Latest news");
  expect(html).toContain("CrossWorlds update &lt;b&gt;today&lt;/b&gt;");
  expect(html).toContain('href="https://store.steampowered.com/news/1" target="_blank" rel="noreferrer"');
  expect(html).toContain('dateTime="2026-09-01T12:00:00Z"');
});

it.each(["empty", "unavailable", "loading"])("keeps Explore usable when news is %s", (state) => {
  news = { data: [], error: state === "unavailable" ? "Upstream failed" : "", loading: state === "loading" };
  const html = render();
  expect(html).toContain(state === "empty" ? "No news yet." : state === "loading" ? "Loading news…" : "News unavailable");
  expect(html).toContain("Community builds");
  expect(html).toContain("Search builds");
  expect(html).toContain("Be the first to share a build");
  expect(html).not.toContain("Upstream failed");
});

it("does not load or display news in My Builds", () => {
  expect(render(true)).not.toContain("Latest news");
  expect(vi.mocked(useLoad).mock.calls.some(([path]) => path === "/news")).toBe(false);
});
