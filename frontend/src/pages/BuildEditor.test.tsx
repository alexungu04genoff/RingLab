import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useNavigate } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { api, ApiError, setToken } from "../api";
import type { Build, BuildStatsResult, MachinePart } from "../types";
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
  gameVersion: null, mapRecommendations: { mode: "ALL", maps: [] }, remixedFrom: null, createdAt: "2026-01-01", updatedAt: "2026-01-01",
  score: 0, upvotes: 0, downvotes: 0,
};
const buildB = { ...buildA, id: "B", title: "Build B draft" };
const latestVersion = { id: "latest", version: "1.4.1", releasedAt: "2026-06-23" };
const map = { id: "map-a", name: "E-Stadium", category: "MAIN_COURSE" as const,
  contentPack: null, imagePath: null, catalogOrder: 1 };
let loadB: () => Promise<Build>;
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(api).mockImplementation(async (path, options) => {
    if (options?.method === "PUT") return buildB;
    if (path === "/builds/A") return buildA;
    if (path === "/builds/B") return loadB();
    if (path === "/racers") return [buildA.racer];
    if (path === "/machine-parts") return [buildA.frontPart, buildA.rearPart, buildA.tirePart];
    if (path === "/game-versions") return [latestVersion];
    if (path === "/maps") return [map];
    return [];
  });
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); Reflect.deleteProperty(HTMLDialogElement.prototype, "showModal"); Reflect.deleteProperty(HTMLDialogElement.prototype, "close"); });

function desktopViewport() {
  let listener: (() => void) | undefined;
  const query = { matches: true, addEventListener: (_: string, changed: () => void) => { listener = changed; }, removeEventListener: () => {} };
  vi.stubGlobal("matchMedia", () => query);
  Object.defineProperty(HTMLDialogElement.prototype, "showModal", { configurable: true, value: function(this: HTMLDialogElement) { this.open = true; } });
  Object.defineProperty(HTMLDialogElement.prototype, "close", { configurable: true, value: function(this: HTMLDialogElement) { this.open = false; } });
  return { resize: (matches: boolean) => { query.matches = matches; act(() => listener?.()); } };
}

it("desktop individual and machine group locks remain synchronized and protect controls", async () => {
  desktopViewport(); await openA();
  const racerLock = screen.getByRole("button", { name: "Lock Racer" });
  expect(racerLock.parentElement?.firstElementChild).toBe(racerLock);
  fireEvent.click(screen.getByRole("button", { name: "Lock Racer" }));
  expect((screen.getByLabelText("Racer") as HTMLSelectElement).disabled).toBe(true);
  fireEvent.click(screen.getByRole("button", { name: "Lock Tires" }));
  expect(screen.getByRole("button", { name: "Lock machine setup" }).getAttribute("aria-pressed")).toBe("mixed");
  expect((screen.getByLabelText("Use stock machine") as HTMLSelectElement).disabled).toBe(true);
  fireEvent.change(screen.getByLabelText("Machine type"), { target: { value: "BOOST" } });
  expect((screen.getByLabelText("Machine type") as HTMLSelectElement).value).toBe("SPEED");
  fireEvent.change(screen.getByLabelText("Machine type"), { target: { value: "" } });
  expect((screen.getByLabelText("Machine type") as HTMLSelectElement).value).toBe("SPEED");
  fireEvent.click(screen.getByRole("button", { name: "Lock machine setup" }));
  expect((screen.getByLabelText("Front") as HTMLSelectElement).disabled).toBe(true);
  expect(screen.getByRole("button", { name: "Unlock machine setup" }).getAttribute("aria-pressed")).toBe("true");
  fireEvent.click(screen.getByRole("button", { name: "Unlock Rear" }));
  expect(screen.getByRole("button", { name: "Lock machine setup" }).getAttribute("aria-pressed")).toBe("mixed");
  fireEvent.click(screen.getByRole("button", { name: "Lock machine setup" }));
  fireEvent.click(screen.getByRole("button", { name: "Unlock machine setup" }));
  expect(screen.getByRole("button", { name: "Lock machine setup" }).getAttribute("aria-pressed")).toBe("false");
  expectNoWrite();
});

it("gadget lock click never toggles its checkbox or submits and locks reset for another editor", async () => {
  desktopViewport(); const fallback = vi.mocked(api).getMockImplementation()!;
  vi.mocked(api).mockImplementation(async(path, options) => path === "/gadgets"
    ? [{ id: "g", name: "Supported gadget", slotCost: 1, description: null, imagePath: null }] : fallback(path, options));
  loadB = () => Promise.resolve(buildB); const router = await openA();
  fireEvent.click(screen.getByRole("checkbox", { name: /Supported gadget/ }));
  fireEvent.click(screen.getByRole("button", { name: "Lock Supported gadget" }));
  const checkbox = screen.getByRole("checkbox", { name: /Supported gadget/ }) as HTMLInputElement;
  const choice = checkbox.closest(".gadget-choice")!;
  expect(choice.firstElementChild).toBe(screen.getByRole("button", { name: "Unlock Supported gadget" }));
  expect(choice.classList).toContain("has-lock");
  expect(checkbox.checked).toBe(true); expect(checkbox.disabled).toBe(true); expectNoWrite();
  fireEvent.click(screen.getByRole("button", { name: "Unlock Supported gadget" })); expect(checkbox.checked).toBe(true);
  fireEvent.click(screen.getByRole("button", { name: "Lock Racer" }));
  await act(async () => router.navigate("/builds/B/edit"));
  await screen.findByDisplayValue("Build B draft");
  expect(screen.getByRole("button", { name: "Lock Racer" }).getAttribute("aria-pressed")).toBe("false");
});

it("resizing to mobile aborts the popup, removes invisible locks and preserves the manual editor", async () => {
  const viewport = desktopViewport(); await openA(); selectLatestPatch();
  fireEvent.click(screen.getByRole("button", { name: "Lock Racer" }));
  fireEvent.click(screen.getByRole("button", { name: "Recommend a build" }));
  expect(vi.mocked(api).mock.calls.some(([path]) => path === "/build-recommendations")).toBe(false);
  let finish!: (value: unknown) => void;
  vi.mocked(api).mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  fireEvent.click(screen.getByRole("button", { name: "Calculate recommendation" }));
  const pending = vi.mocked(api).mock.calls.find(([path]) => path === "/build-recommendations")![1]!.signal!;
  viewport.resize(false); expect(pending.aborted).toBe(true);
  expect(screen.queryByRole("dialog")).toBeNull(); expect(screen.queryByRole("button", { name: "Recommend a build" })).toBeNull();
  expect((screen.getByLabelText("Racer") as HTMLSelectElement).disabled).toBe(false);
  expect((screen.getByLabelText("Build title") as HTMLInputElement).value).toBe("Build A draft");
  fireEvent.change(screen.getByLabelText("Machine type"), { target: { value: "" } });
  expect((screen.getByLabelText("Machine type") as HTMLSelectElement).value).toBe("");
  await act(async () => finish({ selection: null, outcome: "UNAVAILABLE" }));
  viewport.resize(true); expect(screen.queryByRole("dialog")).toBeNull();
  expect(screen.getByRole("button", { name: "Lock Racer" }).getAttribute("aria-pressed")).toBe("false");
});

it("same-user session replacement clears locks and closes the proposal", async () => {
  desktopViewport(); await openA();
  fireEvent.click(screen.getByRole("button", { name: "Lock Racer" }));
  fireEvent.click(screen.getByRole("button", { name: "Recommend a build" }));
  setToken("replacement-session"); fireEvent.change(screen.getByLabelText("Build title"), { target: { value: "Updated" } });
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(screen.getByRole("button", { name: "Lock Racer" }).getAttribute("aria-pressed")).toBe("false");
});

it.each(["/builds/new", "/builds/A/edit"])("shows applied gadget feedback in the picker and preview at %s", async (route) => {
  const fallback = vi.mocked(api).getMockImplementation()!;
  const gadget = {id:"drift",name:"Drift Charge Kit",description:"Improve Handling",slotCost:1,imagePath:null};
  const base: BuildStatsResult = {speed:20,acceleration:30,handling:40,power:50,boost:60,
    character:{speed:5,acceleration:5,handling:5,power:5,boost:5},
    machine:{speed:15,acceleration:25,handling:35,power:45,boost:55}};
  const adjustment={speed:0,acceleration:0,handling:3,power:0,boost:0};
  vi.mocked(api).mockImplementation(async(path,options)=>{
    if(path==="/gadgets") return [gadget];
    if(path==="/stats/gadget-rules") return {ruleset:"test",supportedVersion:"1.4.1",note:"Reviewed effects",gadgets:[{gadgetId:"drift",effects:[{
      effectId:"handling",label:"Handling",kind:"PASSIVE",subject:"ANY",requiredType:null,matching:adjustment,nonMatching:{speed:0,acceleration:0,handling:0,power:0,boost:0},explanation:"Always active",sources:[],stackingGroup:null
    }]}]};
    if(path.startsWith("/stats/passive-build")) return {...base,passive:{base,
      adjustments:adjustment,adjusted:{...base,handling:43},coverage:"CALCULATED",
      ruleset:"test",supportedVersion:"1.4.1",note:"Verified passive effects",
      effects:path.includes("gadgetId=drift") ? [{gadgetId:"drift",gadgetName:gadget.name,effectId:"handling",
        label:"Handling",status:"APPLIED",adjustment,explanation:"Passive Handling bonus",sources:[]}] : []}};
    return fallback(path,options);
  });
  render(<MemoryRouter initialEntries={[route]}><Routes>
    <Route path="/builds/new" element={<BuildEditor />} />
    <Route path="/builds/:id/edit" element={<BuildEditor />} />
  </Routes></MemoryRouter>);
  const garageKicker = screen.getByText("THE GARAGE").closest(".build-editor-kicker");
  expect(garageKicker?.textContent).toContain("← Back");
  const checkbox=await screen.findByRole("checkbox",{name:/Drift Charge Kit/});
  expect(screen.getByLabelText("Reviewed gadget stat adjustments")).toBeTruthy();
  expect(screen.getByLabelText("Reviewed gadget stat adjustments").textContent).toContain("Handling +3");
  selectLatestPatch();
  fireEvent.click(checkbox);
  await waitFor(()=>expect(screen.getByLabelText("Applied gadget adjustments")).toBeTruthy());
  expect(screen.getByLabelText("Applied gadget adjustments").textContent).toContain("Handling +3");
  const picker = checkbox.closest(".gadget-option")!;
  const meta = picker.querySelector(".gadget-option-meta")!;
  expect(within(meta as HTMLElement).getByText("1 slot")).toBeTruthy();
  expect(within(meta as HTMLElement).getByLabelText("Reviewed gadget stat adjustments")).toBeTruthy();
  expect(screen.getByRole("button",{name:"Handling gadget adjustment +3"})).toBeTruthy();
  expect(vi.mocked(api).mock.calls.filter(([path])=>path.includes("gadgetId=drift"))).toHaveLength(1);
  fireEvent.click(checkbox);
  expect(screen.queryByLabelText("Applied gadget adjustments")).toBeNull();
});
async function openA() {
  let navigate!: ReturnType<typeof useNavigate>;
  function Navigation() { navigate = useNavigate(); return null; }
  render(<MemoryRouter initialEntries={["/builds/A/edit"]}><Navigation /><Routes>
    <Route path="/builds/:id/edit" element={<BuildEditor />} />
    <Route path="/builds/:id" element={<p>Saved destination</p>} />
    <Route path="/outside" element={<p>Outside editor</p>} />
  </Routes></MemoryRouter>);
  await waitFor(() => expect((screen.getByLabelText("Build title") as HTMLInputElement).value).toBe("Build A draft"));
  return { navigate };
}
it.each(["success", "failure"])("ignores A's save %s after navigating to B", async (outcome) => {
  loadB = () => Promise.resolve(buildB);
  const router = await openA();
  selectLatestPatch();
  let resolve!: (build: Build) => void;
  let reject!: (error: Error) => void;
  vi.mocked(api).mockImplementationOnce(() => new Promise<Build>((done, fail) => {
    resolve = done; reject = fail;
  }));
  fireEvent.submit(screen.getByLabelText("Build title").closest("form")!);
  await act(async () => { await router.navigate("/builds/B/edit"); });
  await waitFor(() => expect((screen.getByLabelText("Build title") as HTMLInputElement).value).toBe(buildB.title));
  await act(async () => {
    if (outcome === "success") resolve(buildA);
    else reject(new Error("Old save failed"));
  });
  expect((screen.getByLabelText("Build title") as HTMLInputElement).value).toBe(buildB.title);
  expect(screen.queryByText("Saved destination")).toBeNull();
  expect(screen.queryByText("Old save failed")).toBeNull();
});

it("does not redirect after leaving an editor with a pending save", async () => {
  const router = await openA();
  selectLatestPatch();
  let finish!: (build: Build) => void;
  vi.mocked(api).mockImplementationOnce(() => new Promise<Build>(resolve => { finish = resolve; }));
  fireEvent.submit(screen.getByLabelText("Build title").closest("form")!);
  await act(async () => { await router.navigate("/outside"); });
  await act(async () => { finish(buildA); });
  expect(screen.getByText("Outside editor")).toBeTruthy();
  expect(screen.queryByText("Saved destination")).toBeNull();
});
function expectNoWrite() {
  expect(vi.mocked(api).mock.calls.filter(([, options]) => options?.method === "PUT")).toEqual([]);
}
function selectLatestPatch() {
  fireEvent.change(screen.getByLabelText("Game version / Patch"), { target: { value: latestVersion.id } });
}

it("preserves loaded maps through setup edits and requires an explicit All choice to clear them", async () => {
  const original = vi.mocked(api).getMockImplementation()!;
  vi.mocked(api).mockImplementation(async (path, options) => path === "/builds/A" && !options?.method
    ? { ...buildA, mapRecommendations: { mode: "SELECTED", maps: [map] } } : original(path, options));
  await openA();
  selectLatestPatch();
  fireEvent.change(screen.getByLabelText("Racer"), { target: { value: "racer" } });
  fireEvent.change(screen.getByLabelText(/^Front/), { target: { value: "FRONT" } });
  expect((screen.getByRole("checkbox", { name: /E-Stadium/ }) as HTMLInputElement).checked).toBe(true);
  expect(screen.getByLabelText("Your combination recommended maps").textContent).toContain("E-Stadium");
  fireEvent.click(screen.getByRole("checkbox", { name: /E-Stadium/ }));
  expect(screen.getByLabelText("Your combination recommended maps").textContent).toContain("Choose at least one map.");
  expect((screen.getByRole("button", { name: "Save changes" }) as HTMLButtonElement).disabled).toBe(true);
  fireEvent.submit(screen.getByLabelText("Build title").closest("form")!);
  expectNoWrite();
  fireEvent.click(screen.getByRole("radio", { name: "All maps" }));
  expect(screen.getByLabelText("Your combination recommended maps").textContent).toContain("Recommended maps · All");
  fireEvent.submit(screen.getByLabelText("Build title").closest("form")!);
  await screen.findByText("Saved destination");
  const write = vi.mocked(api).mock.calls.find(([, options]) => options?.method === "PUT")!;
  expect(JSON.parse(write[1]!.body as string)).toMatchObject({ recommendedMapIds: [] });
  expect(JSON.parse(write[1]!.body as string)).not.toHaveProperty("mapRecommendationMode");
});

it("keeps an incompatible legacy rear visible and requires explicit correction", async () => {
  const invalid = { ...buildA, rearPart: { ...part("REAR"), id: "wrong-rear", racingType: "POWER" as const, sourceMachineName: "Power source" } };
  const original = vi.mocked(api).getMockImplementation()!;
  vi.mocked(api).mockImplementation(async (path, options) => {
    if (path === "/builds/A") return invalid;
    if (path === "/machine-parts") return [part("FRONT"), part("REAR"), part("TIRE"), invalid.rearPart];
    return original(path, options);
  });
  await openA();
  selectLatestPatch();
  expect((screen.getByLabelText(/^Rear/) as HTMLSelectElement).value).toBe("wrong-rear");
  expect(screen.getByRole("option", { name: /Power source — incompatible/ })).toBeTruthy();
  expect((screen.getByRole("button", { name: "Save changes" }) as HTMLButtonElement).disabled).toBe(true);
  fireEvent.submit(screen.getByLabelText("Build title").closest("form")!);
  expectNoWrite();
  fireEvent.change(screen.getByLabelText(/^Rear/), { target: { value: "REAR" } });
  expect((screen.getByRole("button", { name: "Save changes" }) as HTMLButtonElement).disabled).toBe(false);
  fireEvent.change(screen.getByLabelText("Racer"), { target: { value: "racer" } });
  expect((screen.getByLabelText(/^Front/) as HTMLSelectElement).value).toBe("FRONT");
  expect((screen.getByLabelText(/^Rear/) as HTMLSelectElement).value).toBe("REAR");
});

it("requires a patch selection for a new build", async () => {
  render(<MemoryRouter initialEntries={["/builds/new"]}><Routes>
    <Route path="/builds/new" element={<BuildEditor />} />
  </Routes></MemoryRouter>);
  await waitFor(() => expect((screen.getByLabelText("Game version / Patch") as HTMLSelectElement).value)
    .toBe(""));
  expect(screen.getByRole("option", { name: "Select a patch" })).toBeTruthy();
  expect(screen.getByText("A game version / patch is required.")).toBeTruthy();
});

function openRemix() {
  let navigate!: ReturnType<typeof useNavigate>;
  function Navigation() { navigate = useNavigate(); return null; }
  render(<MemoryRouter initialEntries={["/builds/new?remixFrom=A"]}><Navigation /><Routes>
    <Route path="/builds/new" element={<BuildEditor />} />
  </Routes></MemoryRouter>);
  return { navigate };
}

it("clears a loaded remix and its provenance when navigating to a new build", async () => {
  const router = openRemix();
  await waitFor(() => expect((screen.getByLabelText("Build title") as HTMLInputElement).value)
    .toBe("Remix of Build A draft"));
  await act(async () => { await router.navigate("/builds/new"); });

  expect((screen.getByLabelText("Build title") as HTMLInputElement).value).toBe("");
  expect((screen.getByLabelText("Description") as HTMLTextAreaElement).value).toBe("");
  expect((screen.getByLabelText("Racer") as HTMLSelectElement).value).toBe("");
  expect((screen.getByLabelText("Game version / Patch") as HTMLSelectElement).value).toBe("");
  fireEvent.change(screen.getByLabelText("Build title"), { target: { value: "Fresh build" } });
  fireEvent.change(screen.getByLabelText("Racer"), { target: { value: "racer" } });
  fireEvent.change(screen.getByLabelText("Machine type"), { target: { value: "SPEED" } });
  fireEvent.change(screen.getByLabelText("Use stock machine"), { target: { value: "machine" } });
  selectLatestPatch();
  vi.mocked(api).mockRejectedValueOnce(new Error("Save unavailable"));
  fireEvent.submit(screen.getByLabelText("Build title").closest("form")!);
  await screen.findByText("Save unavailable");
  const write = vi.mocked(api).mock.calls.find(([, options]) => options?.method === "POST")!;
  expect(write[0]).toBe("/builds");
  expect(JSON.parse(write[1]!.body as string).remixedFromBuildId).toBeNull();
});

it.each(["success", "failure"])("ignores an abandoned remix load's %s and leaves a usable new draft", async (outcome) => {
  let resolve!: (build: Build) => void;
  let reject!: (error: Error) => void;
  const original = vi.mocked(api).getMockImplementation()!;
  vi.mocked(api).mockImplementation((path, options) => path === "/builds/A"
    ? new Promise((done, fail) => { resolve = done; reject = fail; }) : original(path, options));
  const router = openRemix();
  await screen.findByText("Loading your build…");
  await act(async () => { await router.navigate("/builds/new"); });
  await act(async () => {
    if (outcome === "success") resolve(buildA);
    else reject(new Error("Abandoned load failed"));
  });

  expect((screen.getByLabelText("Build title") as HTMLInputElement).value).toBe("");
  expect(screen.getByRole("button", { name: "Publish build" })).toBeTruthy();
  expect(screen.queryByText("Loading your build…")).toBeNull();
  expect(screen.queryByText("Abandoned load failed")).toBeNull();
});

it("hides the previous remix when the next source cannot be loaded", async () => {
  const router = openRemix();
  await screen.findByDisplayValue("Remix of Build A draft");
  loadB = () => Promise.reject(new Error("Remix source unavailable"));
  await act(async () => { await router.navigate("/builds/new?remixFrom=B"); });
  await screen.findByText("Remix source unavailable");

  expect(screen.queryByLabelText("Build title")).toBeNull();
  expect(screen.queryByRole("button", { name: "Publish build" })).toBeNull();
});

it("highlights an unspecified legacy patch and requires a selection", async () => {
  await openA();
  const select = screen.getByLabelText("Game version / Patch") as HTMLSelectElement;
  expect(select.value).toBe("");
  expect(select.getAttribute("aria-invalid")).toBe("true");
  expect(screen.getByRole("alert").textContent).toContain("required");
});

it("shows title-cased, color-coded type dots in racer and machine-part dropdowns", async () => {
  await openA();
  const options = screen.getAllByRole("option").filter((option) => option.classList.contains("typed-option"));
  expect(options).toHaveLength(4);
  options.forEach((option) => {
    expect(option.textContent).toContain("● Speed");
    expect(option.classList.contains("racing-type-speed")).toBe(true);
  });
});

it("filters family choices, applies both stock shapes, and clears selections on switches", async () => {
  const boardParts: MachinePart[] = (["FRONT", "REAR"] as const).map((type) => ({
    ...part(type), id: `board-${type}`, sourceMachineId: "board", sourceMachineName: "Diva Macchina",
    racingType: "BOOST",
  }));
  const standardParts: MachinePart[] = (["FRONT", "REAR", "TIRE"] as const).map((type) => ({
    ...part(type), id: `standard-${type}`, sourceMachineId: "standard",
    sourceMachineName: "Speedster Lightning",
  }));
  vi.mocked(api).mockImplementation(async (path) => {
    if (path === "/racers") return [buildA.racer];
    if (path === "/machine-parts") return [...boardParts, ...standardParts];
    if (path === "/game-versions") return [latestVersion];
    return [];
  });
  render(<MemoryRouter initialEntries={["/builds/new"]}><Routes>
    <Route path="/builds/new" element={<BuildEditor />} />
  </Routes></MemoryRouter>);
  const stock = await screen.findByLabelText("Use stock machine") as HTMLSelectElement;
  expect(stock.disabled).toBe(false);
  expect((screen.getByLabelText(/^Front/) as HTMLSelectElement).disabled).toBe(false);
  fireEvent.change(screen.getByLabelText(/^Front/), { target: { value: "standard-FRONT" } });
  expect((screen.getByLabelText("Machine type") as HTMLSelectElement).value).toBe("SPEED");
  expect(within(stock).getByRole("option", { name: /Speedster Lightning/ })).toBeTruthy();
  expect(within(stock).queryByRole("option", { name: /Diva Macchina/ })).toBeNull();
  for (const label of ["Front", "Rear", "Tires"]) {
    const select = screen.getByLabelText(new RegExp(`^${label}`));
    expect(within(select).getByRole("option", { name: /Speedster Lightning/ })).toBeTruthy();
    expect(within(select).queryByRole("option", { name: /Diva Macchina/ })).toBeNull();
  }

  fireEvent.change(stock, { target: { value: "standard" } });
  expect((screen.getByLabelText(/^Front/) as HTMLSelectElement).value).toBe("standard-FRONT");
  expect((screen.getByLabelText(/^Rear/) as HTMLSelectElement).value).toBe("standard-REAR");
  expect((screen.getByLabelText(/^Tires/) as HTMLSelectElement).value).toBe("standard-TIRE");
  expect(document.querySelectorAll(".preview-parts > div")).toHaveLength(3);

  fireEvent.change(screen.getByLabelText("Machine type"), { target: { value: "BOOST" } });
  expect(stock.value).toBe("");
  expect(screen.getByLabelText(/^Front/)).toBeTruthy();
  expect(screen.getByLabelText(/^Rear/)).toBeTruthy();
  expect(screen.queryByLabelText(/^Tires/)).toBeNull();
  expect((screen.getByLabelText(/^Front/) as HTMLSelectElement).value).toBe("");
  expect((screen.getByLabelText(/^Rear/) as HTMLSelectElement).value).toBe("");
  expect(within(stock).getByRole("option", { name: /Diva Macchina/ })).toBeTruthy();
  expect(within(stock).queryByRole("option", { name: "Speedster Lightning" })).toBeNull();
  for (const label of ["Front", "Rear"]) {
    const select = screen.getByLabelText(new RegExp(`^${label}`));
    expect(within(select).getByRole("option", { name: /Diva Macchina/ })).toBeTruthy();
    expect(within(select).queryByRole("option", { name: /Speedster Lightning/ })).toBeNull();
  }
  fireEvent.change(stock, { target: { value: "board" } });
  expect((screen.getByLabelText(/^Front/) as HTMLSelectElement).value).toBe("board-FRONT");
  expect((screen.getByLabelText(/^Rear/) as HTMLSelectElement).value).toBe("board-REAR");
  expect(document.querySelectorAll(".preview-parts > div")).toHaveLength(2);
  expect(screen.queryByText("Boards do not use tires.")).toBeNull();
  expect(screen.queryByText("Boards use front and rear parts only; they do not use tires.")).toBeNull();

  fireEvent.change(screen.getByLabelText("Machine type"), { target: { value: "SPEED" } });
  expect(stock.value).toBe("");
  expect((screen.getByLabelText(/^Front/) as HTMLSelectElement).value).toBe("");
  expect((screen.getByLabelText(/^Rear/) as HTMLSelectElement).value).toBe("");
  expect((screen.getByLabelText(/^Tires/) as HTMLSelectElement).value).toBe("");
});

it.each(["title", "description"])("shows %s validation beside its input and clears it on editing", async (field) => {
  await openA();
  selectLatestPatch();
  vi.mocked(api).mockRejectedValueOnce(new ApiError(400, "Text contains inappropriate language", undefined, field));
  const input = screen.getByLabelText(field === "title" ? "Build title" : "Description");
  fireEvent.submit(input.closest("form")!);
  const alert = await screen.findByRole("alert");
  expect(alert.parentElement?.id).toBe(`build-${field}-error`);
  expect(input.getAttribute("aria-describedby")).toBe(alert.parentElement?.id);
  fireEvent.change(input, { target: { value: "Clean revised text" } });
  expect(screen.queryByRole("alert")).toBeNull();
});

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
  selectLatestPatch();
  fireEvent.submit(title.closest("form")!);
  await screen.findByText("Saved destination");
  const writes = vi.mocked(api).mock.calls.filter(([, options]) => options?.method === "PUT");
  expect(writes).toHaveLength(1);
  expect(writes[0][0]).toBe("/builds/B");
  expect(JSON.parse(writes[0][1]!.body as string).title).toBe("Build B draft");
});
