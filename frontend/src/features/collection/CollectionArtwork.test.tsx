import { renderToStaticMarkup } from "react-dom/server";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, expect, it } from "vitest";
import { CollectionArtwork as Artwork } from "./CollectionArtwork";
import type { Gadget, Machine, Racer } from "../../shared/types";
afterEach(cleanup);

it("renders canonical icons including previously unwired assets and safely falls back on load errors", () => {
  const gadget: Gadget = { id: "84000000-0000-4000-8000-000000000037", name: "Air Trick Action Kit",
    description: null, slotCost: 3, imagePath: "/assets/gadgets/air-trick-action-kit.png" };
  render(<Artwork item={gadget} />);
  expect(screen.getByAltText(gadget.name).getAttribute("src")).toBe(gadget.imagePath);
  fireEvent.error(screen.getByAltText(gadget.name));
  expect(screen.queryByRole("img")).toBeNull(); expect(screen.getByText("AT")).toBeTruthy();
});

it("does not hotlink unreviewed external artwork", () => {
  render(<Artwork item={{ id: "unknown", name: "Unknown Gadget", slotCost: 1, description: null, imagePath: "https://example.com/icon.png" }} />);
  expect(screen.queryByRole("img")).toBeNull(); expect(screen.getByText("UG")).toBeTruthy();
});

it("uses existing initials for Phase 5B identities without reviewed artwork", () => {
  const identities: Array<Racer | Machine | Gadget> = [
    { id: "81000000-0000-4000-8000-000000000001", name: "Amigo", racingType: "HANDLING", imagePath: null },
    { id: "82000000-0000-4000-8000-000000000001", name: "Locomotive de Amigo", racingType: "POWER", imagePath: null },
    { id: "84000000-0000-4000-8000-000000000037", name: "Air Trick Action Kit", slotCost: 3, description: null, imagePath: null },
  ];
  identities.forEach((item, i) => {
    const html = renderToStaticMarkup(<Artwork item={item} />);
    expect(html).toContain([">A</span>", ">Ld</span>", ">AT</span>"][i]);
    expect(html).not.toContain("<img");
    expect(html).not.toContain("not-owned-artwork");
  });
});

it.each([
  ["052", "speed-tuner-1"], ["053", "speed-tuner-2"],
  ["054", "acceleration-tuner-1"], ["055", "acceleration-tuner-2"],
  ["056", "handling-tuner-1"], ["057", "handling-tuner-2"],
  ["058", "power-tuner-1"], ["059", "power-tuner-2"],
  ["060", "boost-tuner-1"], ["061", "boost-tuner-2"],
  ["062", "boost-machine-kit"],
])("renders wiki artwork for gadget %s without a catalog image path", (suffix, file) => {
  const gadget: Gadget = {
    id: `70000000-0000-4000-8000-000000000${suffix}`,
    name: "Display name may change", description: null, slotCost: 1, imagePath: null,
  };
  for (const compact of [false, true]) {
    const html = renderToStaticMarkup(<Artwork item={gadget} compact={compact} />);
    expect(html).toContain(`src="/assets/gadgets/${file}.webp"`);
    expect(html).toContain('alt="Display name may change"');
  }
});

it("prefers an explicit catalog image over the gadget fallback", () => {
  const gadget: Gadget = {
    id: "70000000-0000-4000-8000-000000000054", name: "Acceleration Tuner 1",
    description: null, slotCost: 1, imagePath: "/assets/gadgets/updated-tuner.png",
  };
  const html = renderToStaticMarkup(<Artwork item={gadget} />);
  expect(html).toContain('src="/assets/gadgets/updated-tuner.png"');
  expect(html).not.toContain("acceleration-tuner-1.webp");
});
