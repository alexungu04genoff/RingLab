import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import { useLoad } from "../useLoad";
import type { Build, MachinePart } from "../types";
import { BuildDetails } from "./BuildDetails";
import { draftFromRemix } from "./BuildEditor";

vi.mock("../useLoad", () => ({ useLoad: vi.fn() }));
vi.mock("../api", () => ({ api: vi.fn(), json: vi.fn() }));
let currentUser: { id: string } | null = { id: "remixer" };
vi.mock("../auth", () => ({ useAuth: () => ({ user: currentUser }) }));

const part = (type: MachinePart["type"]): MachinePart => ({
  id: type.toLowerCase(), type, sourceMachineId: "machine", sourceMachineName: "Speedster",
  sourceMachineImagePath: null, racingType: "SPEED",
});
const source: Build = {
  id: "source", title: "Original route", description: "Keep this setup note",
  author: { id: "original-author", username: "amy" },
  racer: { id: "racer", name: "Sonic", racingType: "SPEED", imagePath: null },
  frontPart: part("FRONT"), rearPart: part("REAR"), tirePart: part("TIRE"),
  gameVersion: { id: "version", version: "1.4.1", releasedAt: "2026-06-23" },
  remixedFrom: null,
  gadgets: [
    { id: "second", name: "Second", description: null, slotCost: 1, imagePath: null },
    { id: "first", name: "First", description: null, slotCost: 1, imagePath: null },
  ],
  createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
  score: 12, upvotes: 15, downvotes: 3,
};

beforeEach(() => {
  currentUser = { id: "remixer" };
  vi.mocked(useLoad).mockImplementation((path: string) => ({
    data: path.includes("comments") ? { items: [], total: 0, page: 0, size: 20 }
      : path === "/game-versions" ? [source.gameVersion] : source,
    error: "", loading: false,
  }));
});

it("copies only editable configuration and preserves gadget order", () => {
  expect(draftFromRemix(source)).toEqual({
    title: "Remix of Original route",
    description: "Keep this setup note",
    racerId: "racer", frontPartId: "front", rearPartId: "rear", tirePartId: "tire",
    machineType: "SPEED",
    gameVersionId: "version", remixedFromBuildId: "source", gadgetIds: ["second", "first"],
  });
  expect(draftFromRemix(source)).not.toHaveProperty("author");
  expect(draftFromRemix(source)).not.toHaveProperty("score");
  expect(draftFromRemix(source)).not.toHaveProperty("comments");
});

it("preserves a Board family and its absent tire when remixing", () => {
  const boardPart = (type: "FRONT" | "REAR"): MachinePart => ({
    ...part(type), racingType: "BOOST",
  });
  const board = { ...source, frontPart: boardPart("FRONT"), rearPart: boardPart("REAR"), tirePart: null };

  expect(draftFromRemix(board)).toMatchObject({ machineType: "BOOST", tirePartId: null });
});

it("offers authenticated remix action and links provenance to its source", () => {
  const remix = { ...source, id: "remix", remixedFrom: { id: "source", title: "Original route" } };
  vi.mocked(useLoad).mockImplementation((path: string) => ({
    data: path.includes("comments") ? { items: [], total: 0, page: 0, size: 20 }
      : path === "/game-versions" ? [source.gameVersion] : remix,
    error: "", loading: false,
  }));
  const html = renderToStaticMarkup(<MemoryRouter><BuildDetails /></MemoryRouter>);
  expect(html).toContain('href="/builds/new?remixFrom=remix"');
  expect(html).toContain('>Remix this build</a>');
  expect(html).toContain('Remixed from <a href="/builds/source"');
  expect(html).toContain('>Original route</a>');
});

it("sends unauthenticated visitors through the existing login flow", () => {
  currentUser = null;
  const html = renderToStaticMarkup(<MemoryRouter><BuildDetails /></MemoryRouter>);
  expect(html).toContain('href="/login"');
  expect(html).toContain('>Log in to remix</a>');
  expect(html).not.toContain("Remix this build");
});
