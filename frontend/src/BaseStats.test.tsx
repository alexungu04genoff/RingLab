// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { buildStatsPath, DraftStats, StatsBlock } from "./BaseStats";
import type { BaseStats, BuildDraft } from "./types";

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
const complete: BaseStats = { speed: 17.5, acceleration: 0, handling: 12, power: 9, boost: 8 };
const draft: BuildDraft = { title: "", description: "", racerId: "r", frontPartId: "f", rearPartId: "b",
  tirePartId: "t", gameVersionId: "v", remixedFromBuildId: null, gadgetIds: [] };
const version = { id: "v", version: "1.3.1", releasedAt: "2026-03-18" };

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

it("requests backend totals for the selected components and excludes gadgets", async () => {
  const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(complete)));
  vi.stubGlobal("fetch", fetch);
  render(<DraftStats draft={draft} version={version} />);
  expect(screen.getByText("Loading base stats…")).toBeTruthy();
  await screen.findByText("17.5");
  expect(fetch.mock.calls[0][0]).toBe("/api/stats/build?gameVersionId=v&racerId=r&frontPartId=f&rearPartId=b&tirePartId=t");
  expect(screen.getByText(/Gadget effects are not included/)).toBeTruthy();
  expect(buildStatsPath({ ...draft, tirePartId: "" })).not.toContain("tirePartId");
});

it("reports preview errors and discards stale responses on selection changes", async () => {
  let resolveOld!: (value: Response) => void;
  const fetch = vi.fn().mockImplementationOnce(() => new Promise<Response>((resolve) => { resolveOld = resolve; }))
    .mockResolvedValueOnce(new Response(JSON.stringify({ message: "Unavailable" }), { status: 503 }));
  vi.stubGlobal("fetch", fetch);
  const view = render(<DraftStats draft={draft} version={version} />);
  view.rerender(<DraftStats draft={{ ...draft, racerId: "new" }} version={version} />);
  await screen.findByText(/Could not load base stats/);
  resolveOld(new Response(JSON.stringify(complete)));
  await waitFor(() => expect(screen.queryByText("17.5")).toBeNull());
});
