import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { api, setToken } from "../api";
import { BuildComparisonProvider } from "../BuildComparison";
import { SavedBuildsProvider } from "../SavedBuilds";
import { SavedBuildsPage } from "./SavedBuildsPage";
import { CompareBuilds } from "./CompareBuilds";
import type { Build } from "../types";

vi.mock("../api", async original => ({ ...await original<typeof import("../api")>(), api: vi.fn() }));
vi.mock("../auth", () => ({ useAuth: () => ({ user: { id: "owner" } }) }));
const part = (type: "FRONT" | "REAR" | "TIRE") => ({ id: type, type, sourceMachineId: "m", sourceMachineName: "Machine", sourceMachineImagePath: null, racingType: "SPEED" as const });
const build: Build = { id: "one", title: "Saved setup", description: "", author: { id: "author", username: "Original author" },
  racer: { id: "r", name: "Racer", imagePath: null, racingType: "SPEED" }, frontPart: part("FRONT"), rearPart: part("REAR"), tirePart: part("TIRE"),
  gameVersion: null, remixedFrom: null, gadgets: [], createdAt: "2026-01-01", updatedAt: "2026-01-01", score: 0, upvotes: 0, downvotes: 0 };
const second = { ...build, id: "two", title: "Second setup" };
const values = { speed: 10, acceleration: 20, handling: 30, power: 40, boost: 50 };
const stats = { ...values, character: values, machine: values };
let read: (path: string) => Promise<unknown>;
const page = (items: Build[] = [], total = items.length) => ({ items: items.map(build => ({ build, savedAt: "2026-09-26T10:00:00Z" })), total, page: 0, size: 12,
  statsByBuildId: Object.fromEntries(items.map(build => [build.id, stats])), statsError: null });
beforeEach(() => {
  vi.resetAllMocks(); setToken("saved-page-session"); read = async () => page();
  vi.mocked(api).mockImplementation(async (path, options) => {
    if (path.startsWith("/saved-builds/status?")) return { savedIds: ["one", "two"] };
    if (path.startsWith("/saved-builds?")) return read(path);
    if (options?.method === "DELETE") return undefined;
    if (path === "/builds/one") return build;
    if (path === "/builds/two") return second;
    if (path.startsWith("/stats/")) return stats;
    return [];
  });
});
afterEach(() => { cleanup(); setToken(null); });
function Location() { const location = useLocation(); return <output data-testid="location">{location.pathname}{location.search}</output>; }
function open(entry = "/saved-builds") {
  render(<MemoryRouter initialEntries={[entry]}><SavedBuildsProvider><BuildComparisonProvider><Location /><Routes>
    <Route path="/saved-builds" element={<SavedBuildsPage />} /><Route path="/compare" element={<CompareBuilds />} />
  </Routes></BuildComparisonProvider></SavedBuildsProvider></MemoryRouter>);
}

it("shows loading, empty and filtered-empty states and supports clearing filters", async () => {
  let finish!: (value: unknown) => void;
  read = () => new Promise(resolve => { finish = resolve; }); open();
  expect(screen.getByText("Loading saved builds…")).toBeTruthy();
  finish(page());
  await screen.findByText("No saved builds yet. Bookmark a setup from Explore to find it here later.");
  read = async () => page();
  fireEvent.change(screen.getByLabelText("Search saved builds"), { target: { value: "absent" } });
  fireEvent.click(screen.getByRole("button", { name: "Search" }));
  await screen.findByText("No saved builds match these filters.");
  expect(screen.getByTestId("location").textContent).toContain("search=absent");
  fireEvent.click(screen.getByRole("button", { name: "Clear filters" }));
  await screen.findByText("No saved builds yet. Bookmark a setup from Explore to find it here later.");
});

it("retries list failures and uses page stats without per-card requests", async () => {
  read = async () => { throw new Error("offline"); }; open();
  await screen.findByRole("button", { name: "Retry saved builds" });
  read = async () => page([build]);
  fireEvent.click(screen.getByRole("button", { name: "Retry saved builds" }));
  await screen.findByRole("link", { name: "Saved setup" });
  expect(screen.getByText("@Original author")).toBeTruthy();
  expect(vi.mocked(api).mock.calls.some(([path]) => path.startsWith("/stats/"))).toBe(false);
});

it("moves to the preceding page after removing the final item on the last page", async () => {
  let removed = false;
  read = async path => new URLSearchParams(path.split("?")[1]).get("page") === "1"
    ? page(removed ? [] : [build], removed ? 12 : 13) : page([second], 12);
  open("/saved-builds?page=1&search=setup");
  const remove = await screen.findByRole("button", { name: "Remove Saved setup from saved builds" });
  removed = true; fireEvent.click(remove);
  await screen.findByRole("link", { name: "Second setup" });
  expect(screen.getByTestId("location").textContent).toBe("/saved-builds?search=setup");
});

it("keeps save, card navigation and comparison independent and preserves the saved origin", async () => {
  read = async () => page([build, second]); open("/saved-builds?search=setup");
  await screen.findByRole("link", { name: "Saved setup" });
  expect(screen.getByRole("link", { name: "Saved setup" }).getAttribute("href")).toBe("/builds/one");
  fireEvent.click(screen.getByRole("button", { name: "Compare Saved setup" }));
  fireEvent.click(screen.getByRole("button", { name: "Compare Second setup" }));
  expect(vi.mocked(api).mock.calls.some(([, options]) => options?.method === "PUT")).toBe(false);
  fireEvent.click(within(screen.getByRole("region", { name: "Build comparison selection" })).getByRole("button", { name: "Compare" }));
  await screen.findByRole("heading", { name: "Compare builds" });
  fireEvent.click(screen.getByRole("link", { name: "← Back to Saved Builds" }));
  await waitFor(() => expect(screen.getByTestId("location").textContent).toBe("/saved-builds?search=setup"));
  expect(within(screen.getByRole("region", { name: "Build comparison selection" })).getAllByRole("listitem")).toHaveLength(2);
});
