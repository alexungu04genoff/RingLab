import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, expect, it, vi } from "vitest";
import { GuidePage } from "./Guide";

const auth = vi.hoisted(() => ({ user: null as { id: string } | null }));
vi.mock("../features/auth/auth", () => ({ useAuth: () => auth }));

beforeEach(() => { auth.user = null; });

function render() { return renderToStaticMarkup(<MemoryRouter><GuidePage /></MemoryRouter>); }

it("provides the public guide sections in player-facing language", () => {
  const html = render();
  for (const anchor of ["finding-builds", "recommendations", "your-collection", "understanding-stats", "about-credits"])
    expect(html).toContain(`id="${anchor}"`);
  expect(html).toContain('id="why-ringlab"');
  expect(html).toContain("How RingLab works");
  expect(html).toContain("Best rated");
  expect(html).toContain("Highest score");
  expect(html).toContain("Apply to draft");
  expect(html).toContain("Tie-break / Ignore");
  expect(html).toContain("everything is treated as available");
  expect(html).toContain("Explore builds. Make them your own.");
  expect(html).toContain("Recommendations: build around your preferences");
  expect(html).toContain("You can combine these locks");
  expect(html).toContain("Selecting an item alone does not lock it");
  expect(html).toContain("it does not lock your racer or gadgets");
  expect(html).toContain("a front part, and two favourite gadgets");
  expect(html).toContain("Your locked items stay in place");
  expect(html).not.toContain("Recommendations: keep Tails");
  expect(html).not.toContain("Quick start");
  expect(html).not.toContain("Base/With gadgets");
  expect(html).not.toContain("Rule source");
  expect(html).not.toContain("Wilson");
  expect(html).not.toContain("ruleset");
  expect(html).not.toContain("search limit");
  expect(html).not.toContain("How the maths works");
});

it("shows login guidance only to signed-out readers", () => {
  expect(render()).toContain("Log in to manage your collection");
  auth.user = { id: "account" };
  expect(render()).not.toContain("Log in to manage your collection");
});
