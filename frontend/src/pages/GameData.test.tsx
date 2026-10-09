import { renderToStaticMarkup } from "react-dom/server";
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { useLoad } from "../shared/hooks/useLoad";
import { GameData, newestGameVersion, patchNotesUrl } from "./GameData";
import type { BaseStats, Gadget, Machine, MachinePart, Racer, ScenarioRulesCatalog } from "../shared/types";

vi.mock("../shared/hooks/useLoad", () => ({ useLoad: vi.fn() }));
afterEach(cleanup);

it("badges only catalog-supported scenarios, including descriptive events and combined conditions", () => {
  const names = ["Quick Starter", "Sea Dog", "Ace Pilot", "All-Rounder", "Ring Engine", "Perfect Landing", "Invincible Finish", "Evolution", "Hyper Ring Engine", "Combined"];
  const ids = ["lap", "70000000-0000-4000-8000-000000000015", "70000000-0000-4000-8000-000000000017", "70000000-0000-4000-8000-000000000018", "rings", "landing", "finish", "evolution", "hyper", "combined"];
  const gadgets = names.map((name, i) => ({ id: ids[i], name, description: null, imagePath: null, slotCost: 1 }));
  const fields: ScenarioRulesCatalog["controls"][number]["field"][] = ["LAP", "VEHICLE_FORM", "VEHICLE_FORM", "VEHICLE_FORM", "RINGS_HELD", "LANDING_BOOST_ACTIVE", "DISTANCE_TO_FINISH"];
  const scenarios: ScenarioRulesCatalog = { supportedVersion: "1.4.1", controls: [
    ...fields.map((field, i) => ({ gadgetId: ids[i], field, statEffect: i < 5 })),
    { gadgetId: "combined", field: "LAP", statEffect: true },
    { gadgetId: "combined", field: "RINGS_HELD", statEffect: true },
    { gadgetId: "combined", field: "LAP", statEffect: true },
  ] };
  const zero = { speed: 0, acceleration: 0, handling: 0, power: 0, boost: 0 };
  const rules = { supportedVersion: "1.4.1", ruleset: "test", note: "Global note", gadgets: ids.map(gadgetId => ({ gadgetId, effects: [{
    effectId: "other-0", label: "Conditional effect", kind: "CONDITIONAL", subject: "ANY", requiredType: null,
    matching: zero, nonMatching: zero, explanation: "Gadget-specific condition", sources: [], stackingGroup: null,
  }] })) };
  vi.mocked(useLoad).mockImplementation(path => ({ loading: false, error: "", data:
    path === "/stats/scenario-rules" ? scenarios : path === "/stats/gadget-rules" ? rules
      : path === "/gadgets" ? gadgets : [],
  }));
  render(<MemoryRouter><GameData /></MemoryRouter>);
  fireEvent.click(screen.getByRole("button", { name: "Gadgets" }));
  ["Lap", "Water", "Flight", "Form", "Rings", "Event", "Event", null, null, "Lap / Rings"].forEach((label, i) => {
    const card = screen.getByRole("heading", { name: names[i] }).closest("article")!;
    if (label) expect(within(card).getByText("Scenario modeled")).toBeTruthy();
    else expect(within(card).queryByText("Scenario modeled")).toBeNull();
    expect(within(card).getByText("Race condition")).toBeTruthy();
    expect(card.querySelectorAll(".gadget-effect-scenario").length).toBe(label ? 1 : 0);
  });
  expect(useLoad).toHaveBeenCalledWith("/stats/scenario-rules");
});

it("does not claim scenario support when the scenario catalog is unavailable", () => {
  vi.mocked(useLoad).mockImplementation(path => ({ loading: false, error: "", data:
    path === "/gadgets" ? [{ id: "lap", name: "Quick Starter", slotCost: 1, imagePath: null, description: null }]
      : path === "/stats/scenario-rules" || path === "/stats/gadget-rules" ? undefined : [],
  }));
  render(<MemoryRouter><GameData /></MemoryRouter>);
  fireEvent.click(screen.getByRole("button", { name: "Gadgets" }));
  expect(screen.getByRole("heading", { name: "Quick Starter" })).toBeTruthy();
  expect(screen.getByRole("heading", { name: "Quick Starter" }).closest("article")!.querySelector(".gadget-effect-scenario")).toBeNull();
});

it("searches every collection tab and links maps to explicit recommendations", () => {
  const racer = { id: "r", name: "Sonic", racingType: "SPEED", imagePath: null };
  const machine = { id: "m", name: "Speedster", racingType: "SPEED", imagePath: null };
  const gadget = { id: "g", name: "Ring Engine", description: null, slotCost: 1, imagePath: null };
  const map = { id: "map-a", name: "E-Stadium", category: "MAIN_COURSE", contentPack: null, imagePath: null, catalogOrder: 1 };
  const patch = { id: "v", version: "1.4.1", releasedAt: "2026-06-23" };
  vi.mocked(useLoad).mockImplementation(path => ({ loading: false, error: "", data:
    path === "/stats/gadget-rules" ? { ruleset: "test", supportedVersion: "1.4.1", note: "Reviewed subset", gadgets: [] }
      : path === "/stats/scenario-rules" ? { supportedVersion: "1.4.1", controls: [] }
      : path === "/racers" ? [racer] : path === "/machines" ? [machine] : path === "/gadgets" ? [gadget]
      : path === "/maps" ? [map] : path === "/game-versions" ? [patch] : path === "/machine-parts" ? []
        : { gameVersionId: "v", racers: {}, machines: {}, machineParts: {} },
  }));
  render(<MemoryRouter><GameData /></MemoryRouter>);
  for (const [tab, label, match] of [
    ["Racers", "Search racers", "Sonic"], ["Stock Machines", "Search stock machines", "Speedster"],
    ["Gadgets", "Search gadgets", "Ring Engine"], ["Maps", "Search maps", "E-Stadium"],
    ["Versions / Patches", "Search versions / patches", "1.4.1"],
  ]) {
    fireEvent.click(screen.getByRole("button", { name: tab }));
    const search = screen.getByLabelText(label);
    expect((search as HTMLInputElement).value).toBe("");
    fireEvent.change(search, { target: { value: "no-such-entry" } });
    expect(screen.getByText(/match your search/)).toBeTruthy();
    fireEvent.change(search, { target: { value: match.toUpperCase() } });
    expect(screen.queryByText(/match your search/)).toBeNull();
    if (tab === "Maps") expect(screen.getByRole("link", { name: "Find recommended builds →" }).getAttribute("href"))
      .toBe("/?mapId=map-a&includeAllMaps=false");
  }
});

it("renders verified gadget artwork, effects and singular/plural costs without filling unknown values", () => {
  const gadgets: Gadget[] = [
    { id: "known", name: "Handling Character Kit", description: "Supports collisions and Ring theft.",
      slotCost: 3, imagePath: "/assets/gadgets/handling-character-kit.png" },
    { id: "one", name: "Crash Pads", description: null, slotCost: 1, imagePath: null },
    { id: "unknown", name: "Ring Engine", description: null, slotCost: null, imagePath: null },
  ];
  vi.mocked(useLoad).mockImplementation(path => ({ data: path === "" || path === "/stats/gadget-rules" ? undefined : gadgets, loading: false, error: "" }));
  const html = renderToStaticMarkup(<MemoryRouter><GameData /></MemoryRouter>);
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
  const html = renderToStaticMarkup(<MemoryRouter><GameData /></MemoryRouter>);
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
  const html = renderToStaticMarkup(<MemoryRouter><GameData /></MemoryRouter>);
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

it("shows loaded stock parts when explicitly opened without another catalog request", () => {
  const machine: Machine = { id: "dark-reaper", name: "Dark Reaper", racingType: "SPEED", imagePath: null };
  const part = (type: MachinePart["type"]): MachinePart => ({
    id: type.toLowerCase(), type, sourceMachineId: machine.id, sourceMachineName: machine.name,
    sourceMachineImagePath: null, racingType: "SPEED",
  });
  const parts = [part("FRONT"), part("REAR"), part("TIRE")];
  const values: BaseStats = { speed: 11, acceleration: 12, handling: 13, power: 14, boost: 15 };
  vi.mocked(useLoad).mockImplementation((path) => ({ loading: false, error: "", data:
    path === "/racers" ? [] : path === "/machines" ? [machine] : path === "/machine-parts" ? parts
      : path === "/game-versions" ? [{ id: "latest", version: "1.4.1", releasedAt: "2026-06-23" }]
      : { gameVersionId: "latest", racers: {}, machines: { [machine.id]: values },
        machineParts: Object.fromEntries(parts.map(({ id }) => [id, values])) },
  }));

  render(<MemoryRouter><GameData /></MemoryRouter>);
  fireEvent.click(screen.getByRole("button", { name: /Stock Machines/ }));

  const calls = vi.mocked(useLoad).mock.calls.length;
  expect(screen.queryByLabelText("Front part stats")).toBeNull();
  fireEvent.click(screen.getByRole("button", { name: "Part stats" }));
  expect(vi.mocked(useLoad).mock.calls.length).toBe(calls);

  expect(screen.getByLabelText("Front part stats").textContent).toContain("Speed11");
  expect(screen.getByLabelText("Rear part stats").textContent).toContain("Acceleration12");
  expect(screen.getByLabelText("Tire part stats").textContent).toContain("Boost15");
  expect(screen.getAllByText("Ver. 1.4.1")).toHaveLength(3);
});
