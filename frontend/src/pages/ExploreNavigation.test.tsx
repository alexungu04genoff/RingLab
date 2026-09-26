import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, useLocation } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { useLoad } from "../useLoad";
import { Explore } from "./Explore";

vi.mock("../useLoad", () => ({ useLoad: vi.fn() }));
vi.mock("../auth", () => ({ useAuth: () => ({ user: null }) }));
vi.mock("../TopCommunityBuilds", () => ({ TopCommunityBuilds: () => null }));

beforeEach(() => {
  localStorage.clear();
  vi.clearAllMocks();
  const versions = [{ id: "v1", version: "1.4.1", releasedAt: "2026-06-23" }];
  vi.mocked(useLoad).mockImplementation((path: string) => ({
    data: path === "/game-versions" ? versions
      : path.startsWith("/builds?") ? { items: [], total: 0, page: 0, size: 12 } : [],
    error: "", loading: false,
  }));
});

afterEach(() => { cleanup(); localStorage.clear(); });

function LocationProbe() {
  const { key, search } = useLocation();
  return <output data-testid="location">{key}{search}</output>;
}

function openExplore(entry: string) {
  render(<MemoryRouter initialEntries={[entry]}><LocationProbe /><Explore /></MemoryRouter>);
}

it("ignores an unavailable saved patch without repeatedly replacing the URL", () => {
  localStorage.setItem("ringlab.explore.gameVersionId", "removed-patch");
  openExplore("/?sort=rated");

  expect(screen.getByTestId("location").textContent).toBe("default?sort=rated");
  expect((screen.getByRole("combobox", { name: "Search patch" }) as HTMLInputElement).value)
    .toBe("All versions");
  expect(vi.mocked(useLoad).mock.calls.filter(([path]) => path.startsWith("/builds?"))
    .every(([path]) => !path.includes("gameVersionId"))).toBe(true);
});

it("removing the sort chip clears the saved preference and restores Best rated", async () => {
  localStorage.setItem("ringlab.explore.sort", "score");
  openExplore("/?sort=score&page=2");
  await userEvent.click(screen.getByRole("button", { name: "Remove Sort: Highest score" }));

  expect(localStorage.getItem("ringlab.explore.sort")).toBeNull();
  expect(screen.getByTestId("location").textContent).toMatch(/\?sort=rated$/);
  expect(screen.queryByRole("button", { name: "Remove Sort: Highest score" })).toBeNull();
  expect((screen.getByRole("combobox", { name: /Sort by/ }) as HTMLSelectElement).value).toBe("rated");
});
