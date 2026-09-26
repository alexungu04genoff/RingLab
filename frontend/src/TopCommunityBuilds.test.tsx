import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter, useNavigate } from "react-router-dom";
import type { NavigateFunction } from "react-router-dom";
import { api } from "./api";
import { Explore } from "./pages/Explore";
import { topCommunitySummary } from "./TopCommunityBuilds";
import type { Build, TopCommunitySnapshot } from "./types";

vi.mock("./api", () => ({ api: vi.fn() }));
vi.mock("./auth", () => ({ useAuth: () => ({ user: { id: "me" } }) }));
const part = (type: "FRONT" | "REAR" | "TIRE") => ({ id: type, type, sourceMachineId: "machine",
  sourceMachineName: "Machine", sourceMachineImagePath: null, racingType: "SPEED" as const });
const build: Build = {
  id: "top", title: "Top snapshot winner", description: "", author: { id: "a", username: "author" },
  racer: { id: "r", name: "Racer", racingType: "SPEED", imagePath: null },
  frontPart: part("FRONT"), rearPart: part("REAR"), tirePart: part("TIRE"),
  gameVersion: { id: "v1", version: "1.4.1", releasedAt: "2026-06-23" }, remixedFrom: null,
  gadgets: [], createdAt: "2026-01-01", updatedAt: "2026-01-01", score: 7, upvotes: 8, downvotes: 1,
};
const stats = { speed: null, acceleration: null, handling: null, power: null, boost: null };
const snapshot: TopCommunitySnapshot = {
  schemaVersion: 1, ranking: "best-rated", scope: "overall", patches: "all", snapshotAt: "2026-09-20T12:00:00Z",
  items: [{ rank: 1, build, machineType: "SPEED", stats: { ...stats, character: stats, machine: stats },
    buildUrl: "https://site.example/builds/top", artworkUrl: null }],
};
let top: () => Promise<TopCommunitySnapshot>;
let searchError: boolean;
beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  top = () => Promise.resolve(snapshot);
  searchError = false;
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === "/community/top-builds") return top();
    if (path === "/game-versions") return [build.gameVersion];
    if (path.startsWith("/stats/build")) return { ...stats, character: stats, machine: stats };
    if (path.startsWith("/builds?")) {
      if (searchError) throw new Error("Search failed");
      const params = new URLSearchParams(path.split("?")[1]);
      const title = params.get("search") === "comment" ? "[Comment Demo] result" : "[Wilson Demo] result";
      return { items: Array.from({ length: 12 }, (_, i) => ({ ...build, id: `result-${i}`, title: `${title} ${i}` })), total: 25, page: 0, size: 12 };
    }
    return [];
  });
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
function page(mine = false) {
  let navigate!: NavigateFunction;
  function Navigation() { navigate = useNavigate(); return <Explore mine={mine} />; }
  render(<MemoryRouter><Navigation /></MemoryRouter>);
  return { navigate: (to: string) => navigate(to) };
}
const topCalls = () => vi.mocked(api).mock.calls.filter(([path]) => path === "/community/top-builds");

it("keeps one showcase request across every filter, preference, sort and page change", async () => {
  localStorage.setItem("ringlab.explore.sort", "newest");
  localStorage.setItem("ringlab.explore.gameVersionId", "v1");
  const router = page();
  await screen.findByText("Top snapshot winner");
  expect(screen.getByText("Overall best rated · All patches")).toBeTruthy();
  expect(screen.getByLabelText("Rank 1")).toBeTruthy();
  expect(within(screen.getByRole("region", { name: "Top 3 community builds" }))
    .getByLabelText("Base stats")).toBeTruthy();
  expect(topCalls()).toHaveLength(1);
  expect(topCalls()[0][1]).toMatchObject({ anonymous: true, credentials: "omit", cache: "no-cache" });
  expect(vi.mocked(api).mock.calls.some(([path]) =>
    path.startsWith("/builds?") && new URLSearchParams(path.split("?")[1]).getAll("excludeId").includes("top")))
    .toBe(true);
  for (const query of ["search=wilson", "search=comment", "racerId=r", "machineId=m", "gameVersionId=v1", "sort=score", "page=1"]) {
    await act(async () => { await router.navigate(`/?${query}`); });
    expect(screen.getByText("Top snapshot winner")).toBeTruthy();
    expect(screen.getByText("25 builds")).toBeTruthy();
    expect(topCalls()).toHaveLength(1);
    expect(screen.getAllByText(/\[(Wilson|Comment) Demo\] result/)).toHaveLength(12);
  }
  expect(screen.getByText("Page 2")).toBeTruthy();
}, 15000);

it("copies displayed items without fetching and reports clipboard failures", async () => {
  const writeText = vi.fn().mockResolvedValue(undefined);
  Object.defineProperty(navigator, "clipboard", { configurable: true, value: { writeText } });
  page();
  await screen.findByText("Top snapshot winner");
  fireEvent.click(screen.getByRole("button", { name: "Copy top 3" }));
  await screen.findByText("Top builds copied to clipboard.");
  expect(writeText).toHaveBeenCalledWith(topCommunitySummary(snapshot));
  expect(topCalls()).toHaveLength(1);
  writeText.mockRejectedValueOnce(new Error("Denied"));
  fireEvent.click(screen.getByRole("button", { name: "Copy top 3" }));
  await screen.findByText(/Couldn’t copy/);
});

it("keeps displayed winners and paginated exclusions together when winners change", async () => {
  const challenger = { ...build, id: "challenger", title: "New winner" };
  const fallback = vi.mocked(api).getMockImplementation()!;
  vi.mocked(api).mockImplementation(async (path, options) => {
    if (!path.startsWith("/builds?")) return fallback(path, options);
    const excluded = new URLSearchParams(path.split("?")[1]).getAll("excludeId");
    const items = [build, challenger].filter(item => !excluded.includes(item.id));
    return { items, total: items.length, page: 0, size: 12 };
  });
  const router = page();
  await screen.findByText("1 builds");
  expect(screen.getAllByText(build.title)).toHaveLength(1);
  expect(screen.getAllByText(challenger.title)).toHaveLength(1);
  top = () => Promise.resolve({ ...snapshot, items: [{ ...snapshot.items[0], build: challenger }] });
  // Changing the lower query must still exclude the displayed old winner, despite server changes.
  await act(async () => { await router.navigate("/?sort=score"); });
  await screen.findByText("1 builds");
  expect(screen.getAllByText(build.title)).toHaveLength(1);
  fireEvent.click(screen.getByRole("button", { name: "Refresh top builds" }));
  await waitFor(() => expect(within(screen.getByRole("region", { name: "Top 3 community builds" }))
    .getByText(challenger.title)).toBeTruthy());
  await screen.findByText("1 builds");
  expect(screen.getAllByText(build.title)).toHaveLength(1);
  expect(screen.getAllByText(challenger.title)).toHaveLength(1);
});

it("keeps loaded showcase on refresh failure and retries independently", async () => {
  page();
  await screen.findByText("Top snapshot winner");
  top = () => Promise.reject(new Error("Unavailable"));
  fireEvent.click(screen.getByRole("button", { name: "Refresh top builds" }));
  await screen.findByText(/Showing the last loaded snapshot/);
  expect(screen.getByText("Top snapshot winner")).toBeTruthy();
  expect(screen.getByText("25 builds")).toBeTruthy();
  top = () => Promise.resolve({ ...snapshot, items: [] });
  fireEvent.click(screen.getByRole("button", { name: "Retry top builds" }));
  await screen.findByText("No eligible community builds yet.");
});

it("loads and fails independently of normal results", async () => {
  let resolve!: (value: TopCommunitySnapshot) => void;
  top = () => new Promise(done => { resolve = done; });
  const router = page();
  await screen.findByText("25 builds");
  expect(screen.getByText("Loading top community builds…")).toBeTruthy();
  await act(async () => resolve(snapshot));
  searchError = true;
  await act(async () => { await router.navigate("/?search=failed"); });
  await screen.findByText("Search failed");
  expect(screen.getByText("Top snapshot winner")).toBeTruthy();
});

it("does not show the showcase in My Builds", async () => {
  page(true);
  await screen.findByText("25 builds");
  expect(topCalls()).toHaveLength(0);
  expect(vi.mocked(api).mock.calls.some(([path]) => path.startsWith("/builds?") && path.includes("excludeTop")))
    .toBe(false);
  expect(screen.queryByText("Top 3 community builds")).toBeNull();
});

it("does not substitute search results when the initial showcase request fails", async () => {
  top = () => Promise.reject(new Error("Unavailable"));
  page();
  await screen.findByText(/Couldn’t refresh top community/);
  const section = screen.getByRole("region", { name: "Top 3 community builds" });
  expect(within(section).queryByText(/Wilson Demo/)).toBeNull();
  await waitFor(() => expect(screen.getByText("25 builds")).toBeTruthy());
});
