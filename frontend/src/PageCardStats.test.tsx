import { act, cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter, useNavigate } from "react-router-dom";
import type { NavigateFunction } from "react-router-dom";
import { api } from "./api";
import { Explore } from "./pages/Explore";
import { CardStats, pageCardStats } from "./CardStats";
import type { Build, BuildPage } from "./types";

vi.mock("./api", () => ({ api: vi.fn() }));
vi.mock("./auth", () => ({ useAuth: () => ({ user: { id: "mine" } }) }));
vi.mock("./TopCommunityBuilds", () => ({ TopCommunityBuilds: () => null }));
const part = (type: "FRONT" | "REAR" | "TIRE") => ({ id: type, type, sourceMachineId: "m",
  sourceMachineName: "Machine", sourceMachineImagePath: null, racingType: "SPEED" as const });
const base: Build = { id: "b", title: "Ordinary build", description: "", author: { id: "a", username: "person" },
  racer: { id: "r", name: "Racer", imagePath: null, racingType: "SPEED" },
  frontPart: part("FRONT"), rearPart: part("REAR"), tirePart: part("TIRE"),
  gameVersion: { id: "patch", version: "1.4.1", releasedAt: "2026-06-23" },
  remixedFrom: null, gadgets: [], createdAt: "2026-01-01", updatedAt: "2026-01-01", score: 1, upvotes: 1, downvotes: 0 };
const values = { speed: 17.25, acceleration: 0, handling: null, power: 3, boost: 4 };
const stats = { ...values, character: values, machine: values };
const unknown = { speed: null, acceleration: null, handling: null, power: null, boost: null };
const page: BuildPage = { items: [base], total: 1, page: 0, size: 12, statsByBuildId: { b: stats } };
let loadPage: (path: string) => Promise<BuildPage>;
let navigate: NavigateFunction;
beforeEach(() => {
  vi.clearAllMocks(); localStorage.clear(); loadPage = async () => page;
  vi.mocked(api).mockImplementation(async (path) => {
    if (path.startsWith("/builds?")) return loadPage(path);
    if (path === "/game-versions") return [base.gameVersion];
    return [];
  });
});
afterEach(cleanup);
function Harness({ mine }: { mine: boolean }) { navigate = useNavigate(); return <Explore mine={mine} />; }
function open(mine = false) { render(<MemoryRouter initialEntries={[mine ? "/my-builds?sort=newest" : "/?sort=rated"]}><Harness mine={mine} /></MemoryRouter>); }
const statsRequests = () => vi.mocked(api).mock.calls.filter(([path]) => path.startsWith("/stats/"));

it.each([false, true])("consumes a 12-card page in Explore/My Builds (mine=%s) without per-card requests", async (mine) => {
  const items = Array.from({ length: 12 }, (_, i) => ({ ...base, id: String(i), title: `Build ${i}` }));
  loadPage = async () => ({ ...page, items, total: 12, statsByBuildId: Object.fromEntries(items.map(({ id }) => [id, stats])) });
  open(mine);
  expect(await screen.findAllByText("17.25")).toHaveLength(12);
  expect(screen.getAllByText("0")).toHaveLength(12);
  expect(statsRequests()).toHaveLength(0);
  const queries = vi.mocked(api).mock.calls.filter(([path]) => path.startsWith("/builds?")).map(([path]) => new URLSearchParams(path.split("?")[1]));
  expect(queries.every((query) => query.get("includeStats") === "true")).toBe(true);
  if (mine) expect(queries.every((query) => query.get("authorId") === "mine")).toBe(true);
});

it("accepts all-null/versionless results as unavailable without falling back to another request", async () => {
  loadPage = async () => ({ ...page, items: [{ ...base, gameVersion: null }], statsByBuildId: { b: { ...unknown, character: unknown, machine: unknown } } });
  open(); await screen.findByRole("link", { name: "Ordinary build" });
  expect(screen.getAllByText("—")).toHaveLength(5);
  expect(document.querySelectorAll(".card-stat-track.unknown")).toHaveLength(5);
  expect(statsRequests()).toHaveLength(0);
});

it("keeps the browse page usable on enrichment failure and retries the page as a whole", async () => {
  loadPage = async () => ({ ...page, statsByBuildId: undefined, statsError: "Could not load base stats." });
  open(); await screen.findByText("Couldn’t load stats.");
  expect(screen.getByRole("link", { name: "Ordinary build" })).toBeTruthy();
  loadPage = async () => page;
  fireEvent.click(screen.getByRole("button", { name: "Retry page stats" }));
  expect(await screen.findByText("17.25")).toBeTruthy();
  expect(statsRequests()).toHaveLength(0);
});

it("distinguishes absent enrichment and pending data without silently starting per-card loads", () => {
  const { rerender } = render(<CardStats build={base} pageStats={{ status: "pending" }} />);
  expect(screen.getByText("Loading stats…")).toBeTruthy();
  rerender(<CardStats build={base} pageStats={pageCardStats({ ...page, statsByBuildId: undefined }, base.id)} />);
  expect(screen.getByText("Stats not included in this page.")).toBeTruthy();
  rerender(<CardStats build={base} pageStats={pageCardStats({ ...page, statsByBuildId: {} }, base.id)} />);
  expect(screen.getByText("Couldn’t load stats.")).toBeTruthy();
  expect(statsRequests()).toHaveLength(0);
});

it("never displays stats from a stale page after a fast filter/page change", async () => {
  let resolveOld!: (page: BuildPage) => void;
  loadPage = (path) => path.includes("page=0") ? new Promise((resolve) => { resolveOld = resolve; })
    : Promise.resolve({ ...page, items: [{ ...base, id: "new", title: "New page" }], statsByBuildId: { new: { ...stats, speed: 88 } } });
  open();
  await act(async () => { await navigate("/?sort=rated&page=1&search=new"); });
  await screen.findByText("88");
  await act(async () => { resolveOld(page); });
  expect(screen.queryByRole("link", { name: "Ordinary build" })).toBeNull();
  expect(screen.queryByText("17.25")).toBeNull();
  expect(within(screen.getByRole("article")).getByText("88")).toBeTruthy();
  expect(statsRequests()).toHaveLength(0);
});
