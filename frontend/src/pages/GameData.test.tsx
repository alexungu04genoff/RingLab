import { renderToStaticMarkup } from "react-dom/server";
import { expect, it, vi } from "vitest";
import { useLoad } from "../useLoad";
import { GameData, newestGameVersion, patchNotesUrl } from "./GameData";
import type { Gadget, Racer } from "../types";

vi.mock("../useLoad", () => ({ useLoad: vi.fn() }));

it("renders verified gadget artwork, effects and singular/plural costs without filling unknown values", () => {
  const gadgets: Gadget[] = [
    { id: "known", name: "Handling Character Kit", description: "Supports collisions and Ring theft.",
      slotCost: 3, imagePath: "/assets/gadgets/handling-character-kit.png" },
    { id: "one", name: "Crash Pads", description: null, slotCost: 1, imagePath: null },
    { id: "unknown", name: "Ring Engine", description: null, slotCost: null, imagePath: null },
  ];
  vi.mocked(useLoad).mockReturnValue({ data: gadgets, loading: false, error: "" });
  const html = renderToStaticMarkup(<GameData />);
  expect(html).toContain('src="/assets/gadgets/handling-character-kit.png"');
  expect(html).toContain("Supports collisions and Ring theft.");
  expect(html).toContain("3 slots");
  expect(html).toContain("1 slot");
  expect(html).not.toContain("1 slots");
  const unknown = html.slice(html.indexOf("<h2>Ring Engine</h2>"));
  expect(unknown).not.toContain("slots");
  expect(unknown).not.toContain("null");
  expect(html).not.toContain("0 slots");
  expect(html).toContain('aria-hidden="true">RE</span>');
});

it("uses the shared racing type colors in racer collection cards", () => {
  const racers: Racer[] = [
    { id: "sonic", name: "Sonic the Hedgehog", racingType: "SPEED", imagePath: "/assets/racers/sonic.png" },
  ];
  vi.mocked(useLoad).mockReturnValue({ data: racers, loading: false, error: "" });
  const html = renderToStaticMarkup(<GameData />);
  expect(html).toContain("racer-collection-item");
  expect(html).toContain("racing-type-speed");
  expect(html).toContain(">SPEED</span>");
});

it("links every catalog version to its Steam patch notes", () => {
  ["1.4.1", "1.3.1", "1.2.2", "1.2.0"].forEach((version) => {
    expect(patchNotesUrl(version)).toMatch(/^https:\/\/steam/);
  });
  expect(patchNotesUrl("unknown")).toBeNull();
});

it("uses the newest released patch for collection stats regardless of API ordering", () => {
  const racers: Racer[] = [
    { id: "known", name: "Historical racer", racingType: "SPEED", imagePath: null },
    { id: "later", name: "Later racer", racingType: "SPEED", imagePath: null },
  ];
  vi.mocked(useLoad).mockImplementation((path) => ({ loading: false, error: "", data:
    path === "/racers" ? racers : path === "/game-versions"
      ? [
        { id: "baseline", version: "1.3.1", releasedAt: "2026-03-18" },
        { id: "latest", version: "1.4.1", releasedAt: "2026-06-23" },
      ]
      : { gameVersionId: "latest", racers: { known: { speed: 20, acceleration: 5, handling: null, power: 15, boost: 7 } },
        machineParts: {}, machines: {} },
  }));
  const html = renderToStaticMarkup(<GameData />);
  expect(html).toContain("Historical racer");
  expect(html).toContain("Later racer");
  expect(html).toContain("Partial stats");
  expect(html).toContain("Stats unavailable for Ver. 1.4.1");
  expect(useLoad).toHaveBeenCalledWith("/stats/catalog?gameVersionId=latest");
});

it("selects the newest version by release date", () => {
  expect(newestGameVersion([
    { id: "latest", version: "1.4.1", releasedAt: "2026-06-23" },
    { id: "older", version: "1.3.1", releasedAt: "2026-03-18" },
  ])?.id).toBe("latest");
  expect(newestGameVersion(undefined)).toBeUndefined();
});
