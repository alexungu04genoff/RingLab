import { act, cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { api, ApiError, setToken } from "../shared/api/api";
import type { Build, MachinePart } from "../shared/types";
import { BuildDetails } from "./BuildDetails";

const actor = { id: "owner", username: "driver" };
let currentActor: typeof actor | null = actor;
vi.mock("../features/auth/auth", () => ({ useAuth: () => ({ user: currentActor }) }));
vi.mock("../shared/api/api", async (original) => ({ ...await original<typeof import("../shared/api/api")>(), api: vi.fn() }));
const part = (type: MachinePart["type"]): MachinePart => ({ id: type, type,
  sourceMachineId: "machine", sourceMachineName: "Machine", sourceMachineImagePath: null,
  racingType: "SPEED" });
const build = (id: string): Build => ({
  id, title: `Build ${id}`, description: "", author: actor,
  racer: { id: "racer", name: "Sonic", racingType: "SPEED", imagePath: null },
  frontPart: part("FRONT"), rearPart: part("REAR"), tirePart: part("TIRE"),
  gameVersion: null, gadgets: [], mapRecommendations: { mode: "ALL", maps: [] }, remixedFrom: id === "A" ? { id: "B", title: "Source B" } : null,
  createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
  score: id === "A" ? 10 : 20, upvotes: id === "A" ? 10 : 20, downvotes: 0,
});
let finishMutation: (value: unknown) => void;

it("shows the private owner workflow without exposing active community controls", async () => {
  const fallback = vi.mocked(api).getMockImplementation()!;
  vi.mocked(api).mockImplementation((path, options) => path === "/builds/A"
    ? Promise.resolve({ ...build("A"), visibility: "PRIVATE" }) : fallback(path, options));
  render(<MemoryRouter initialEntries={["/builds/A"]}><Routes>
    <Route path="/builds/:id" element={<BuildDetails />} />
  </Routes></MemoryRouter>);
  await screen.findByRole("heading", { name: "Build A" });
  expect(screen.getByText("🔒 Private")).toBeTruthy();
  expect(screen.getByText("Comments are unavailable while this build is private.")).toBeTruthy();
  expect(screen.getByRole("link", { name: "Publish build" })).toBeTruthy();
  expect(screen.getByRole("link", { name: "Compare" })).toBeTruthy();
  expect(screen.getByRole("link", { name: "Remix this build" })).toBeTruthy();
  expect(screen.queryByRole("button", { name: /Upvote|Downvote|Copy setup/ })).toBeNull();
  expect(vi.mocked(api).mock.calls.some(([path]) => path.includes("/comments") || path.endsWith("/vote"))).toBe(false);
});

it.each(["logout", "account switch", "session replacement"])("clears private detail immediately on %s", async change => {
  const fallback = vi.mocked(api).getMockImplementation()!;
  let allowed = true;
  vi.mocked(api).mockImplementation((path, options) => path === "/builds/A"
    ? allowed ? Promise.resolve({ ...build("A"), visibility: "PRIVATE" }) : Promise.reject(new ApiError(404, "Build not found"))
    : fallback(path, options));
  const content = () => <MemoryRouter initialEntries={["/builds/A"]}><Routes>
    <Route path="/builds/:id" element={<BuildDetails />} />
  </Routes></MemoryRouter>;
  const view = render(content());
  await screen.findByRole("heading", { name: "Build A" });
  allowed = false;
  currentActor = change === "logout" ? null : change === "account switch" ? { id: "other", username: "other" } : actor;
  act(() => { setToken("different-session"); view.rerender(content()); });
  expect(screen.queryByRole("heading", { name: "Build A" })).toBeNull();
  await screen.findByText("Build not found");
  setToken(null);
});

it("removes a stale public detail after a vote discovers the build is unavailable", async () => {
  const fallback = vi.mocked(api).getMockImplementation()!;
  vi.mocked(api).mockImplementation((path, options) => options?.method === "PUT"
    ? Promise.reject(new ApiError(404, "Build not found")) : fallback(path, options));
  render(<MemoryRouter initialEntries={["/builds/A"]}><Routes>
    <Route path="/builds/:id" element={<BuildDetails />} />
  </Routes></MemoryRouter>);
  const user = userEvent.setup();
  await screen.findByRole("heading", { name: "Build A" });
  await waitFor(() => expect((screen.getByRole("button", { name: "↑ Upvote" }) as HTMLButtonElement).disabled).toBe(false));
  await user.click(screen.getByRole("button", { name: "↑ Upvote" }));
  await screen.findByText("This build is no longer available.");
  expect(screen.queryByRole("heading", { name: "Build A" })).toBeNull();
});
beforeEach(() => {
  currentActor = actor;
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

it("uses saved selections for a public scenario request without editing the build", async () => {
  const fallback=vi.mocked(api).getMockImplementation()!;
  vi.mocked(api).mockImplementation(async(path,options)=> {
    if(path==="/stats/scenario-rules") return {supportedVersion:"1.4.1",controls:[]};
    if(path==="/stats/scenario-build") return new Promise(()=>{});
    return fallback(path,options);
  });
  render(<MemoryRouter initialEntries={["/builds/A"]}><Routes>
    <Route path="/builds/:id" element={<BuildDetails />} />
  </Routes></MemoryRouter>);
  await userEvent.click(await screen.findByRole("button",{name:"Try a scenario"}));
  await waitFor(()=>expect(vi.mocked(api).mock.calls.some(([path])=>path==="/stats/scenario-build")).toBe(true));
  const preview=vi.mocked(api).mock.calls.find(([path])=>path==="/stats/scenario-build")!;
  expect(JSON.parse(preview[1]!.body as string)).toMatchObject({racerId:"racer",frontPartId:"FRONT",rearPartId:"REAR",tirePartId:"TIRE",gameVersionId:null});
  expect(preview[1]?.anonymous).toBe(true);
  expect(vi.mocked(api).mock.calls.filter(([path,options])=>options?.method && !path.startsWith("/stats/"))).toHaveLength(0);
});

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

it("posts through the shared busy boundary and shows the last comment page", async () => {
  const fallback = vi.mocked(api).getMockImplementation()!;
  let posted = false;
  vi.mocked(api).mockImplementation(async (path, options) => {
    if (path === "/builds/A/comments" && options?.method === "POST") {
      return new Promise(resolve => { finishMutation = () => { posted = true; resolve(undefined); }; });
    }
    if (path.startsWith("/builds/A/comments?")) {
      const page = Number(new URLSearchParams(path.split("?")[1]).get("page"));
      return { items: posted && page === 1 ? [{ id: "new", author: actor.username, authorId: actor.id,
        text: "New comment", createdAt: "2026-01-01T00:00:00Z" }] : [], total: posted ? 22 : 21, page, size: 20 };
    }
    return fallback(path, options);
  });
  render(<MemoryRouter initialEntries={["/builds/A"]}><Routes>
    <Route path="/builds/:id" element={<BuildDetails />} />
  </Routes></MemoryRouter>);
  const user = userEvent.setup();
  await screen.findByRole("heading", { name: "Build A" });
  await user.type(screen.getByRole("textbox"), "New comment");
  await user.click(screen.getByRole("button", { name: "Post comment" }));
  expect((screen.getByRole("button", { name: "Post comment" }) as HTMLButtonElement).disabled).toBe(true);
  expect((screen.getByRole("button", { name: "↑ Upvote" }) as HTMLButtonElement).disabled).toBe(true);
  expect(api).toHaveBeenCalledWith("/builds/A/comments", expect.objectContaining({ method: "POST", body: JSON.stringify({ text: "New comment" }) }));
  await act(async () => { finishMutation(undefined); });
  await screen.findByText("New comment");
  expect(screen.getByText("Page 2")).toBeTruthy();
  expect((screen.getByRole("textbox") as HTMLTextAreaElement).value).toBe("");
  expect((screen.getByRole("button", { name: "↑ Upvote" }) as HTMLButtonElement).disabled).toBe(false);
});

it("deletes an owned final comment and returns to the preceding page", async () => {
  const fallback = vi.mocked(api).getMockImplementation()!;
  let deleted = false;
  vi.mocked(api).mockImplementation(async (path, options) => {
    if (path === "/comments/last" && options?.method === "DELETE") { deleted = true; return undefined; }
    if (path.startsWith("/builds/A/comments?")) {
      const page = Number(new URLSearchParams(path.split("?")[1]).get("page"));
      return { items: [{ id: page === 1 ? "last" : "other", author: page === 1 ? actor.username : "Other",
        authorId: page === 1 ? actor.id : "other", text: page === 1 ? "Final comment" : "Earlier comment",
        createdAt: "2026-01-01T00:00:00Z" }], total: deleted ? 20 : 21, page, size: 20 };
    }
    return fallback(path, options);
  });
  render(<MemoryRouter initialEntries={["/builds/A"]}><Routes>
    <Route path="/builds/:id" element={<BuildDetails />} />
  </Routes></MemoryRouter>);
  const user = userEvent.setup();
  const earlier = await screen.findByText("Earlier comment");
  expect(within(earlier.closest("article")!).queryByRole("button", { name: "Delete" })).toBeNull();
  await user.click(screen.getByRole("button", { name: "Next" }));
  const final = await screen.findByText("Final comment");
  await user.click(within(final.closest("article")!).getByRole("button", { name: "Delete" }));
  await screen.findByText("Earlier comment");
  expect(api).toHaveBeenCalledWith("/comments/last", expect.objectContaining({ method: "DELETE" }));
  expect(screen.queryByText("Final comment")).toBeNull();
  expect(screen.queryByText("Page 2")).toBeNull();
  expect(screen.queryByRole("button", { name: "Next" })).toBeNull();
});
