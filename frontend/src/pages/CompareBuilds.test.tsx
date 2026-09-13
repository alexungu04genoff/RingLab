// @vitest-environment jsdom
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { useLoad } from "../useLoad";
import type { Build, MachinePart } from "../types";
import { BuildDetails } from "./BuildDetails";
import { buildDiff, CompareBuilds, compareUrl } from "./CompareBuilds";

vi.mock("../useLoad", () => ({ useLoad: vi.fn() }));
vi.mock("../api", () => ({ api: vi.fn(), json: vi.fn() }));
vi.mock("../auth", () => ({ useAuth: () => ({ user: null }) }));

const part = (type: MachinePart["type"], source: string): MachinePart => ({
  id: `${source}-${type}`, type, sourceMachineId: source, sourceMachineName: source,
  sourceMachineImagePath: `/assets/machines/${source}.png`, racingType: "SPEED",
});
const gadget = (id: string, cost: number) => ({
  id, name: `Gadget ${id}`, description: null, slotCost: cost, imagePath: `/assets/gadgets/${id}.png`,
});
const left: Build = {
  id: "left", title: "Sonic speed line", description: "", author: { id: "a", username: "amy" },
  racer: { id: "sonic", name: "Sonic", racingType: "SPEED", imagePath: "/assets/racers/sonic.png" },
  frontPart: part("FRONT", "blue-star"), rearPart: part("REAR", "blue-star"),
  tirePart: part("TIRE", "blue-star"), gameVersion: { id: "v1", version: "1.4.1", releasedAt: "2026-06-23" },
  remixedFrom: null,
  gadgets: [gadget("ring", 1), gadget("boost", 2)], createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-02T00:00:00Z", score: 5, upvotes: 7, downvotes: 2,
};
const right: Build = {
  ...left, id: "right", title: "Shadow mixed line", author: { id: "b", username: "rouge" },
  racer: { id: "shadow", name: "Shadow", racingType: "POWER", imagePath: "/assets/racers/shadow.png" },
  rearPart: part("REAR", "dark-reaper"), gameVersion: { id: "v2", version: "1.3.1", releasedAt: "2026-03-18" },
  gadgets: [gadget("boost", 2), gadget("ring", 1)], score: 2, upvotes: 4, downvotes: 2,
};

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useLoad).mockImplementation((path: string) => {
    if (!path) return { data: undefined, error: "", loading: false };
    if (path === "/builds/left") return { data: left, error: "", loading: false };
    if (path === "/builds/right") return { data: right, error: "", loading: false };
    if (path.startsWith("/builds?")) return {
      data: { items: [left, right], total: 2, page: 0, size: 8 }, error: "", loading: false,
    };
    if (path.includes("/comments")) return { data: { items: [], total: 0, page: 0, size: 20 }, error: "", loading: false };
    if (path === "/game-versions") return { data: [left.gameVersion, right.gameVersion], error: "", loading: false };
    return { data: undefined, error: "Build not found", loading: false };
  });
});
afterEach(cleanup);

function route(entry: string) {
  return render(<MemoryRouter initialEntries={[entry]}><Routes>
    <Route path="/compare" element={<CompareBuilds />} />
    <Route path="/builds/:id" element={<div>Build details destination</div>} />
  </Routes></MemoryRouter>);
}

it("renders both URL-selected builds and preserves gadget order and plate usage", () => {
  route("/compare?left=left&right=right");
  expect(screen.getByRole("article", { name: "Left build: Sonic speed line" })).toBeTruthy();
  expect(screen.getByRole("article", { name: "Right build: Shadow mixed line" })).toBeTruthy();
  const leftColumn = screen.getByRole("article", { name: "Left build: Sonic speed line" });
  const gadgets = within(leftColumn).getAllByRole("listitem");
  expect(gadgets[0].textContent).toContain("Gadget ring");
  expect(gadgets[1].textContent).toContain("Gadget boost");
  expect(within(leftColumn).getByText("3 / 6 slots")).toBeTruthy();
});

it("excludes the current build and navigates after an accessible selection", async () => {
  const user = userEvent.setup();
  route("/compare?left=left");
  await user.click(screen.getByRole("combobox", { name: "Search community builds" }));
  expect(screen.queryByRole("option", { name: /Sonic speed line/ })).toBeNull();
  await user.click(screen.getByRole("option", { name: /Shadow mixed line/ }));
  expect(screen.getByRole("article", { name: "Left build: Sonic speed line" })).toBeTruthy();
  expect(screen.getByRole("article", { name: "Right build: Shadow mixed line" })).toBeTruthy();
});

it("marks changed racer, patch, rear part, composition, and gadget positions", () => {
  route("/compare?left=left&right=right");
  ["Racer", "Patch", "Machine composition", "REAR part", "Gadget 1", "Gadget 2"].forEach((label) =>
    expect(document.querySelector(`[data-difference="${label}"]`)).not.toBeNull());
  expect(document.querySelector('[data-difference="FRONT part"]')).toBeNull();
  expect(document.querySelector('[data-difference="TIRES part"]')).toBeNull();
});

it("does not mark matching values as different", () => {
  expect(buildDiff(left, { ...left, id: "another" })).toMatchObject({
    racer: false, patch: false, front: false, rear: false, tires: false, composition: false, plate: false,
  });
  expect(buildDiff(left, { ...left, id: "another" }).gadgetAt(0)).toBe(false);
});

it("shows safe states for missing, invalid, and repeated build IDs", () => {
  const missing = route("/compare");
  expect(screen.getByText("Choose a build first.")).toBeTruthy();
  missing.unmount();
  const invalid = route("/compare?left=missing&right=right");
  expect(screen.getByRole("alert").textContent).toContain("Build not found");
  invalid.unmount();
  route("/compare?left=left&right=left");
  expect(screen.getByText("Choose two different builds.")).toBeTruthy();
});

it("creates refresh-compatible URLs and starts Compare from the current detail build", () => {
  expect(compareUrl("left id", "right/id")).toBe("/compare?left=left+id&right=right%2Fid");
  const html = renderToStaticMarkup(<MemoryRouter initialEntries={["/builds/left"]}>
    <Routes><Route path="/builds/:id" element={<BuildDetails />} /></Routes>
  </MemoryRouter>);
  expect(html).toContain('href="/compare?left=left"');
  expect(html).toContain(">Compare</a>");
  route(compareUrl(left.id, right.id));
  expect(vi.mocked(useLoad)).toHaveBeenCalledWith("/builds/left");
  expect(vi.mocked(useLoad)).toHaveBeenCalledWith("/builds/right");
});
