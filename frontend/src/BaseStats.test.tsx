// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { buildStatsPath, BuildStats, calculateStatsBreakdown, DraftStats, StatsBlock } from "./BaseStats";
import { BuildCard } from "./components";
import type { BaseStats, Build, BuildDraft, MachinePart, StatsCatalog } from "./types";

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
const complete: BaseStats = { speed: 17.5, acceleration: 0, handling: 12, power: 9, boost: 8 };
const calculated: BaseStats = { ...complete, acceleration: 5 };
const draft: BuildDraft = { title: "", description: "", racerId: "r", frontPartId: "f", rearPartId: "b",
  tirePartId: "t", gameVersionId: "v", remixedFromBuildId: null, gadgetIds: [] };
const version = { id: "v", version: "1.3.1", releasedAt: "2026-03-18" };
const catalog: StatsCatalog = { gameVersionId: "v",
  racers: { r: { speed: 5, acceleration: 2, handling: 3, power: 1, boost: 2 } },
  machineParts: {
    f: { speed: 4, acceleration: 1, handling: 3, power: 2, boost: 2 },
    b: { speed: 4, acceleration: 1, handling: 3, power: 3, boost: 2 },
    t: { speed: 4.5, acceleration: 1, handling: 3, power: 3, boost: 2 },
  }, machines: {} };

it("renders known values including true zero and decimals", () => {
  render(<StatsBlock stats={complete} version="1.3.1" />);
  expect(screen.getByText("17.5")).toBeTruthy();
  expect(screen.getByText("0")).toBeTruthy();
  expect(screen.queryByText(/Partial/)).toBeNull();
  expect(screen.getByRole("progressbar", { name: "Speed: 17.5" }).getAttribute("aria-valuenow")).toBe("17.5");
  expect(screen.getAllByRole("progressbar")).toHaveLength(5);
});

it("renders a partial stat as a dash without losing known values", () => {
  render(<StatsBlock stats={{ ...complete, handling: null }} version="1.3.1" />);
  expect(screen.getByText("—")).toBeTruthy();
  expect(screen.getByText(/Partial stats/)).toBeTruthy();
  expect(screen.getByText("17.5")).toBeTruthy();
});

it("shows character and machine contributions separately from the total", () => {
  const breakdown = calculateStatsBreakdown(draft, catalog);
  render(<StatsBlock stats={complete} version="1.3.1" breakdown={breakdown} />);
  expect(screen.getByText("Character")).toBeTruthy();
  expect(screen.getByText("Machine")).toBeTruthy();
  expect(screen.getByLabelText("Speed calculation: Character 5 plus Machine 12.5 equals 17.5")).toBeTruthy();
  expect(screen.getByRole("progressbar", { name: "Speed: 17.5" }).querySelectorAll(".stat-fill")).toHaveLength(2);
});

it("renders a missing historical record as unavailable", () => {
  render(<StatsBlock version="1.3.1" />);
  expect(screen.getByText("Stats unavailable for Ver. 1.3.1")).toBeTruthy();
  expect(screen.getAllByText("—")).toHaveLength(5);
  expect(screen.getByRole("progressbar", { name: "Speed: unknown" }).getAttribute("aria-valuenow")).toBeNull();
});

it("requires an explicit version and does not make a preview request without one", () => {
  const fetch = vi.fn(); vi.stubGlobal("fetch", fetch);
  render(<DraftStats draft={{ ...draft, gameVersionId: null }} version={null} />);
  expect(screen.getByText("Select a game version to see stats.")).toBeTruthy();
  expect(fetch).not.toHaveBeenCalled();
  expect(buildStatsPath({ ...draft, gameVersionId: null })).toBe("");
});

it("explains missing versions on saved builds without suggesting a nonexistent selector", () => {
  const machinePart = (type: MachinePart["type"]): MachinePart => ({ id: type, type,
    sourceMachineId: "machine", sourceMachineName: "Machine", sourceMachineImagePath: null, racingType: "SPEED" });
  const build = { racer: { id: "r", name: "Blaze", racingType: "SPEED", imagePath: null },
    frontPart: machinePart("FRONT"), rearPart: machinePart("REAR"), tirePart: machinePart("TIRE"),
    gameVersion: null } as Build;
  render(<BuildStats build={build} />);
  expect(screen.getByText("Stats unavailable because this build has no recorded game version.")).toBeTruthy();
  expect(screen.queryByText("Select a game version to see stats.")).toBeNull();
});

it("requests backend totals for the selected components and excludes gadgets", async () => {
  const fetch = vi.fn().mockImplementation((url: string) => Promise.resolve(new Response(JSON.stringify(
    url.includes("/stats/catalog") ? catalog : calculated,
  ))));
  vi.stubGlobal("fetch", fetch);
  render(<DraftStats draft={draft} version={version} />);
  expect(screen.getByText("Loading base stats…")).toBeTruthy();
  await screen.findByRole("progressbar", { name: "Speed: 17.5" });
  expect(fetch.mock.calls[0][0]).toBe("/api/stats/build?gameVersionId=v&racerId=r&frontPartId=f&rearPartId=b&tirePartId=t");
  expect(fetch.mock.calls.some(([url]) => url === "/api/stats/catalog?gameVersionId=v")).toBe(true);
  expect(screen.getByLabelText("Speed calculation: Character 5 plus Machine 12.5 equals 17.5")).toBeTruthy();
  expect(screen.getByText(/Gadget effects are not included/)).toBeTruthy();
  expect(buildStatsPath({ ...draft, tirePartId: "" })).not.toContain("tirePartId");
});

it("shows compact version-aware stat bars on build cards", async () => {
  const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(calculated)));
  vi.stubGlobal("fetch", fetch);
  const machinePart = (type: MachinePart["type"]): MachinePart => ({ id: type, type,
    sourceMachineId: "machine", sourceMachineName: "Machine", sourceMachineImagePath: null, racingType: "SPEED" });
  const build = { id: "build", title: "Fast build", description: "", author: { id: "u", username: "driver" },
    racer: { id: "r", name: "Blaze", racingType: "SPEED", imagePath: null },
    frontPart: machinePart("FRONT"), rearPart: machinePart("REAR"), tirePart: machinePart("TIRE"),
    gameVersion: version, remixedFrom: null, gadgets: [], createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z", score: 0, upvotes: 0, downvotes: 0 } satisfies Build;
  render(<MemoryRouter><BuildCard build={build} /></MemoryRouter>);
  expect(screen.getByText("Loading stats…")).toBeTruthy();
  expect(await screen.findByText("17.5")).toBeTruthy();
  expect(fetch).toHaveBeenCalledWith(
    "/api/stats/build?gameVersionId=v&racerId=r&frontPartId=FRONT&rearPartId=REAR&tirePartId=TIRE",
    expect.anything(),
  );
});

it("reports preview errors and discards stale responses on selection changes", async () => {
  let resolveOld!: (value: Response) => void;
  const fetch = vi.fn().mockImplementation((url: string) => {
    if (url.includes("/stats/catalog")) return Promise.resolve(new Response(JSON.stringify(catalog)));
    if (url.includes("racerId=r&")) return new Promise<Response>((resolve) => { resolveOld = resolve; });
    return Promise.resolve(new Response(JSON.stringify({ message: "Unavailable" }), { status: 503 }));
  });
  vi.stubGlobal("fetch", fetch);
  const view = render(<DraftStats draft={draft} version={version} />);
  view.rerender(<DraftStats draft={{ ...draft, racerId: "new" }} version={version} />);
  await screen.findByText(/Could not load base stats/);
  resolveOld(new Response(JSON.stringify(complete)));
  await waitFor(() => expect(screen.queryByText("17.5")).toBeNull());
});
