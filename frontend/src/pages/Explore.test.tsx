import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import { useLoad } from "../useLoad";
import { browseOrigin, buildDetailsOrigin } from "../components";
import {
  clearExploreFilters,
  Explore,
  resolveGameVersion,
  resolvePage,
  resolveSort,
  selectRandomHeroRacers,
  updateExploreParams,
} from "./Explore";

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
    if (path === "/game-versions") return {
      data: [{ id: "v1", version: "1.4.1", releasedAt: "2026-06-23" },
        { id: "v2", version: "1.3.1", releasedAt: "2026-03-18" }], error: "", loading: false,
    };
    if (path.startsWith("/builds?")) {
      return { data: { items: [], total: 0, page: 0, size: 12 }, error: "", loading: false };
    }
    return { data: [], error: "", loading: false };
  });
});

function render(mine = false, entry = "/") {
  return renderToStaticMarkup(<MemoryRouter initialEntries={[entry]}><Explore mine={mine} /></MemoryRouter>);
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

it("offers all versions by default and newest-first patches alongside existing filters and sorts", () => {
  const html = render();
  const selector = html.match(/Patch<select([^>]*)>(.*?)<\/select>/)!;
  expect(selector[1]).not.toContain("required");
  expect(selector[2]).toContain('value="" selected="">All versions');
  expect(selector[2].indexOf("Ver. 1.4.1")).toBeLessThan(selector[2].indexOf("Ver. 1.3.1"));
  expect(html).toContain("Uses parts from");
  expect(html).toContain('value="rated" selected="">Best rated');
  expect(vi.mocked(useLoad).mock.calls.find(([path]) => path.startsWith("/builds?"))![0])
    .not.toContain("gameVersionId");
});

it("uses rated for public Explore and newest for My Builds", () => {
  render();
  expect(vi.mocked(useLoad).mock.calls.find(([path]) => path.startsWith("/builds?"))![0])
    .toContain("sort=rated");
  vi.clearAllMocks();
  render(true, "/my-builds");
  expect(vi.mocked(useLoad).mock.calls.find(([path]) => path.startsWith("/builds?"))![0])
    .toContain("sort=newest");
});

it("applies URL, preference, and default sort precedence safely", () => {
  expect(resolveSort("score", "rated", false)).toBe("score");
  expect(resolveSort(null, "score", false)).toBe("score");
  expect(resolveSort(null, "score", true)).toBe("newest");
  expect(resolveSort("unknown", "broken", false)).toBe("rated");
});

it("restores only known saved public patches", () => {
  expect(resolveGameVersion(null, "v1", ["v1", "v2"], false)).toBe("v1");
  expect(resolveGameVersion("v2", "v1", ["v1", "v2"], false)).toBe("v2");
  expect(resolveGameVersion(null, "v1", ["v1", "v2"], true)).toBe("");
  expect(resolveGameVersion(null, "removed", ["v1", "v2"], false)).toBe("");
});

it("updates filters and applied search in the URL while resetting page", () => {
  const initial = new URLSearchParams("sort=rated&page=3");
  expect(updateExploreParams(initial, { sort: "score" }).toString()).toBe("sort=score");
  expect(updateExploreParams(initial, { gameVersionId: "v1" }).toString()).toBe("sort=rated&gameVersionId=v1");
  expect(updateExploreParams(initial, { racerId: "r1", machineId: "m1" }).toString())
    .toBe("sort=rated&racerId=r1&machineId=m1");
  expect(updateExploreParams(initial, { search: "speed build" }).toString())
    .toBe("sort=rated&search=speed+build");
});

it("keeps pagination in the URL and rejects malformed pages", () => {
  expect(updateExploreParams(new URLSearchParams("sort=rated"), { page: "2" }, false).get("page"))
    .toBe("2");
  expect(resolvePage("2")).toBe(2);
  expect(resolvePage("-1")).toBe(0);
  expect(resolvePage("nonsense")).toBe(0);
});

it("clears applied filters and page while retaining sort", () => {
  const cleared = clearExploreFilters(new URLSearchParams(
    "search=sonic&racerId=r1&machineId=m1&gameVersionId=v1&sort=score&page=2",
  ));
  expect(cleared.toString()).toBe("sort=score");
});

it("preserves and restores exact Explore and My Builds origins", () => {
  const explore = browseOrigin("/", "?sort=rated&gameVersionId=v1&page=2");
  const mine = browseOrigin("/my-builds", "?sort=newest&page=1");
  expect(buildDetailsOrigin(explore)).toBe(explore);
  expect(buildDetailsOrigin(mine)).toBe(mine);
  expect(buildDetailsOrigin("https://example.com")).toBe("/");
});

it("randomly selects four unique hero racers without changing the catalog order", () => {
  const racers = Array.from({ length: 10 }, (_, index) => ({
    id: String(index),
    name: `Racer ${index}`,
    racingType: "SPEED" as const,
    imagePath: `/assets/racers/${index}.png`,
  }));

  const selected = selectRandomHeroRacers(racers, 4, () => 0);

  expect(selected.map(({ id }) => id)).toEqual(["1", "2", "3", "4"]);
  expect(new Set(selected.map(({ id }) => id))).toHaveLength(4);
  expect(racers.map(({ id }) => id)).toEqual(["0", "1", "2", "3", "4", "5", "6", "7", "8", "9"]);
});
