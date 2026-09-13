import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useNavigate } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { api, ApiError } from "../api";
import type { Build, MachinePart } from "../types";
import { BuildEditor } from "./BuildEditor";

vi.mock("../auth", () => ({ useAuth: () => ({ user: { id: "owner" } }) }));
vi.mock("../api", async (original) => ({ ...await original<typeof import("../api")>(), api: vi.fn() }));
const part = (type: MachinePart["type"]): MachinePart => ({
  id: type, type, sourceMachineId: "machine", sourceMachineName: "Machine",
  sourceMachineImagePath: null, racingType: "SPEED",
});
const buildA: Build = {
  id: "A", title: "Build A draft", description: "A description", author: { id: "owner", username: "alex" },
  racer: { id: "racer", name: "Sonic", imagePath: null, racingType: "SPEED" },
  frontPart: part("FRONT"), rearPart: part("REAR"), tirePart: part("TIRE"), gadgets: [],
  gameVersion: null, remixedFrom: null, createdAt: "2026-01-01", updatedAt: "2026-01-01",
  score: 0, upvotes: 0, downvotes: 0,
};
const buildB = { ...buildA, id: "B", title: "Build B draft" };
let loadB: () => Promise<Build>;
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api).mockImplementation(async (path, options) => {
    if (options?.method === "PUT") return buildB;
    if (path === "/builds/A") return buildA;
    if (path === "/builds/B") return loadB();
    if (path === "/racers") return [buildA.racer];
    if (path === "/machine-parts") return [buildA.frontPart, buildA.rearPart, buildA.tirePart];
    return [];
  });
});
afterEach(cleanup);
async function openA() {
  let navigate!: ReturnType<typeof useNavigate>;
  function Navigation() { navigate = useNavigate(); return null; }
  render(<MemoryRouter initialEntries={["/builds/A/edit"]}><Navigation /><Routes>
    <Route path="/builds/:id/edit" element={<BuildEditor />} />
    <Route path="/builds/:id" element={<p>Saved destination</p>} />
  </Routes></MemoryRouter>);
  await waitFor(() => expect((screen.getByLabelText("Build title") as HTMLInputElement).value).toBe("Build A draft"));
  return { navigate };
}
function expectNoWrite() {
  expect(vi.mocked(api).mock.calls.filter(([, options]) => options?.method === "PUT")).toEqual([]);
}

it.each(["network", "forbidden"])("removes A's draft when B fails with %s", async (failure) => {
  loadB = () => Promise.reject(failure === "network" ? new Error("Network unavailable") : new ApiError(403, "Forbidden"));
  const router = await openA();
  await act(async () => { await router.navigate("/builds/B/edit"); });
  await screen.findByRole("alert");
  expect(screen.queryByLabelText("Build title")).toBeNull();
  expect(screen.queryByRole("button", { name: "Save changes" })).toBeNull();
  expectNoWrite();
});

it("does not retain editing permission when B belongs to another author", async () => {
  loadB = () => Promise.resolve({ ...buildB, author: { id: "other", username: "other" } });
  const router = await openA();
  await act(async () => { await router.navigate("/builds/B/edit"); });
  await screen.findByText("Only the author may edit this build.");
  expect(screen.queryByLabelText("Build title")).toBeNull();
  expectNoWrite();
});

it("blocks submission during a delayed B load, then submits only B's loaded draft", async () => {
  let resolveB!: (build: Build) => void;
  loadB = () => new Promise((resolve) => { resolveB = resolve; });
  const router = await openA();
  await act(async () => { await router.navigate("/builds/B/edit"); });
  expect(screen.getByRole("status").textContent).toContain("Loading your build");
  expect(screen.queryByLabelText("Build title")).toBeNull();
  expectNoWrite();
  await act(async () => { resolveB(buildB); });
  const title = screen.getByLabelText("Build title") as HTMLInputElement;
  expect(title.value).toBe("Build B draft");
  fireEvent.submit(title.closest("form")!);
  await screen.findByText("Saved destination");
  const writes = vi.mocked(api).mock.calls.filter(([, options]) => options?.method === "PUT");
  expect(writes).toHaveLength(1);
  expect(writes[0][0]).toBe("/builds/B");
  expect(JSON.parse(writes[0][1]!.body as string).title).toBe("Build B draft");
});
