import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import { BuildCard } from "../components";
import { useLoad } from "../useLoad";
import type { Build, MachinePart } from "../types";
import { BuildEditor } from "./BuildEditor";
import { BuildDetails } from "./BuildDetails";

vi.mock("../useLoad", () => ({ useLoad: vi.fn() }));
vi.mock("../api", () => ({ api: vi.fn(), json: vi.fn() }));
vi.mock("../auth", () => ({ useAuth: () => ({ user: null }) }));

const part = (type: MachinePart["type"], source = "Dark Reaper"): MachinePart => ({
  id: `${source}-${type}`, type, sourceMachineId: source, sourceMachineName: source, racingType: "SPEED",
});
const stock: Build = {
  id: "build", title: "My setup", description: "", author: { id: "author", username: "driver" },
  racer: { id: "racer", name: "Shadow", racingType: "SPEED", imagePath: null },
  frontPart: part("FRONT"), rearPart: part("REAR"), tirePart: part("TIRE"),
  gameVersion: null,
  gadgets: [{ id: "gadget", name: "Ring Engine", description: null, slotCost: null, imagePath: null }],
  createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z", score: 39,
};
let build = stock;

beforeEach(() => {
  build = stock;
  vi.mocked(useLoad).mockImplementation((path: string) => {
    const data = path === "/machine-parts"
      ? [part("FRONT"), part("REAR"), part("TIRE"), part("FRONT", "Speedster Lightning"),
          part("REAR", "Speedster Lightning"), part("TIRE", "Speedster Lightning")]
      : path === "/racers" ? [stock.racer]
      : path === "/gadgets" ? stock.gadgets
      : path === "/game-versions" ? [{ id: "version", version: "1.4.1", releasedAt: "2026-06-23" }]
      : path.includes("/comments") ? { items: [], total: 0, page: 0, size: 20 }
      : build;
    return { data, error: "", loading: false };
  });
});

it("renders three required selectors containing only the correct part type and keeps gadgets separate", () => {
  const html = renderToStaticMarkup(<MemoryRouter><BuildEditor /></MemoryRouter>);
  const selectors = [...html.matchAll(/<label>(Front|Rear|Tires)<select([^>]*)>(.*?)<\/select>/g)];
  expect(selectors.map((match) => match[1])).toEqual(["Front", "Rear", "Tires"]);
  selectors.forEach((match, index) => {
    expect(match[2]).toContain("required");
    const type = ["FRONT", "REAR", "TIRE"][index];
    expect(match[3]).toContain(`value="Dark Reaper-${type}"`);
    expect(match[3]).toContain(`value="Speedster Lightning-${type}"`);
    expect(match[3]).not.toContain("Ring Engine");
    expect([...match[3].matchAll(/<option/g)]).toHaveLength(3);
  });
  expect(html).toContain('type="checkbox"');
  expect(html).toContain("Ring Engine");
  expect(html).toContain("Machine setup");
});

it.each([false, true])("renders stock or mixed details and compact cards (mixed=%s)", (mixed) => {
  build = mixed ? { ...stock, rearPart: part("REAR", "Speedster Lightning") } : stock;
  const card = renderToStaticMarkup(<MemoryRouter><BuildCard build={build} /></MemoryRouter>);
  expect(card).toContain(mixed ? "Mixed machine" : "Dark Reaper");
  expect(card).not.toContain("Speedster Lightning");
  const details = renderToStaticMarkup(<MemoryRouter><BuildDetails /></MemoryRouter>);
  expect(details).toContain("<dt>Front</dt><dd>Dark Reaper</dd>");
  expect(details).toContain(`<dt>Rear</dt><dd>${mixed ? "Speedster Lightning" : "Dark Reaper"}</dd>`);
  expect(details).toContain("<dt>Tires</dt><dd>Dark Reaper</dd>");
  expect(details).toContain("Ring Engine");
  expect(details).toContain('aria-live="polite">39</strong>');
});

it("offers an optional version selector defaulting to unspecified", () => {
  const html = renderToStaticMarkup(<MemoryRouter><BuildEditor /></MemoryRouter>);
  const selector = html.match(/Game version \/ Patch<select([^>]*)>(.*?)<\/select>/)!;
  expect(selector[1]).not.toContain("required");
  expect(selector[2]).toContain('value="" selected="">Unspecified');
  expect(selector[2]).toContain('value="version">Ver. 1.4.1');
});

it.each([false, true])("renders version metadata only when specified and preserves raw score (versioned=%s)", (versioned) => {
  build = { ...stock, gameVersion: versioned
    ? { id: "version", version: "1.4.1", releasedAt: "2026-06-23" } : null };
  const card = renderToStaticMarkup(<MemoryRouter><BuildCard build={build} /></MemoryRouter>);
  const details = renderToStaticMarkup(<MemoryRouter><BuildDetails /></MemoryRouter>);
  expect(card.includes("Ver. 1.4.1")).toBe(versioned);
  expect(details.includes("Ver. 1.4.1")).toBe(versioned);
  expect(details.includes("Released")).toBe(versioned);
  expect(details).toContain('aria-live="polite">39</strong>');
  expect(card).toContain('class="score">↑ 39');
});
