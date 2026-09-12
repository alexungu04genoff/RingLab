import { renderToStaticMarkup } from "react-dom/server";
import { expect, it, vi } from "vitest";
import { useLoad } from "../useLoad";
import { GameData } from "./GameData";
import type { Gadget } from "../types";

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
