import { renderToStaticMarkup } from "react-dom/server";
import { expect, it } from "vitest";
import { Artwork, ItemSelect } from "./components";
import type { Racer } from "./types";

const racer: Racer = {
  id: "blinky", name: "Blinky", racingType: null, imagePath: null,
};

it("renders initials for a racer with neither verified type nor artwork", () => {
  const html = renderToStaticMarkup(<Artwork item={racer} />);
  expect(html).toContain("type-unknown");
  expect(html).toContain('aria-hidden="true">B</span>');
  expect(html).not.toContain("<img");
});

it("shows official local artwork even when the racing type is unknown", () => {
  const html = renderToStaticMarkup(
    <Artwork item={{ ...racer, name: "Sample Racer", imagePath: "/assets/racers/sample-racer.png" }} />,
  );
  expect(html).toContain('src="/assets/racers/amigo.png"');
  expect(html).toContain('alt="Sample Racer"');
});

it("keeps unknown-type entries selectable alongside known racing types", () => {
  const html = renderToStaticMarkup(
    <ItemSelect label="Racer" value="" onChange={() => {}}
      items={[racer, { ...racer, id: "sonic", name: "Sonic", racingType: "SPEED" }]} />,
  );
  expect(html).toContain('<option value="blinky">Blinky · Unknown</option>');
  expect(html).toContain('<option value="sonic">Sonic · speed</option>');
});
