import { act, cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { api } from "../api";
import type { Build, MachinePart } from "../types";
import { BuildDetails } from "./BuildDetails";

const actor = { id: "owner", username: "driver" };
vi.mock("../auth", () => ({ useAuth: () => ({ user: actor }) }));
vi.mock("../api", async (original) => ({ ...await original<typeof import("../api")>(), api: vi.fn() }));
const part = (type: MachinePart["type"]): MachinePart => ({ id: type, type,
  sourceMachineId: "machine", sourceMachineName: "Machine", sourceMachineImagePath: null,
  racingType: "SPEED" });
const build = (id: string): Build => ({
  id, title: `Build ${id}`, description: "", author: actor,
  racer: { id: "racer", name: "Sonic", racingType: "SPEED", imagePath: null },
  frontPart: part("FRONT"), rearPart: part("REAR"), tirePart: part("TIRE"),
  gameVersion: null, gadgets: [], remixedFrom: id === "A" ? { id: "B", title: "Source B" } : null,
  createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
  score: id === "A" ? 10 : 20, upvotes: id === "A" ? 10 : 20, downvotes: 0,
});
let finishMutation: (value: unknown) => void;
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api).mockImplementation(async (path, options) => {
    if (path === "/game-versions") return [];
    if (options?.method === "DELETE") return new Promise((resolve) => { finishMutation = resolve; });
    const id = path.split("/")[2];
    if (path.includes("/comments")) return {
      items: [], total: id === "A" ? 21 : 0,
      page: Number(new URLSearchParams(path.split("?")[1]).get("page")), size: 20,
    };
    if (path.endsWith("/vote")) {
      if (options?.method) return new Promise((resolve) => { finishMutation = resolve; });
      return { score: build(id).score, upvotes: build(id).upvotes, downvotes: 0, myVote: 0 };
    }
    return build(id);
  });
});

it("does not navigate away from B when A's pending deletion completes", async () => {
  render(<MemoryRouter initialEntries={["/builds/A"]}><Routes>
    <Route path="/builds/:id" element={<BuildDetails />} />
    <Route path="/my-builds" element={<p>My builds destination</p>} />
  </Routes></MemoryRouter>);
  const user = userEvent.setup();
  await screen.findByRole("heading", { name: "Build A" });
  await user.click(screen.getByRole("button", { name: /^Delete$/ }));
  await user.click(screen.getByRole("button", { name: "Yes, delete build" }));
  await user.click(screen.getByRole("link", { name: "Source B" }));
  await screen.findByRole("heading", { name: "Build B" });
  await act(async () => { finishMutation(undefined); });
  expect(screen.getByRole("heading", { name: "Build B" })).toBeTruthy();
});
afterEach(cleanup);

it("isolates drafts, confirmation, pagination and a delayed vote across a remix-source navigation", async () => {
  const view = render(<MemoryRouter initialEntries={["/builds/A"]}><Routes>
    <Route path="/builds/:id" element={<BuildDetails />} />
  </Routes></MemoryRouter>);
  const user = userEvent.setup();
  await screen.findByRole("heading", { name: "Build A" });
  await user.type(screen.getByRole("textbox"), "A's draft");
  await user.click(screen.getByRole("button", { name: /^Delete$/ }));
  await user.click(screen.getByRole("button", { name: "Next" }));
  await screen.findByText("Page 2");
  await user.click(screen.getByRole("button", { name: "↑ Upvote" }));
  await user.click(screen.getByRole("link", { name: "Source B" }));
  await screen.findByRole("heading", { name: "Build B" });
  expect((screen.getByRole("textbox") as HTMLTextAreaElement).value).toBe("");
  expect(screen.queryByRole("button", { name: "Yes, delete build" })).toBeNull();
  expect(screen.queryByText("Page 2")).toBeNull();
  expect(api).toHaveBeenCalledWith("/builds/B/comments?page=0&size=20", expect.anything());
  expect((screen.getByRole("button", { name: "↑ Upvote" }) as HTMLButtonElement).disabled).toBe(false);
  await act(async () => { finishMutation({ score: 11, upvotes: 11, downvotes: 0, myVote: 1 }); });
  expect(view.container.querySelector(".big-score")?.textContent).toBe("20");
  expect(screen.getByRole("button", { name: "↑ Upvote" }).getAttribute("aria-pressed")).toBe("false");
});
