import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import { BuildCard, countLabel, patchAge } from "../components";
import { useLoad } from "../useLoad";
import type { Build, MachinePart } from "../types";
import { BuildEditor } from "./BuildEditor";
import { BuildDetails } from "./BuildDetails";

vi.mock("../useLoad", () => ({ useLoad: vi.fn() }));
vi.mock("../api", () => ({ api: vi.fn(), json: vi.fn() }));
vi.mock("../auth", () => ({ useAuth: () => ({ user: null }) }));

const part = (type: MachinePart["type"], source = "Dark Reaper"): MachinePart => ({
  id: `${source}-${type}`, type, sourceMachineId: source, sourceMachineName: source,
  sourceMachineImagePath: `/assets/machines/${source.toLowerCase().replaceAll(" ", "-")}.png`, racingType: "SPEED",
});
const stock: Build = {
  id: "build", title: "My setup", description: "", author: { id: "author", username: "driver" },
  racer: { id: "racer", name: "Shadow", racingType: "SPEED", imagePath: null },
  frontPart: part("FRONT"), rearPart: part("REAR"), tirePart: part("TIRE"),
  gameVersion: null,
  gadgets: [{ id: "gadget", name: "Ring Engine", description: "Gain rings over time.", slotCost: 1,
    imagePath: "/assets/gadgets/ring-engine.png" }],
  createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z", score: 39, upvotes: 40, downvotes: 1,
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
  const selectors = [...html.matchAll(/<label class="part-select">(Front|Rear|Tires)<select([^>]*)>(.*?)<\/select>/g)];
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
  expect(card).toContain('aria-label="Front: Dark Reaper"');
  expect(card).toContain('aria-label="Rear:');
  expect(card).toContain('aria-label="Tires: Dark Reaper"');
  expect(card.includes("Speedster Lightning")).toBe(mixed);
  expect(card).toContain('aria-label="Ring Engine: Gain rings over time."');
  expect(card).toContain('role="tooltip"><strong>Ring Engine</strong>');
  const details = renderToStaticMarkup(<MemoryRouter><BuildDetails /></MemoryRouter>);
  expect(details).toContain(mixed ? "Mixed setup" : "Stock setup");
  expect(details).toContain(`class="setup-indicator ${mixed ? "mixed-setup" : "stock-setup"}"`);
  expect(details).toContain("FRONT");
  expect(details).toContain("REAR");
  expect(details).toContain("TIRES");
  expect(details).toContain(`/assets/machines/${mixed ? "speedster-lightning" : "dark-reaper"}.png`);
  expect(details).toContain("Ring Engine");
  expect(details).toContain(">Copy setup</button>");
  expect(details).toContain('/assets/gadgets/ring-engine.png');
  expect(details).toContain("1 slot");
  expect(details).toContain("Gain rings over time.");
  expect(details).toContain("Gadget Plate · valid · 1 / 6 slots");
  expect(details).toContain("Valid · 1 / 6 slots");
  expect(details).toContain("Gadgets <span class=\"muted\">· 1</span>");
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
  expect(card).toContain('aria-label="40 upvotes, 1 downvote"');
  expect(card).toContain('class="upvote-count">↑ 40</span>');
  expect(card).toContain('class="downvote-count">↓ 1</span>');
});

it("renders separate vote counts on cards and net score plus counts on details", () => {
  build = { ...stock, score: -20, upvotes: 20, downvotes: 40 };
  const card = renderToStaticMarkup(<MemoryRouter><BuildCard build={build} /></MemoryRouter>);
  const details = renderToStaticMarkup(<MemoryRouter><BuildDetails /></MemoryRouter>);
  expect(card).toContain('aria-label="20 upvotes, 40 downvotes"');
  expect(card).toContain('class="upvote-count">↑ 20</span>');
  expect(card).toContain('class="downvote-count">↓ 40</span>');
  expect(details).toContain('aria-live="polite">-20</strong>');
  expect(details).toContain('class="upvote-count">↑ 20</span>');
  expect(details).toContain('class="downvote-count">↓ 40</span>');
  expect(card + details).not.toContain("↑ -20");
});

it("renders a safe invalid Gadget Plate status and keeps the validation caveat visible", () => {
  build = {
    ...stock,
    gadgets: [{ id: "invalid", name: "Invalid", description: null, slotCost: 4, imagePath: null }],
  };
  const details = renderToStaticMarkup(<MemoryRouter><BuildDetails /></MemoryRouter>);
  expect(details).toContain('class="gadget-plate-status invalid"');
  expect(details).toContain("invalid cost: Invalid");
  expect(details).toContain("Gadget Plate capacity is validated using current catalog costs.");
  expect(details).toContain("Other gadget compatibility rules are not modeled.");
});

it("uses singular and plural vote labels in accessible text", () => {
  expect(countLabel(1, "upvote")).toBe("1 upvote");
  expect(countLabel(2, "upvote")).toBe("2 upvotes");
  build = { ...stock, upvotes: 1, downvotes: 1 };
  const card = renderToStaticMarkup(<MemoryRouter><BuildCard build={build} /></MemoryRouter>);
  const details = renderToStaticMarkup(<MemoryRouter><BuildDetails /></MemoryRouter>);
  expect(card).toContain('aria-label="1 upvote, 1 downvote"');
  expect(details).toContain('aria-label="Vote breakdown: 1 upvote, 1 downvote"');
});

it("classifies the first catalog version as latest known and later entries as older", () => {
  const latest = { id: "latest", version: "1.4.1", releasedAt: "2026-06-23" };
  const older = { id: "older", version: "1.3.1", releasedAt: "2026-03-18" };
  expect(patchAge(latest, [latest, older])).toBe("latest");
  expect(patchAge(older, [latest, older])).toBe("older");
  expect(patchAge(null, [latest, older])).toBe("unspecified");
});

it("renders accessible older, latest-known, and unspecified patch card states", () => {
  const latest = { id: "latest", version: "1.4.1", releasedAt: "2026-06-23" };
  const older = { id: "older", version: "1.3.1", releasedAt: "2026-03-18" };
  const renderCard = (gameVersion: Build["gameVersion"]) => renderToStaticMarkup(
    <MemoryRouter><BuildCard build={{ ...stock, gameVersion }} versions={[latest, older]} /></MemoryRouter>,
  );
  expect(renderCard(older)).toContain('class="patch-badge patch-older">Ver. 1.3.1');
  expect(renderCard(older)).not.toContain("Older patch");
  expect(renderCard(latest)).toContain('class="patch-badge patch-latest">Ver. 1.4.1');
  expect(renderCard(latest)).not.toContain("Older patch");
  expect(renderCard(null)).toContain('class="patch-badge patch-unspecified">Patch unspecified');
});

it("warns on older build details but not latest-known or unspecified versions", () => {
  const latest = { id: "version", version: "1.4.1", releasedAt: "2026-06-23" };
  const older = { id: "older", version: "1.3.1", releasedAt: "2026-03-18" };
  vi.mocked(useLoad).mockImplementation((path: string) => {
    if (path === "/game-versions") return { data: [latest, older], error: "", loading: false };
    if (path.includes("/comments")) return { data: { items: [], total: 0, page: 0, size: 20 }, error: "", loading: false };
    return { data: build, error: "", loading: false };
  });
  build = { ...stock, gameVersion: older };
  expect(renderToStaticMarkup(<MemoryRouter><BuildDetails /></MemoryRouter>))
    .toContain("Built for an older patch. Behavior may differ in newer versions.");
  build = { ...stock, gameVersion: latest };
  expect(renderToStaticMarkup(<MemoryRouter><BuildDetails /></MemoryRouter>)).not.toContain("older patch");
  build = { ...stock, gameVersion: null };
  expect(renderToStaticMarkup(<MemoryRouter><BuildDetails /></MemoryRouter>)).not.toContain("older patch");
});
