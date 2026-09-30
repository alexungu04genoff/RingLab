import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { api } from "./api";
import { ScenarioPreview, type ScenarioResult, type ScenarioSelection } from "./ScenarioPreview";

vi.mock("./api", async original => ({ ...await original<typeof import("./api")>(), api: vi.fn() }));
const selection: ScenarioSelection = { gameVersionId: "patch", racerId: "racer", frontPartId: "front",
  rearPartId: "rear", tirePartId: null, gadgetIds: ["quick"] };
const stats = { speed: 65, acceleration: 30, handling: 59, power: 52, boost: 34 };
const zero = { speed: 0, acceleration: 0, handling: 0, power: 0, boost: 0 };
const result: ScenarioResult = {
  passive: { base: { ...stats, character: stats, machine: zero }, adjusted: stats, adjustments: zero,
    coverage: "CALCULATED", effects: [], ruleset: "passive", supportedVersion: "1.4.1", note: "Passive note" },
  total: stats, knownSubtotal: stats, adjustments: zero, coverage: "CALCULATED", effects: [],
  ruleset: "scenario", supportedVersion: "1.4.1", note: "Raw points only",
};
const controls = { supportedVersion: "1.4.1", controls: [
  { gadgetId: "quick", field: "LAP", statEffect: true }, { gadgetId: "plane", field: "VEHICLE_FORM", statEffect: true },
  { gadgetId: "ring", field: "RINGS_HELD", statEffect: true }, { gadgetId: "landing", field: "LANDING_BOOST_ACTIVE", statEffect: false },
  { gadgetId: "finish", field: "DISTANCE_TO_FINISH", statEffect: false },
] };
beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(api).mockImplementation(async path => path === "/stats/scenario-rules" ? controls : result);
});
afterEach(cleanup);
function open() { fireEvent.click(screen.getByRole("button", { name: "Try a scenario" })); }
function calls() { return vi.mocked(api).mock.calls.filter(([path]) => path === "/stats/scenario-build"); }

it("defaults to passive with no requests and exposes only relevant conditions", async () => {
  render(<ScenarioPreview selections={[{ label: "Draft", selection }]} />);
  expect(api).not.toHaveBeenCalled(); open();
  expect(await screen.findByLabelText("Lap")).toBeTruthy();
  expect(screen.queryByLabelText("Vehicle form")).toBeNull();
  await waitFor(() => expect(calls()).toHaveLength(1));
  const request = JSON.parse(calls()[0][1]!.body as string);
  expect(request.scenario.lap).toBeNull(); expect(request.tirePartId).toBeNull();
  expect(calls()[0][1]?.anonymous).toBe(true);
  expect(request).not.toHaveProperty("recommendedMapIds");
  fireEvent.change(screen.getByLabelText("Lap"), { target: { value: "1" } });
  await waitFor(() => expect(JSON.parse(calls().at(-1)![1]!.body as string).scenario.lap).toBe(1));
  fireEvent.click(screen.getByRole("button", { name: "Reset to Passive only" }));
  expect(screen.queryByLabelText("Lap")).toBeNull(); open();
  expect((await screen.findByLabelText("Lap") as HTMLSelectElement).value).toBe("");
  expect(selection).toEqual({ gameVersionId: "patch", racerId: "racer", frontPartId: "front", rearPartId: "rear", tirePartId: null, gadgetIds: ["quick"] });
});

it("sends one shared scenario to both comparison selections", async () => {
  render(<ScenarioPreview selections={[{ label: "Left", selection },
    { label: "Right", selection: { ...selection, racerId: "other", gadgetIds: ["plane"] } }]} />);
  open(); fireEvent.change(await screen.findByLabelText("Vehicle form"), { target: { value: "FLIGHT" } });
  await waitFor(() => expect(calls().slice(-2).every(([, options]) => JSON.parse(options!.body as string).scenario.vehicleForm === "FLIGHT")).toBe(true));
  const bodies = calls().slice(-2).map(([, o]) => JSON.parse(o!.body as string));
  expect(bodies[0].scenario).toEqual(bodies[1].scenario);
  expect(bodies.map(b => b.racerId)).toEqual(["racer", "other"]);
  expect(screen.getAllByLabelText("Vehicle form")).toHaveLength(1);
});

it.each(["racerId", "frontPartId", "rearPartId", "tirePartId", "gameVersionId", "gadgetIds"] as const)(
  "aborts and hides stale results immediately when %s changes", async field => {
    let resolveOld!: (r: ScenarioResult) => void;
    vi.mocked(api).mockImplementation(async path => path === "/stats/scenario-rules" ? controls
      : new Promise(resolve => { resolveOld = resolve; }));
    const view=render(<ScenarioPreview selections={[{ label: "Draft", selection }]} />);open();
    await waitFor(() => expect(calls()).toHaveLength(1));
    const finish=resolveOld;const signal=calls()[0][1]?.signal;
    const updated={ ...selection, [field]: field === "gadgetIds" ? ["plane"] : "different" };
    view.rerender(<ScenarioPreview selections={[{ label: "Draft", selection: updated }]} />);
    expect(signal?.aborted).toBe(true);
    await act(async () => finish({ ...result, note: "Obsolete result" }));
    expect(screen.queryByText("Obsolete result")).toBeNull();
    expect(screen.getByText("Calculating scenario preview…")).toBeTruthy();
    view.unmount();expect(calls().at(-1)![1]?.signal?.aborted).toBe(true);
  });

it("invalidates on condition edits and ignores a late result after reset", async () => {
  let finish!: (r: ScenarioResult) => void;
  vi.mocked(api).mockImplementation(async path => path === "/stats/scenario-rules" ? controls : new Promise(resolve => { finish=resolve; }));
  render(<ScenarioPreview selections={[{ label: "Draft", selection }]} />);open();
  await waitFor(() => expect(calls()).toHaveLength(1));
  const old=finish;fireEvent.change(screen.getByLabelText("Lap"), { target: { value: "2" } });
  expect(calls()[0][1]?.signal?.aborted).toBe(true);
  fireEvent.click(screen.getByRole("button", { name: "Reset to Passive only" }));
  await act(async () => old(result));expect(screen.queryByText(/Scenario total/)).toBeNull();
});

it("shows unsupported and unknown effects without fake zero totals", async () => {
  vi.mocked(api).mockImplementation(async path => path === "/stats/scenario-rules" ? controls : {
    ...result, total: null, coverage: "PARTIAL", passive: { ...result.passive, coverage: "PARTIAL" },
    effects: [{ gadgetId: "quick", gadgetName: "Quick Starter", effectId: "lap", label: "Lap", status: "CONDITION_UNKNOWN",
      statEffect: true, adjustment: null, explanation: "Specify a lap.", sources: [] },
    { gadgetId: "evolution", gadgetName: "Damage Evolution", effectId: "count", label: "Damage", status: "UNSUPPORTED",
      statEffect: true, adjustment: null, explanation: "Cap unverified.", sources: [] }],
  });
  render(<ScenarioPreview selections={[{ label: "Draft", selection }]} />);open();
  expect(await screen.findByText(/Known subtotal —/)).toBeTruthy();
  expect(screen.getAllByText("Not calculated")).toHaveLength(5);
  expect(screen.getByText(/Passive calculation is also incomplete/)).toBeTruthy();
  fireEvent.click(screen.getByText("Scenario effects & details"));
  expect(screen.getByText("Condition not specified")).toBeTruthy();expect(screen.getByText("Unsupported effects")).toBeTruthy();
});

it("handles an empty supported catalog and retries failures without writing builds", async () => {
  let failing=true;
  vi.mocked(api).mockImplementation(async path => {
    if(failing) throw new Error("Unavailable");
    return path === "/stats/scenario-rules" ? controls : result;
  });
  render(<ScenarioPreview selections={[{ label: "Utility", selection: { ...selection, gadgetIds: [] } }]} />);open();
  await screen.findByRole("alert");failing=false;fireEvent.click(screen.getByRole("button", { name: "Retry controls" }));
  expect(await screen.findByText("No selected gadget has a supported scenario stat effect.")).toBeTruthy();
  await screen.findByText(/Scenario total/);
  expect(vi.mocked(api).mock.calls.every(([path])=>path.startsWith("/stats/scenario-"))).toBe(true);
});

it("keeps a failed comparison side unavailable and retries the same context", async () => {
  let fail=true;
  vi.mocked(api).mockImplementation(async (path, options) => {
    if(path === "/stats/scenario-rules") return controls;
    if(fail && JSON.parse(options!.body as string).racerId === "other") throw new Error("Offline");
    return result;
  });
  render(<ScenarioPreview selections={[{ label: "Left", selection },{ label: "Right", selection: { ...selection, racerId: "other" } }]} />);open();
  expect(await screen.findByText(/Scenario unavailable: Offline/)).toBeTruthy();
  fail=false;fireEvent.click(screen.getByRole("button",{name:"Retry preview"}));
  await waitFor(()=>expect(screen.getAllByText(/Scenario total —/)).toHaveLength(2));
});

it("validates whole-number inputs and describes active utility without points", async () => {
  vi.mocked(api).mockImplementation(async path => path === "/stats/scenario-rules" ? controls : { ...result,
    effects: [{ gadgetId: "landing", gadgetName: "Perfect Landing", effectId: "landing", label: "Landing boost",
      status: "ACTIVE_NON_STAT", statEffect: false, adjustment: null, explanation: "Not represented by stat points.", sources: [] }] });
  render(<ScenarioPreview selections={[{ label: "Draft", selection: { ...selection, gadgetIds: ["ring","landing","finish"] } }]} />);open();
  const input=await screen.findByLabelText(/Rings held now/);
  fireEvent.change(input,{target:{value:"1000"}});expect(screen.getByRole("alert").textContent).toContain("whole numbers");
  fireEvent.change(input,{target:{value:"0"}});
  fireEvent.change(screen.getByLabelText("Metres to finish"),{target:{value:"300"}});
  fireEvent.change(screen.getByLabelText("Successful landing boost"),{target:{value:"true"}});
  await waitFor(()=>expect(JSON.parse(calls().at(-1)![1]!.body as string).scenario).toMatchObject({ringsHeld:0,distanceToFinish:300,landingBoostActive:true}));
  await screen.findByText(/Scenario total/);
  fireEvent.click(screen.getByText("Scenario effects & details"));expect(screen.getByText("Active non-stat effects")).toBeTruthy();
});

it("keeps preview validation and controls outside the build-saving form", async () => {
  render(<form aria-label="Save build"><ScenarioPreview selections={[{ label: "Draft",
    selection: { ...selection, gadgetIds: ["ring"] } }]} /></form>);open();
  const input=await screen.findByLabelText(/Rings held now/) as HTMLInputElement;
  fireEvent.change(input,{target:{value:"1000"}});
  expect(input.form).toBeNull();expect(input.checkValidity()).toBe(false);
  expect((screen.getByRole("form",{name:"Save build"}) as HTMLFormElement).checkValidity()).toBe(true);
});
