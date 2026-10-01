import { renderToStaticMarkup } from "react-dom/server";
import { expect, it } from "vitest";
import { BrowserRouter } from "react-router-dom";
import { SiteFooter } from "./SiteFooter";

it("credits Meohong visibly and links the documented sources", () => {
  const html = renderToStaticMarkup(
    <BrowserRouter><SiteFooter /></BrowserRouter>,
  );

  expect(html).toContain("With thanks to <strong>Meohong</strong>");
  expect(html).toContain('href="https://www.srcgadgetbuilder.com/"');
  expect(html).toContain("Special thanks to Meohong, creator of");
  expect(html).toContain('href="https://sonic.fandom.com/wiki/Sonic_Racing:_CrossWorlds"');
  expect(html).toContain('href="https://w.atwiki.jp/sonicracingcw/"');
  expect(html).toContain('href="/guide"');
});
