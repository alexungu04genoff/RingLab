import { useState } from "react";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter, useLocation } from "react-router-dom";
import { MapRecommendationControl, MapRecommendationPicker, MapThumbnail, MapFilter } from "./MapRecommendations";
import { allMaps, compareMapRecommendations, filterMaps, mapSummary } from "./mapSelection";
import { BuildCard } from "../builds/BuildCard";
import { BuildComparisonProvider } from "../builds/BuildComparison";
import { formatBuildForSharing } from "../builds/buildSharing";
import { draftFromRemix } from "../builds/useBuildDraft";
import type { Build, MapRecommendations, RaceMap } from "../../shared/types";

const maps: RaceMap[] = [
  { id: "a", name: "E-Stadium", category: "MAIN_COURSE", contentPack: null, imagePath: "/assets/maps/e-stadium.png", catalogOrder: 1 },
  { id: "b", name: "Magma Planet", category: "CROSSWORLD", contentPack: null, imagePath: null, catalogOrder: 2 },
  { id: "c", name: "Minecraft World", category: "MAIN_COURSE", contentPack: "Minecraft", imagePath: null, catalogOrder: 3 },
];
const selected = (items = maps): MapRecommendations => ({ mode: "SELECTED", maps: items });
const part = { id: "p", sourceMachineId: "m", sourceMachineName: "Machine", sourceMachineImagePath: null, racingType: "SPEED" as const };
const build: Build = { id: "build", title: "Test setup", description: "", author: { id: "u", username: "driver" },
  racer: { id: "r", name: "Racer", racingType: "SPEED", imagePath: null }, frontPart: { ...part, type: "FRONT" },
  rearPart: { ...part, type: "REAR" }, tirePart: { ...part, type: "TIRE" }, gameVersion: null,
  gadgets: [], remixedFrom: null, mapRecommendations: selected(), score: 0, upvotes: 0, downvotes: 0,
  createdAt: "2026-01-01", updatedAt: "2026-01-01" };

beforeEach(() => {
  // jsdom does not implement native modal behavior; browser checks cover focus trapping/Escape.
  Object.defineProperty(HTMLDialogElement.prototype, "showModal", { configurable: true,
    value: function (this: HTMLDialogElement) { this.open = true; } });
  Object.defineProperty(HTMLDialogElement.prototype, "close", { configurable: true,
    value: function (this: HTMLDialogElement) { this.open = false; } });
});
afterEach(() => { cleanup(); Reflect.deleteProperty(HTMLDialogElement.prototype, "showModal");
  Reflect.deleteProperty(HTMLDialogElement.prototype, "close"); vi.restoreAllMocks(); });

it("compares sets in stable order and keeps All distinct from a full catalog selection", () => {
  expect(compareMapRecommendations(selected(), selected([...maps].reverse())).different).toBe(false);
  expect(compareMapRecommendations(allMaps, selected()).different).toBe(true);
  expect(compareMapRecommendations(allMaps, allMaps).different).toBe(false);
  const result = compareMapRecommendations(selected(maps.slice(0, 2)), selected(maps.slice(1)));
  expect(result.common).toEqual([maps[1]]);
  expect(result.onlyLeft).toEqual([maps[0]]);
  expect(result.onlyRight).toEqual([maps[2]]);
  expect(compareMapRecommendations(allMaps, selected()).common).toEqual([]);
  expect(mapSummary(allMaps)).toBe("All maps");
  expect(mapSummary(selected(), 2)).toBe("E-Stadium, Magma Planet +1 more");
  expect(filterMaps(maps, "  MAGMA ", "CROSSWORLD")).toEqual([maps[1]]);
  expect(filterMaps(maps, "Magma", "MAIN_COURSE")).toEqual([]);
});

it("copies recommendations independently for a remix and shares current names", () => {
  const draft = draftFromRemix(build);
  expect(draft.recommendedMapIds).toEqual(["a", "b", "c"]);
  expect(draft.mapRecommendationMode).toBe("SELECTED");
  draft.recommendedMapIds.pop();
  expect(build.mapRecommendations.maps).toHaveLength(3);
  expect(formatBuildForSharing(build, "http://localhost/builds/build")).toContain("E-Stadium, Magma Planet, Minecraft World");
  expect(formatBuildForSharing({ ...build, mapRecommendations: allMaps }, "url")).toContain("**Recommended maps:** All maps");
});

it("opens a card map dialog by keyboard without navigation or comparison changes", async () => {
  function Location() { return <output aria-label="Location">{useLocation().pathname}</output>; }
  const { container } = render(<MemoryRouter><BuildComparisonProvider><Location /><BuildCard build={build} /></BuildComparisonProvider></MemoryRouter>);
  const trigger = screen.getByRole("button", { name: "Recommended maps for Test setup: 3" });
  expect(trigger.closest("a")).toBeNull();
  expect(trigger.nextElementSibling?.getAttribute("aria-label")).toBe("Machine type: Speed");
  trigger.focus();
  await userEvent.keyboard("{Enter}");
  expect(screen.getByRole("dialog").textContent).toContain("Minecraft World");
  expect(screen.getByLabelText("Location").textContent).toBe("/");
  expect(screen.getByRole("button", { name: "Compare Test setup" }).getAttribute("aria-pressed")).toBe("false");
  expect(container.querySelector('a button')).toBeNull();
  fireEvent(screen.getByRole("dialog"), new Event("cancel", { bubbles: true, cancelable: true }));
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(document.activeElement).toBe(trigger);
  await userEvent.click(trigger);
  await userEvent.click(screen.getByRole("button", { name: "Close recommended maps" }));
  expect(document.activeElement).toBe(trigger);
  await userEvent.click(trigger);
  fireEvent.click(screen.getByRole("dialog"), { clientX: -1, clientY: -1 });
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(document.activeElement).toBe(trigger);
  expect(trigger.textContent).toContain("Map 3");
});

it("shows a short All explanation and labelled fallback after an artwork failure", async () => {
  render(<><MapRecommendationControl title="General" recommendations={allMaps} /><MapThumbnail map={maps[0]} /></>);
  fireEvent.error(screen.getByRole("img", { name: "E-Stadium" }));
  expect(screen.getByRole("img", { name: "E-Stadium: artwork unavailable" })).toBeTruthy();
  await userEvent.click(screen.getByRole("button", { name: "Recommended maps for General: All" }));
  expect(screen.getByText("No specific maps selected.")).toBeTruthy();
  expect(screen.queryByText("Magma Planet")).toBeNull();
});

it("selects searches removes and explicitly clears a bounded picker", async () => {
  function Picker() {
    const [value, setValue] = useState<MapRecommendations> (allMaps);
    return <MapRecommendationPicker maps={maps} mode={value.mode} selectedIds={value.maps.map(m => m.id)}
      loading={false} error="" onChange={(mode, ids) => setValue({ mode, maps: maps.filter(m => ids.includes(m.id)) })} />;
  }
  render(<Picker />);
  expect(screen.queryByLabelText("Search maps")).toBeNull();
  await userEvent.click(screen.getByRole("radio", { name: "Specific maps" }));
  expect(screen.getByText("Select at least one map, or choose All maps.")).toBeTruthy();
  for (const name of ["E-Stadium", "Magma Planet", "Minecraft World"]) await userEvent.click(screen.getByRole("checkbox", { name: new RegExp(name) }));
  expect(screen.getByText("3 selected")).toBeTruthy();
  expect((screen.getByRole("radio", { name: "Specific maps" }) as HTMLInputElement).checked).toBe(true);
  await userEvent.type(screen.getByLabelText("Search maps"), "Magma");
  expect(screen.getAllByRole("checkbox")).toHaveLength(1);
  await userEvent.click(screen.getByRole("checkbox"));
  expect(screen.getByText("2 selected")).toBeTruthy();
  await userEvent.selectOptions(screen.getByLabelText("Course category"), "MAIN_COURSE");
  expect(screen.getByText("No maps match your search.")).toBeTruthy();
  await userEvent.click(screen.getByRole("radio", { name: "All maps" }));
  await userEvent.click(screen.getByRole("radio", { name: "Specific maps" }));
  expect(screen.getByText("0 selected")).toBeTruthy();
});

it("keeps selected IDs visible as a count when the catalog cannot load", () => {
  render(<MapRecommendationPicker maps={[]} mode="SELECTED" selectedIds={["a"]}
    loading={false} error="Unavailable" onChange={vi.fn()} />);
  expect(screen.getByRole("alert").textContent).toContain("Your selection is preserved");
  expect(screen.getByText("1 selected")).toBeTruthy();
});

it("includes general recommendations by default and supports explicit-only filtering", async () => {
  const change = vi.fn();
  const { rerender } = render(<MapFilter maps={maps} mapId="" includeAllMaps={true} onChange={change} />);
  expect((screen.getByRole("checkbox") as HTMLInputElement).disabled).toBe(true);
  rerender(<MapFilter maps={maps} mapId="a" includeAllMaps={true} onChange={change} />);
  await userEvent.click(screen.getByRole("checkbox"));
  expect(change).toHaveBeenLastCalledWith("a", false);
});
