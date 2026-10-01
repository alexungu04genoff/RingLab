import { renderToStaticMarkup } from "react-dom/server";
import { expect, it } from "vitest";
import { CollectionArtwork as Artwork } from "./CollectionArtwork";
import type { Gadget } from "../../shared/types";

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
