import { renderToStaticMarkup } from "react-dom/server";
import { expect, it } from "vitest";
import { ItemSelect } from "./ItemSelect";
import type { Racer } from "../../shared/types";

const racer: Racer = {
  id: "blinky", name: "Blinky", racingType: null, imagePath: null,
};

it("keeps unknown-type entries selectable alongside known racing types", () => {
  const html = renderToStaticMarkup(
    <ItemSelect label="Racer" value="" onChange={() => {}}
      items={[racer, { ...racer, id: "sonic", name: "Sonic", racingType: "SPEED" }]} />,
  );
  expect(html).toContain('<option value="blinky" class="typed-option racing-type-unknown">Blinky · ● Unknown</option>');
  expect(html).toContain('<option value="sonic" class="typed-option racing-type-speed">Sonic · ● Speed</option>');
});
