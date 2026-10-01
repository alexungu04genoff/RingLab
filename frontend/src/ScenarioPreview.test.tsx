import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
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

it("shows the returned +0 known adjustment when all conditions are unspecified without claiming a final total", async () => {
  vi.mocked(api).mockImplementation(async path => path === "/stats/scenario-rules" ? controls : {
    ...result, total: null, coverage: "PARTIAL", passive: { ...result.passive, coverage: "PARTIAL" },
    effects: [{ gadgetId: "quick", gadgetName: "Quick Starter", effectId: "lap", label: "Lap", status: "CONDITION_UNKNOWN",
      statEffect: true, adjustment: null, explanation: "Specify a lap.", sources: [] },
    { gadgetId: "evolution", gadgetName: "Damage Evolution", effectId: "count", label: "Damage", status: "UNSUPPORTED",
      statEffect: true, adjustment: null, explanation: "Cap unverified.", sources: [] }],
  });
  render(<ScenarioPreview selections={[{ label: "Draft", selection }]} />);open();
  expect(await screen.findByText(/Scenario not fully calculated/)).toBeTruthy();
  expect(screen.getAllByText("+0")).toHaveLength(5);
  expect(screen.getByLabelText("Speed known subtotal").textContent).toBe("65");
  expect(screen.getByText("Passive 65 → Known subtotal 65")).toBeTruthy();
  expect(screen.getAllByText("Known effects only · final total unavailable")).toHaveLength(5);
  expect(screen.queryByText("Passive 65 → Scenario 65")).toBeNull();
  expect(screen.getByText(/Passive calculation is also incomplete/)).toBeTruthy();
  fireEvent.click(screen.getByText("Scenario effects & details"));
  expect(screen.getByText("Condition not specified")).toBeTruthy();expect(screen.getByText("Unsupported effects")).toBeTruthy();
});

function applied(gadgetName: string, speed: number): ScenarioResult["effects"][number] {
  return { gadgetId: gadgetName, gadgetName, effectId: "effect", label: "Stat effect", status: "ACTIVE_AND_APPLIED",
    statEffect: true, adjustment: { ...zero, speed }, explanation: "Reviewed effect", sources: [] };
}

it.each([[40, 105], [-20, 45]])("shows an exact signed adjustment %s with the returned total %s", async (delta, total) => {
  vi.mocked(api).mockImplementation(async path => path === "/stats/scenario-rules" ? controls : {
    ...result, adjustments: { ...zero, speed: delta }, total: { ...stats, speed: total },
    passive: { ...result.passive, base: { ...result.passive.base, speed: 55,
      character: { ...stats, speed: 25 }, machine: { ...zero, speed: 30 } }, adjustments: { ...zero, speed: 10 } },
    effects: [applied("Quick Starter", delta)],
  });
  render(<ScenarioPreview selections={[{ label: "Draft", selection }]} />); open();
  const bar = await screen.findByRole("img", { name: `Speed: passive 65; scenario adjustment ${delta > 0 ? "plus 40" : "minus 20"}; scenario total ${total}.` });
  expect(bar.closest(".stat-row")?.querySelector("dd")?.textContent).toBe(String(total));
  expect(screen.getByText(`Passive 65 → Scenario ${total}`)).toBeTruthy();
  const segment = bar.querySelector<HTMLElement>(".card-stat-scenario-segment")!;
  expect(segment.classList.contains(delta > 0 ? "bonus" : "penalty")).toBe(true);
  expect(parseFloat(segment.style.left)).toBeCloseTo(Math.min(65, total) * 100 / Math.max(100, total));
  expect(parseFloat(segment.style.width)).toBeCloseTo(Math.abs(delta) * 100 / Math.max(100, total));
  const passiveSegment = bar.querySelector<HTMLElement>(".card-stat-gadget-segment")!;
  expect(parseFloat(passiveSegment.style.left)).toBeCloseTo(55 * 100 / Math.max(100, total));
  expect(parseFloat(passiveSegment.style.width)).toBeCloseTo(10 * 100 / Math.max(100, total));
  expect(screen.getByText("Scenario", { selector: ".stat-legend span" })).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Reset to Passive only" }));
  expect(screen.queryByRole("img")).toBeNull();
  expect(screen.queryByText("Scenario", { selector: ".stat-legend span" })).toBeNull();
});

it("labels a returned known subtotal without promoting it to an exact total", async () => {
  vi.mocked(api).mockImplementation(async path => path === "/stats/scenario-rules" ? controls : {
    ...result, coverage: "PARTIAL", total: null, adjustments: { ...zero, speed: 20 },
    knownSubtotal: { ...stats, speed: 85 }, effects: [applied("Quick Starter", 20)],
  });
  render(<ScenarioPreview selections={[{ label: "Draft", selection }]} />); open();
  const bar = await screen.findByRole("img", { name: "Speed: passive 65; scenario adjustment plus 20; known subtotal 85; final total unavailable." });
  expect(bar.closest(".stat-row")?.querySelector("dd")?.textContent).toBe("85");
  expect(screen.getByText("Passive 65 → Known subtotal 85")).toBeTruthy();
  expect(screen.getByText("+20")).toBeTruthy();
  expect(screen.queryByText("Passive 65 → Scenario 85")).toBeNull();
  expect(screen.getByRole("img", { name: /Acceleration: passive 30; scenario adjustment plus 0; known subtotal 30/ })).toBeTruthy();
});

it("keeps genuinely unknown adjustments unavailable instead of displaying +0", async () => {
  vi.mocked(api).mockImplementation(async path => path === "/stats/scenario-rules" ? controls : {
    ...result, coverage: "PARTIAL", total: null, adjustments: { ...zero, speed: null },
    knownSubtotal: { ...stats, speed: null },
  });
  render(<ScenarioPreview selections={[{ label: "Draft", selection }]} />); open();
  const bar = await screen.findByRole("img", { name: /Speed: passive 65; not fully calculated/ });
  expect(bar.closest(".stat-row")?.querySelector("dd")?.textContent).toBe("—");
  expect(within(bar.closest(".stat-row") as HTMLElement).queryByText("+0")).toBeNull();
});

it("shows only passive bars when unavailable, even if a subtotal was returned", async () => {
  vi.mocked(api).mockImplementation(async path => path === "/stats/scenario-rules" ? controls : {
    ...result, coverage: "UNAVAILABLE", total: null,
  });
  render(<ScenarioPreview selections={[{ label: "Draft", selection }]} />); open();
  const bar = await screen.findByRole("img", { name: /Speed: passive 65; not fully calculated/ });
  expect(bar.closest(".stat-row")?.querySelector("dd")?.textContent).toBe("—");
  expect(bar.querySelector(".card-stat-scenario-segment")).toBeNull();
});

it("summarizes simultaneous effects and compares returned values on one scale with shared conditions", async () => {
  vi.mocked(api).mockImplementation(async (path, options) => {
    if (path === "/stats/scenario-rules") return controls;
    const body = JSON.parse(options!.body as string);
    return { ...result, adjustments: { ...zero, speed: body.racerId === "other" ? 20 : 40 },
      total: { ...stats, speed: body.racerId === "other" ? 85 : 105 },
      effects: [applied("Quick Starter", 20), applied("Sea Dog Kit", 20)] };
  });
  render(<ScenarioPreview selections={[{ label: "Left", selection },
    { label: "Right", selection: { ...selection, racerId: "other", gadgetIds: ["plane"] } }]} />); open();
  fireEvent.change(await screen.findByLabelText("Lap"), { target: { value: "1" } });
  fireEvent.change(screen.getByLabelText("Vehicle form"), { target: { value: "WATER" } });
  expect(await screen.findByText("Lap 1 · Water")).toBeTruthy();
  for (const summary of screen.getAllByLabelText("Applied scenario effects")) {
    expect(within(summary).getByText("Quick Starter")).toBeTruthy();
    expect(within(summary).getByText("Sea Dog Kit")).toBeTruthy();
  }
  const left = within(screen.getByLabelText("Scenario result: Left")).getByRole("img", { name: /Speed:/ });
  const right = within(screen.getByLabelText("Scenario result: Right")).getByRole("img", { name: /Speed:/ });
  expect(left.querySelector<HTMLElement>(".card-stat-character-fill")!.style.width)
    .toBe(right.querySelector<HTMLElement>(".card-stat-character-fill")!.style.width);
  expect(calls().slice(-2).map(([, options]) => JSON.parse(options!.body as string).scenario))
    .toEqual([expect.objectContaining({ lap: 1, vehicleForm: "WATER" }), expect.objectContaining({ lap: 1, vehicleForm: "WATER" })]);
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
