import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { CollectionProvider, MissingItems, OwnershipCheckbox, useCollection, type CollectionExclusions } from "./Collection";
import { api, setToken } from "./api";
import { Artwork } from "./components";
import { GameData } from "./pages/GameData";
import type { Build } from "./types";

const auth = vi.hoisted(() => ({ user: { id: "demo-a" } as { id: string } | null, loading: false, sessionError: "" }));
vi.mock("./auth", () => ({ useAuth: () => auth }));
vi.mock("./api", async original => ({ ...await original<typeof import("./api")>(), api: vi.fn() }));
vi.mock("./useLoad", () => ({ useLoad: (path: string) => ({ loading: false, error: "", data: path === "/racers"
  ? [{ id: "r", name: "Demo racer", racingType: "SPEED", imagePath: "/assets/racers/demo.png" }]
  : path === "/machines" ? [{ id: "m", name: "Demo source machine", racingType: "SPEED", imagePath: null }]
  : path === "/gadgets" ? [{ id: "g", name: "Demo gadget", description: "A useful gadget.", slotCost: 1, imagePath: null }]
  : path === "/game-versions" ? [{ id: "v", version: "1.4.1", releasedAt: "2026-06-23" }]
  : path.startsWith("/stats/catalog") ? { gameVersionId: "v", racers: {}, machines: {}, machineParts: {} }
  : path === "/stats/gadget-rules" ? { ruleset: "test", supportedVersion: "1.4.1", note: "Test", gadgets: [] } : [] }) }));
const empty: CollectionExclusions = { racers: [], machines: [], gadgets: [] };
const racer = { id: "r", name: "Demo racer", racingType: "SPEED" as const, imagePath: "/assets/racers/demo.png" };
function Status() { const state = useCollection(); return <output>{state.status}:{state.data.racers.join(",")}</output>; }
function Harness() { return <CollectionProvider><Status /><OwnershipCheckbox category="RACER" id="r" name="Demo racer" />
  <Artwork item={racer} portrait /></CollectionProvider>; }
beforeEach(() => {
  vi.clearAllMocks(); auth.user = { id: "demo-a" }; auth.loading = false; auth.sessionError = "";
  setToken("disposable-demo-token"); vi.mocked(api).mockResolvedValue(empty);
});
afterEach(cleanup);

it("defaults owned, persists explicit unchecks and restores ownership without hiding artwork", async () => {
  let exclusions = empty;
  vi.mocked(api).mockImplementation(async (path, options) => {
    if (options?.method === "PUT") { exclusions = JSON.parse(options.body as string).owned ? empty : { ...empty, racers: ["r"] }; return undefined; }
    return exclusions;
  });
  render(<Harness />);
  const checkbox = screen.getByRole("checkbox") as HTMLInputElement;
  await waitFor(() => expect(checkbox.checked).toBe(true));
  fireEvent.click(checkbox);
  await screen.findByText("Not owned");
  expect(screen.getByAltText("Demo racer").parentElement?.className).toContain("not-owned-artwork");
  expect(api).toHaveBeenCalledWith("/collection/RACER/r", expect.objectContaining({ method: "PUT", body: '{"owned":false}' }));
  fireEvent.click(checkbox);
  await waitFor(() => expect(checkbox.checked).toBe(true));
  expect(screen.queryByText("Not owned")).toBeNull();
});

it("discards late account responses and clears exclusions on logout", async () => {
  const resolve: ((value: CollectionExclusions) => void)[] = [];
  vi.mocked(api).mockImplementation(() => new Promise(done => resolve.push(done)));
  const view = render(<Harness />);
  auth.user = { id: "demo-b" }; setToken("demo-b"); view.rerender(<Harness />);
  await act(async () => resolve[0]({ ...empty, racers: ["r"] }));
  expect(screen.getByRole("status").textContent).toBe("loading:");
  await act(async () => resolve[1](empty));
  expect(screen.getByRole("status").textContent).toBe("ready:");
  auth.user = null; setToken(null); view.rerender(<Harness />);
  expect(screen.getByRole("status").textContent).toBe("anonymous:");
  expect(screen.queryByRole("checkbox")).toBeNull();
});

it("failed loads are unknown and disable ownership controls", async () => {
  vi.mocked(api).mockRejectedValue(new Error("Offline"));
  render(<Harness />);
  await waitFor(() => expect(screen.getByRole("status").textContent).toBe("error:"));
  expect((screen.getByRole("checkbox") as HTMLInputElement).disabled).toBe(true);
  expect(screen.queryByText("Not owned")).toBeNull();
});

it("does not accept malformed availability or a failed mutation as all-owned", async () => {
  vi.mocked(api).mockResolvedValueOnce({ racers: [] });
  const view = render(<Harness />);
  await waitFor(() => expect(screen.getByRole("status").textContent).toBe("error:"));
  expect((screen.getByRole("checkbox") as HTMLInputElement).indeterminate).toBe(true);
  view.unmount();
  vi.mocked(api).mockResolvedValueOnce(empty).mockRejectedValueOnce(new Error("Save failed"));
  render(<Harness />);
  await waitFor(() => expect(screen.getByRole("status").textContent).toBe("ready:"));
  fireEvent.click(screen.getByRole("checkbox"));
  await waitFor(() => expect(screen.getByRole("status").textContent).toBe("error:"));
  expect((screen.getByRole("checkbox") as HTMLInputElement).disabled).toBe(true);
});

it("a previous account's late mutation cannot refresh or overwrite the next account", async () => {
  let finish!: () => void;
  vi.mocked(api).mockResolvedValueOnce(empty).mockImplementationOnce(() => new Promise(resolve => { finish = () => resolve(undefined); }))
    .mockResolvedValueOnce({ ...empty, racers: ["r"] });
  const view = render(<Harness />);
  await waitFor(() => expect(screen.getByRole("status").textContent).toBe("ready:"));
  fireEvent.click(screen.getByRole("checkbox"));
  auth.user = { id: "demo-b" }; setToken("demo-b"); view.rerender(<Harness />);
  await screen.findByText("Not owned");
  await act(async () => finish());
  expect(screen.getByRole("status").textContent).toBe("ready:r");
  expect(api).toHaveBeenCalledTimes(3);
});

it("counts each mixed source once and identifies the missing categories", async () => {
  vi.mocked(api).mockResolvedValue({ racers: ["r"], machines: ["m", "other"], gadgets: ["g"] });
  const part = { sourceMachineId: "m", sourceMachineName: "Main source" };
  const build = { racer, frontPart: part, rearPart: part, tirePart: { sourceMachineId: "other", sourceMachineName: "Other source" },
    gadgets: [{ id: "g", name: "Demo gadget" }] } as Build;
  render(<CollectionProvider><MissingItems build={build} /></CollectionProvider>);
  await screen.findByText("Missing 4 items");
  expect(screen.getAllByText("Source machine: Main source")).toHaveLength(1);
  expect(screen.getByText("Source machine: Other source")).toBeTruthy();
  expect(screen.getByText("Racer: Demo racer")).toBeTruthy();
  expect(screen.getByText("Gadget: Demo gadget")).toBeTruthy();
});

it("excluded catalog items remain searchable with concise guidance and no dismiss action", async () => {
  vi.mocked(api).mockResolvedValue({ ...empty, racers: ["r"] });
  render(<MemoryRouter><CollectionProvider><GameData /></CollectionProvider></MemoryRouter>);
  await screen.findByText("Not owned");
  fireEvent.change(screen.getByLabelText("Search racers"), { target: { value: "demo" } });
  expect(screen.getByRole("heading", { name: "Demo racer" })).toBeTruthy();
  expect(screen.getByAltText("Demo racer").parentElement?.className).toContain("not-owned-artwork");
  expect(screen.getByText(/Everything is owned by default/)).toBeTruthy();
  expect(screen.queryByRole("button", { name: /Dismiss/ })).toBeNull();
  expect(screen.queryByText("Owned / unlocked")).toBeNull();
});

it("renders the shared ownership row inside racer, machine, and gadget cards", async () => {
  vi.mocked(api).mockResolvedValue({ racers: ["r"], machines: ["m"], gadgets: ["g"] });
  render(<MemoryRouter><CollectionProvider><GameData /></CollectionProvider></MemoryRouter>);
  for (const [tab, cardClass] of [["Racers", "racer-collection-item"], ["Stock Machines", "machine-collection-item"], ["Gadgets", "gadget-collection-item"]]) {
    fireEvent.click(screen.getByRole("button", { name: new RegExp(tab) }));
    const control = await screen.findByRole("checkbox");
    const card = control.closest("article");
    expect(card?.classList.contains(cardClass)).toBe(true);
    expect(control.closest("label")?.parentElement).toBe(card);
    expect(screen.getByText("Not owned").closest("article")).toBe(card);
  }
});

function LoginDestination() {
  const location = useLocation();
  return <output>return:{location.state?.from}</output>;
}

it("shows guests a contextual login notice without controls or collection requests", () => {
  auth.user = null; setToken(null);
  render(<MemoryRouter initialEntries={["/game-data"]}><CollectionProvider><Routes>
    <Route path="/game-data" element={<GameData />} />
    <Route path="/login" element={<LoginDestination />} />
  </Routes></CollectionProvider></MemoryRouter>);
  expect(screen.getByText("Log in to manage your collection.")).toBeTruthy();
  expect(screen.getByText(/While logged out, everything is treated as available/)).toBeTruthy();
  expect(screen.queryByText(/Everything starts marked/)).toBeNull();
  expect(screen.queryByRole("checkbox")).toBeNull();
  expect(api).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("link", { name: "Log in" }));
  expect(screen.getByText("return:/game-data")).toBeTruthy();
});

it("keeps one in-card row while saving and reports a failed save without a guest fallback", async () => {
  let finish!: () => void;
  vi.mocked(api).mockResolvedValueOnce(empty).mockImplementationOnce(() => new Promise((_, reject) => { finish = () => reject(new Error("Save failed")); }));
  render(<MemoryRouter><CollectionProvider><GameData /></CollectionProvider></MemoryRouter>);
  const checkbox = await screen.findByRole("checkbox");
  const card = checkbox.closest("article");
  fireEvent.click(checkbox);
  expect(screen.getByText("Saving…").closest("article")).toBe(card);
  expect(card?.querySelectorAll(".ownership-control")).toHaveLength(1);
  await act(async () => finish());
  await screen.findByText("Unavailable");
  expect(screen.getByRole("alert").textContent).toContain("Save failed");
  expect(screen.queryByText("Log in to manage your collection.")).toBeNull();
  expect(card?.querySelectorAll(".ownership-control")).toHaveLength(1);
});
