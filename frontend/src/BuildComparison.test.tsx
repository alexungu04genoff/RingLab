import { act, cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import type { NavigateFunction } from "react-router-dom";
import { api, ApiError } from "./api";
import { BuildComparisonProvider } from "./BuildComparison";
import { Explore } from "./pages/Explore";
import { CompareBuilds } from "./pages/CompareBuilds";
import type { Build, TopCommunitySnapshot } from "./types";

vi.mock("./api", async (original) => ({ ...await original<typeof import("./api")>(), api: vi.fn() }));
vi.mock("./auth", () => ({ useAuth: () => ({ user: null }) }));
const part = (type: "FRONT" | "REAR" | "TIRE") => ({ id: type, type, sourceMachineId: "m",
  sourceMachineName: "Machine", sourceMachineImagePath: null, racingType: "SPEED" as const });
const base: Build = { id: "top", title: "Top build", description: "", author: { id: "a", username: "person" },
  racer: { id: "r", name: "Racer", imagePath: null, racingType: "SPEED" },
  frontPart: part("FRONT"), rearPart: part("REAR"), tirePart: part("TIRE"), gameVersion: null,
  remixedFrom: null, gadgets: [], createdAt: "2026-01-01", updatedAt: "2026-01-01", score: 1, upvotes: 1, downvotes: 0 };
const unknown = { speed: null, acceleration: null, handling: null, power: null, boost: null };
const stats = { ...unknown, character: unknown, machine: unknown };
const ordinary = { ...base, id: "ordinary", title: "Ordinary build" };
let snapshot: TopCommunitySnapshot;
let readBuild: (id: string) => Promise<Build>;
let navigate: NavigateFunction;
beforeEach(() => {
  vi.clearAllMocks(); localStorage.clear();
  snapshot = { schemaVersion: 1, ranking: "best-rated", scope: "overall", patches: "all", snapshotAt: "2026-09-26T10:00:00Z",
    items: [{ rank: 1, build: base, stats, machineType: "SPEED", buildUrl: "/builds/top", artworkUrl: null }] };
  readBuild = async (id) => id === "top" ? base : ordinary;
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === "/community/top-builds") return snapshot;
    if (path.startsWith("/builds?")) return { items: [ordinary, { ...base, id: "third", title: "Third build" }], total: 25, page: 0, size: 12 };
    if (path.startsWith("/builds/")) return readBuild(path.slice(8));
    return [];
  });
});
afterEach(cleanup);
function Harness() {
  navigate = useNavigate();
  const location = useLocation();
  return <><output data-testid="location">{location.pathname}{location.search}</output>
    <Routes><Route path="/" element={<Explore />} /><Route path="/compare" element={<CompareBuilds />} />
      <Route path="/builds/:id" element={<output data-testid="origin">{location.state?.from}</output>} /></Routes></>;
}
function open() { render(<MemoryRouter initialEntries={["/?sort=rated"]}><BuildComparisonProvider><Harness /></BuildComparisonProvider></MemoryRouter>); }
const toggle = (title: string) => screen.getByRole("button", { name: `Compare ${title}` });
const tray = () => screen.getByRole("region", { name: "Build comparison selection" });

it("shares selections, disables other builds at the limit, toggles by ID and clears", async () => {
  open(); await screen.findByText("Top build"); await screen.findByRole("button", { name: "Compare Ordinary build" });
  await userEvent.click(toggle("Top build"));
  expect(within(tray()).getByRole("button", { name: "Compare" }).hasAttribute("disabled")).toBe(true);
  toggle("Ordinary build").focus(); await userEvent.keyboard(" ");
  expect(toggle("Ordinary build").getAttribute("aria-pressed")).toBe("true");
  expect(toggle("Top build").hasAttribute("disabled")).toBe(false);
  expect(toggle("Ordinary build").hasAttribute("disabled")).toBe(false);
  expect(toggle("Third build").hasAttribute("disabled")).toBe(true);
  expect(toggle("Third build").getAttribute("title")).toContain("Remove a selected build");
  expect(within(tray()).getAllByRole("listitem")).toHaveLength(2);
  await userEvent.click(toggle("Top build"));
  expect(toggle("Top build").getAttribute("aria-pressed")).toBe("false");
  expect(toggle("Third build").hasAttribute("disabled")).toBe(false);
  await userEvent.click(within(tray()).getByRole("button", { name: "Clear" }));
  expect(screen.queryByRole("region", { name: "Build comparison selection" })).toBeNull();
  expect(screen.getByText("Comparison cleared.")).toBeTruthy();
});

it("keeps selection across filters, pages, refreshed winners and comparison navigation", async () => {
  open(); await screen.findByText("Top build"); await screen.findByRole("button", { name: "Compare Ordinary build" });
  fireEvent.click(toggle("Top build")); fireEvent.click(toggle("Ordinary build"));
  for (const query of ["search=a", "sort=score", "racerId=r", "machineId=m", "gameVersionId=v", "page=1"]) {
    await act(async () => { await navigate(`/?sort=rated&${query}`); });
    expect(within(tray()).getAllByRole("listitem")).toHaveLength(2);
  }
  snapshot = { ...snapshot, items: [{ ...snapshot.items[0], build: { ...base, id: "new", title: "New winner" } }] };
  fireEvent.click(screen.getByRole("button", { name: "Refresh top builds" }));
  await screen.findByText("New winner");
  expect(within(tray()).getByText("Top build")).toBeTruthy();
  await userEvent.click(within(tray()).getByRole("button", { name: "Compare" }));
  await screen.findByRole("heading", { name: "Compare builds" });
  expect(screen.getByTestId("location").textContent).toBe("/compare?left=top&right=ordinary");
  await userEvent.click(screen.getByRole("link", { name: "← Back to Explore" }));
  expect(screen.getByTestId("location").textContent).toBe("/?sort=rated&page=1");
  expect(within(tray()).getAllByRole("listitem")).toHaveLength(2);
});

it("keeps real details links separate from every focusable card control and preserves origin", async () => {
  open(); const link = await screen.findByRole("link", { name: "Ordinary build" });
  expect(link.getAttribute("href")).toBe("/builds/ordinary");
  expect(link.querySelector("button, [tabindex], a")).toBeNull();
  expect(toggle("Ordinary build").closest("a")).toBeNull();
  expect(document.querySelector("a .card-part-icon, a .score-help")).toBeNull();
  await userEvent.click(link);
  expect(screen.getByTestId("origin").textContent).toBe("/?sort=rated");
});

it("toggles duplicate appearances by ID without conflating identical titles", async () => {
  const previous = vi.mocked(api).getMockImplementation()!;
  vi.mocked(api).mockImplementation(async (path, options) => path.startsWith("/builds?")
    ? { items: [base, { ...base, id: "same-title" }], total: 2, page: 0, size: 12 }
    : previous(path, options));
  open();
  const buttons = await screen.findAllByRole("button", { name: "Compare Top build" });
  // Wait for the independently loaded Top 3 and page to both settle.
  await screen.findByRole("region", { name: "Top 3 community builds" });
  await act(async () => {});
  expect(screen.getAllByRole("button", { name: "Compare Top build" })).toHaveLength(3);
  fireEvent.click(buttons[0]);
  expect(screen.getAllByRole("button", { name: "Compare Top build" }).filter((button) => button.getAttribute("aria-pressed") === "true")).toHaveLength(2);
  fireEvent.click(screen.getAllByRole("button", { name: "Compare Top build" })[2]);
  expect(within(tray()).getAllByRole("listitem")).toHaveLength(2);
  fireEvent.click(screen.getAllByRole("button", { name: "Compare Top build" })[1]);
  expect(within(tray()).getAllByRole("listitem")).toHaveLength(1);
});

it("retains missing and temporarily unavailable selections with distinct explanations and retry", async () => {
  open(); await screen.findByText("Top build"); await screen.findByRole("button", { name: "Compare Ordinary build" });
  fireEvent.click(toggle("Top build")); fireEvent.click(toggle("Ordinary build"));
  readBuild = async (id) => { throw id === "top" ? new ApiError(404, "Missing") : new Error("Offline"); };
  await act(async () => { fireEvent.click(within(tray()).getByRole("button", { name: "Compare" })); });
  await screen.findByText(/This build no longer exists/);
  expect(screen.getByText(/Couldn’t check this build/)).toBeTruthy();
  expect(within(tray()).getAllByRole("listitem")).toHaveLength(2);
  readBuild = async (id) => id === "top" ? base : ordinary;
  await act(async () => { fireEvent.click(within(tray()).getByRole("button", { name: "Compare" })); });
  await screen.findByRole("heading", { name: "Compare builds" });
});

it("does not navigate after selection changes while its availability check is pending", async () => {
  open(); await screen.findByText("Top build"); await screen.findByRole("button", { name: "Compare Ordinary build" });
  fireEvent.click(toggle("Top build")); fireEvent.click(toggle("Ordinary build"));
  const resolvers: Array<(build: Build) => void> = [];
  readBuild = () => new Promise((done) => { resolvers.push(done); });
  fireEvent.click(within(tray()).getByRole("button", { name: "Compare" }));
  fireEvent.click(within(tray()).getByRole("button", { name: "Remove Top build from comparison" }));
  await act(async () => { resolvers.forEach((resolve) => resolve(ordinary)); });
  expect(screen.getByTestId("location").textContent).toBe("/?sort=rated");
});
