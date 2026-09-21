import { renderToStaticMarkup } from "react-dom/server";
import { expect, it } from "vitest";
import { BrowserRouter } from "react-router-dom";
import { Artwork, ItemSelect, SiteFooter } from "./components";
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
  expect(html).toContain('src="/assets/racers/sample-racer.png?v=left-facing-artwork"');
  expect(html).toContain('alt="Sample Racer"');
});

it("keeps unknown-type entries selectable alongside known racing types", () => {
  const html = renderToStaticMarkup(
    <ItemSelect label="Racer" value="" onChange={() => {}}
      items={[racer, { ...racer, id: "sonic", name: "Sonic", racingType: "SPEED" }]} />,
  );
  expect(html).toContain('<option value="blinky" class="typed-option racing-type-unknown">Blinky · ● Unknown</option>');
  expect(html).toContain('<option value="sonic" class="typed-option racing-type-speed">Sonic · ● Speed</option>');
});

it("credits Meohong visibly and links the documented sources", () => {
  const html = renderToStaticMarkup(
    <BrowserRouter><SiteFooter /></BrowserRouter>,
  );

  expect(html).toContain("With thanks to <strong>Meohong</strong>");
  expect(html).toContain('href="https://www.srcgadgetbuilder.com/"');
  expect(html).toContain("Special thanks to Meohong, creator of");
  expect(html).toContain('href="https://sonic.fandom.com/wiki/Sonic_Racing:_CrossWorlds"');
  expect(html).toContain('href="https://w.atwiki.jp/sonicracingcw/"');
});
