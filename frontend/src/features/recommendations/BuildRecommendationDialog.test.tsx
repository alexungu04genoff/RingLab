import { useRef, useState } from "react";
import { act, cleanup, fireEvent, render, renderHook, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { BuildRecommendationDialog } from "./BuildRecommendationDialog";
import { SelectionLock } from "./SelectionLock";
import { api, setToken } from "../../shared/api/api";
import { useBuildRecommendation } from "./useBuildRecommendation";
import { defaultPriorities, draftSelection, emptyLocks, recommendationIdentity, signedStatChange, validBalanced } from "./recommendation";
import type { RecommendationCatalog, RecommendationRequest, RecommendationResult } from "./recommendation";
import type { BuildDraft, BuildStatsResult } from "../../shared/types";

vi.mock("../../shared/api/api", async original => ({ ...await original<typeof import("../../shared/api/api")>(), api: vi.fn() }));
const collection = vi.hoisted(() => ({ status: "ready", busy: false, revision: 0, identity: "account",
  data: { racers: [] as string[], machines: [] as string[], gadgets: [] as string[] },
  refresh: vi.fn(async () => ({ data: { racers: [] as string[], machines: [] as string[], gadgets: [] as string[] },
    revision: 0, identity: "account" })) }));
vi.mock("../collection/Collection", async original => ({ ...await original<typeof import("../collection/Collection")>(), useCollection: () => collection }));
const draft: BuildDraft = { title: "Keep", description: "Details", racerId: "r", frontPartId: "f", rearPartId: "b", tirePartId: "t",
  machineType: "SPEED", gameVersionId: "patch", gadgetIds: ["a"], recommendedMapIds: ["map"], mapRecommendationMode: "SELECTED", remixedFromBuildId: "source" };
const catalog: RecommendationCatalog = { racers: [{ id: "r", name: "Sonic", racingType: "SPEED", imagePath: null }],
  parts: ["f", "b", "t", "f2"].map((id, index) => ({ id, type: index === 1 ? "REAR" : index === 2 ? "TIRE" : "FRONT",
    sourceMachineId: id, sourceMachineName: `Machine ${id}`, racingType: "SPEED", sourceMachineImagePath: null })),
  gadgets: [{ id: "a", name: "Gadget A", description: null, slotCost: 1, imagePath: null }] };
const stats = { speed: 30, acceleration: 20, handling: 40, boost: 60, power: 50 };
const zeroStats = { speed: 0, acceleration: 0, handling: 0, boost: 0, power: 0 };
const baseStats: BuildStatsResult = { ...stats, character: stats, machine: zeroStats };
const loadedStats: BuildStatsResult = { ...baseStats, passive: { base: baseStats, adjustments: zeroStats, adjusted: stats,
  coverage: "CALCULATED", ruleset: "test", supportedVersion: "1.4.1", note: "Reviewed effects", effects: [] } };
const result: RecommendationResult = { outcome: "ESTABLISHED", selection: { ...draftSelection(draft), frontPartId: "f2" },
  currentStats: stats, recommendedStats: { ...stats, speed: 29, acceleration: 21 }, alreadyBest: false,
  reason: "Compared with current: first differing priority is ACCELERATION.", restrictions: ["Unreviewed rules are excluded."],
  ruleset: "test-rules", note: "Caps are not established.", work: 123, elapsedMillis: 2 };
const request: RecommendationRequest = { gameVersionId: "patch", machineType: "SPEED", priorities: defaultPriorities, current: draftSelection(draft), locked: emptyLocks() };
it("blocks failed collection loads and revalidates every selected source before Apply", async () => {
  const hook = renderHook(() => useBuildRecommendation(draft, emptyLocks(), "A", catalog, applied));
  collection.status = "error"; hook.rerender();
  await act(async () => { await hook.result.current.calculate(request); });
  expect(api).not.toHaveBeenCalled();
  collection.status = "ready"; hook.rerender();
  await act(async () => { await hook.result.current.calculate(request); });
  collection.refresh.mockResolvedValue({ data: { racers: [], machines: ["b"], gadgets: [] }, revision: 0, identity: "account" });
  await act(async () => { expect(await hook.result.current.apply()).toBe(false); });
  expect(hook.result.current.error).toContain("no longer own");
  expect(applied).not.toHaveBeenCalled();
  expect(api).toHaveBeenCalledTimes(1);
});

it("invalidates proposals when collection changes during calculation", async () => {
  let resolve!: (value: RecommendationResult) => void;
  vi.mocked(api).mockReturnValue(new Promise(done => { resolve = done; }));
  const hook = renderHook(() => useBuildRecommendation(draft, emptyLocks(), "A", catalog, applied));
  act(() => { void hook.result.current.calculate(request); });
  collection.revision++; hook.rerender();
  await act(async () => resolve(result));
  await act(async () => { expect(await hook.result.current.apply()).toBe(false); });
  expect(hook.result.current.error).toContain("Collection settings changed");
  expect(applied).not.toHaveBeenCalled();
});

it("Cancel or unmount during Apply's collection check never writes the draft", async () => {
  for (const unmount of [false, true]) {
    let resolve!: (value: Awaited<ReturnType<typeof collection.refresh>>) => void;
    collection.refresh.mockReturnValueOnce(new Promise(done => { resolve = done; }));
    const hook = renderHook(() => useBuildRecommendation(draft, emptyLocks(), "A", catalog, applied));
    await act(async () => { await hook.result.current.calculate(request); });
    let applying!: Promise<boolean>;
    act(() => { applying = hook.result.current.apply(); });
    if (unmount) hook.unmount(); else act(() => hook.result.current.cancel());
    await act(async () => { resolve({ data: { racers: [], machines: [], gadgets: [] }, revision: 0, identity: "account" }); expect(await applying).toBe(false); });
    expect(applied).not.toHaveBeenCalled();
    hook.unmount();
  }
});
beforeEach(() => {
  collection.status = "ready"; collection.busy = false; collection.revision = 0; collection.identity = "account";
  collection.refresh.mockResolvedValue({ data: { racers: [], machines: [], gadgets: [] }, revision: 0, identity: "account" });
  vi.clearAllMocks(); vi.mocked(api).mockResolvedValue(result);
  Object.defineProperty(HTMLDialogElement.prototype, "showModal", { configurable: true, value: function(this: HTMLDialogElement) { this.open = true; } });
  Object.defineProperty(HTMLDialogElement.prototype, "close", { configurable: true, value: function(this: HTMLDialogElement) { this.open = false; } });
});
afterEach(() => { cleanup(); Reflect.deleteProperty(HTMLDialogElement.prototype, "showModal"); Reflect.deleteProperty(HTMLDialogElement.prototype, "close"); });
const applied = vi.fn();
function Harness({ patch = "1.4.1", locked = false, initial = draft, versionLoaded = true, referenceStats }: {
  patch?: string; locked?: boolean; initial?: BuildDraft; versionLoaded?: boolean; referenceStats?: BuildStatsResult | null;
}) {
  const [open, setOpen] = useState(false); const trigger = useRef<HTMLButtonElement>(null);
  return <><button ref={trigger} onClick={() => setOpen(true)}>Recommend</button>{open && <BuildRecommendationDialog draft={initial}
    locks={locked ? { ...emptyLocks(), racerId: "r", tirePartId: "t", gadgetIds: ["a"] } : emptyLocks()} context="editor"
    catalog={catalog} version={versionLoaded ? { id: "patch", version: patch, releasedAt: "2026-01-01" } : null} returnFocus={trigger.current}
    referenceStats={referenceStats} onClose={() => setOpen(false)} onApply={applied} />}</>;
}
function open() { fireEvent.click(screen.getByRole("button", { name: "Recommend" })); }
function calculate() { fireEvent.click(screen.getByRole("button", { name: "Calculate recommendation" })); }

it.each(["Strict", "Balanced"])("defaults to keeping gadgets and cancels a proposal when scope changes in %s", async mode => {
  render(<Harness referenceStats={loadedStats} />); open();
  if (mode === "Balanced") fireEvent.click(screen.getByRole("button", { name: "Balanced" }));
  expect((screen.getByRole("radio", { name: "Keep current" }) as HTMLInputElement).checked).toBe(true);
  calculate();
  await screen.findByRole("button", { name: "Apply to draft" });
  expect(JSON.parse(vi.mocked(api).mock.calls.at(-1)![1]!.body as string).gadgetScope).toBe("KEEP_CURRENT");
  fireEvent.click(screen.getByRole("radio", { name: "Optimize unlocked gadgets" }));
  expect(screen.queryByRole("button", { name: "Apply to draft" })).toBeNull();
  calculate();
  await screen.findByRole("button", { name: "Apply to draft" });
  expect(JSON.parse(vi.mocked(api).mock.calls.at(-1)![1]!.body as string).gadgetScope).toBe("OPTIMIZE_UNLOCKED");
  fireEvent.click(screen.getByRole("radio", { name: "Keep current" }));
  expect(screen.queryByRole("button", { name: "Apply to draft" })).toBeNull();
  expect(applied).not.toHaveBeenCalled();
});

it("opens configuration with current stats without requesting, exposes priorities/type and cancels without mutation", () => {
  render(<Harness referenceStats={loadedStats} />); open();
  expect(api).not.toHaveBeenCalled(); expect(screen.getByRole("heading", { name: "Recommend a build" })).toBe(document.activeElement);
  const guide = screen.getByRole("link", { name: "How recommendations work ↗" });
  expect(guide.getAttribute("href")).toBe("/guide#recommendations");
  expect(guide.getAttribute("target")).toBe("_blank");
  const reference = screen.getByLabelText("Frozen reference");
  expect(reference.querySelector("details")?.open).toBe(true);
  expect(within(reference).getByRole("img", { name: "This setup stays fixed for this recommendation session." })).toBeTruthy();
  expect(within(reference).getByRole("img", { name: "Racer type: Speed" })).toBeTruthy();
  expect(within(reference).getAllByRole("img", { name: "Machine type: Speed" })).toHaveLength(3);
  expect((screen.getByLabelText("Recommendation machine type") as HTMLSelectElement).value).toBe("SPEED");
  expect(screen.getByLabelText("Acceleration current: 20").textContent).toContain("20");
  expect(screen.getByLabelText("Boost current: 60").textContent).toContain("60");
  expect(screen.getByText("Nothing is locked. Gadget scope controls whether gadgets may change.")).toBeTruthy();
  expect(within(screen.getByRole("list")).getAllByRole("listitem").map(row => row.querySelector(".type-badge")?.textContent))
    .toEqual(["Boost", "Power", "Handling", "Speed", "Acceleration"]);
  fireEvent.click(screen.getByRole("button", { name: "Move Power up" }));
  expect(within(screen.getByRole("list")).getAllByRole("listitem")[0].textContent).toContain("Power");
  fireEvent.click(screen.getByRole("button", { name: "Move Power down" }));
  fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
  expect(screen.queryByRole("dialog")).toBeNull(); expect(applied).not.toHaveBeenCalled();
  expect(document.activeElement).toBe(screen.getByRole("button", { name: "Recommend" }));
});

it("wraps Tab and Shift+Tab inside enabled dialog controls, including initial heading focus", async () => {
  render(<Harness />); open();
  const heading = screen.getByRole("heading", { name: "Recommend a build" });
  const close = screen.getByRole("button", { name: "Close recommendation" });
  const cancel = screen.getByRole("button", { name: "Cancel" });
  expect(document.activeElement).toBe(heading);
  fireEvent.keyDown(heading, { key: "Tab" }); expect(document.activeElement).toBe(close);
  fireEvent.keyDown(close, { key: "Tab", shiftKey: true }); expect(document.activeElement).toBe(cancel);
  fireEvent.keyDown(cancel, { key: "Tab" }); expect(document.activeElement).toBe(close);
  heading.focus(); fireEvent.keyDown(heading, { key: "Tab", shiftKey: true }); expect(document.activeElement).toBe(cancel);
  const type = screen.getByLabelText("Recommendation machine type"); type.focus();
  const normalTab = new KeyboardEvent("keydown", { key: "Tab", bubbles: true, cancelable: true });
  fireEvent(type, normalTab); expect(normalTab.defaultPrevented).toBe(false);
  const browserShortcut = new KeyboardEvent("keydown", { key: "Tab", ctrlKey: true, bubbles: true, cancelable: true });
  fireEvent(cancel, browserShortcut); expect(browserShortcut.defaultPrevented).toBe(false);
  calculate(); await screen.findByRole("heading", { name: "Recommended setup" });
  const resultCancel = screen.getByRole("button", { name: "Cancel" }); resultCancel.focus();
  fireEvent.keyDown(resultCancel, { key: "Tab" }); expect(document.activeElement).toBe(close);
  fireEvent.keyDown(close, { key: "Tab", shiftKey: true }); expect(document.activeElement).toBe(resultCancel);
});

it("renders locked thumbnails, precise type conflicts and unsupported patches", () => {
  const mounted = render(<Harness locked />); open();
  expect(screen.getByLabelText("Locked selections").textContent).toContain("Sonic");
  fireEvent.change(screen.getByLabelText("Recommendation machine type"), { target: { value: "BOOST" } });
  expect(screen.getByText(/Unlock the Tire before choosing Boost/)).toBeTruthy();
  expect((screen.getByRole("button", { name: "Calculate recommendation" }) as HTMLButtonElement).disabled).toBe(true);
  fireEvent.click(screen.getByRole("button", { name: "Close recommendation" })); mounted.unmount();
  render(<Harness patch="1.3.1" />); open(); expect(screen.getByText(/unavailable for this patch/)).toBeTruthy();
  expect((screen.getByRole("button", { name: "Calculate recommendation" }) as HTMLButtonElement).disabled).toBe(true);
});

it("drags colorful priority badges while retaining keyboard reorder controls", () => {
  render(<Harness />); open();
  const rows = within(screen.getByRole("list")).getAllByRole("listitem");
  const transfer = { setData: vi.fn(), getData: () => "POWER", effectAllowed: "", dropEffect: "" };
  expect(rows[4].querySelector(".racing-type-power")).toBeTruthy();
  fireEvent.dragStart(rows[4], { dataTransfer: transfer });
  expect(transfer.setData).toHaveBeenCalledWith("text/plain", "POWER");
  fireEvent.dragOver(rows[0], { dataTransfer: transfer }); fireEvent.drop(rows[0], { dataTransfer: transfer });
  fireEvent.dragEnd(rows[4], { dataTransfer: transfer });
  expect(within(screen.getByRole("list")).getAllByRole("listitem")[0].textContent).toContain("Power");
  expect((screen.getByRole("button", { name: "Move Power up" }) as HTMLButtonElement).disabled).toBe(true);
  expect(api).not.toHaveBeenCalled();
});

it("sends one async request, displays honest deltas/explanation and explicitly applies", async () => {
  render(<Harness />); open(); calculate();
  expect(screen.getByRole("status").textContent).toBe("Calculating recommendation…");
  await screen.findByRole("heading", { name: "Recommended setup" });
  expect(vi.mocked(api).mock.calls).toHaveLength(1);
  expect(JSON.parse(vi.mocked(api).mock.calls[0][1]!.body as string)).toEqual({ ...request, gadgetScope: "KEEP_CURRENT" });
  const comparison = screen.getByRole("region", { name: "Stat comparison" });
  expect(comparison.textContent).toContain("+1"); expect(comparison.textContent).toContain("-1");
  expect(within(comparison).getByRole("img", { name: "Acceleration: 20 to 21" })).toBeTruthy();
  expect(within(comparison).getByRole("img", { name: "Speed: 30 to 29" })).toBeTruthy();
  expect(screen.getByLabelText("Recommendation explanation").textContent).toContain(result.reason);
  expect(applied).not.toHaveBeenCalled(); await act(async () => { fireEvent.click(screen.getByRole("button", { name: "Apply to draft" })); });
  expect(applied).toHaveBeenCalledWith({ ...draft, frontPartId: "f2" }); expect(screen.queryByRole("dialog")).toBeNull();
});

it("explains stat gains and losses on hover, keyboard focus and tap without applying the result", async () => {
  render(<Harness />); open(); calculate();
  await screen.findByRole("heading", { name: "Recommended setup" });
  const gain = screen.getByRole("button", { name: "Acceleration change: +1 points" });
  const loss = screen.getByRole("button", { name: "Speed change: -1 points" });
  expect(screen.queryByRole("tooltip")).toBeNull();
  fireEvent.mouseEnter(gain);
  expect(screen.getByRole("tooltip").textContent).toContain("Starting 20 → Recommended 21");
  expect(screen.getByRole("tooltip").textContent).toContain("Outlined extension: increase");
  fireEvent.mouseLeave(gain);
  expect(screen.queryByRole("tooltip")).toBeNull();
  fireEvent.focus(loss);
  expect(screen.getByRole("tooltip").textContent).toContain("Starting 30 → Recommended 29");
  expect(screen.getByRole("tooltip").textContent).toContain("Hatched reduction: decrease");
  expect(loss.getAttribute("aria-describedby")).toBe(screen.getByRole("tooltip").id);
  fireEvent.keyDown(loss, { key: "Escape" });
  expect(screen.queryByRole("tooltip")).toBeNull();
  expect(screen.getByRole("dialog")).toBeTruthy();
  fireEvent.click(gain);
  expect(screen.getByRole("tooltip").textContent).toContain("Acceleration · +1 points");
  expect(applied).not.toHaveBeenCalled();
});

it("shows unavailable current stats and result outcome without claiming improvement", async () => {
  vi.mocked(api).mockResolvedValue({ ...result, currentStats: null, recommendedStats: null, outcome: "BEST_FOUND" });
  render(<Harness initial={{ ...draft, machineType: "BOOST", tirePartId: null }} />); open(); calculate();
  await screen.findByRole("heading", { name: "Recommended setup" });
  expect(screen.getByRole("region", { name: "Stat comparison" }).textContent).toContain("Unavailable");
  expect(screen.getByText(/Boost uses Front and Rear only/)).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Back to priorities" }));
  expect(screen.queryByRole("button", { name: "Apply to draft" })).toBeNull(); expect(api).toHaveBeenCalledTimes(1);
  vi.mocked(api).mockResolvedValue({ ...result, outcome: "UNAVAILABLE", selection: null }); calculate();
  await screen.findByRole("heading", { name: "Recommendation unavailable" }); expect(screen.queryByRole("button", { name: "Apply to draft" })).toBeNull();
});

it("allows retry after an error and aborts canceled requests while ignoring late responses", async () => {
  vi.mocked(api).mockRejectedValueOnce(new Error("Rate limit. Try again in 10 seconds."));
  render(<Harness />); open(); calculate(); await screen.findByText(/Rate limit/);
  let resolve!: (value: RecommendationResult) => void;
  vi.mocked(api).mockImplementationOnce(() => new Promise(done => { resolve = done; })); calculate();
  const signal = vi.mocked(api).mock.calls[1][1]!.signal!;
  fireEvent(screen.getByRole("dialog"), new Event("cancel", { cancelable: true })); expect(signal.aborted).toBe(true);
  await act(async () => resolve(result)); expect(screen.queryByRole("dialog")).toBeNull(); expect(applied).not.toHaveBeenCalled();
  open(); expect(screen.queryByRole("button", { name: "Apply to draft" })).toBeNull();
});

it("lock buttons are independent buttons and never submit or toggle enclosing selection", () => {
  const clicked = vi.fn(), submitted = vi.fn(), checked = vi.fn();
  render(<form onSubmit={submitted}><label><input type="checkbox" onChange={checked} />Gadget</label>
    <SelectionLock label="Gadget" locked={false} onClick={clicked} /></form>);
  fireEvent.click(screen.getByRole("button", { name: "Lock Gadget" }));
  expect(clicked).toHaveBeenCalledOnce(); expect(checked).not.toHaveBeenCalled(); expect(submitted).not.toHaveBeenCalled();
});

it("hook ignores stale session/context replies, rejects stale apply and prevents duplicate submissions", async () => {
  let resolve!: (value: RecommendationResult) => void;
  vi.mocked(api).mockImplementation(() => new Promise(done => { resolve = done; }));
  const hook = renderHook(({ context, value }) => useBuildRecommendation(value, emptyLocks(), context, catalog, applied),
    { initialProps: { context: "A", value: draft } });
  act(() => { void hook.result.current.calculate(request); void hook.result.current.calculate(request); });
  expect(api).toHaveBeenCalledTimes(1);
  const oldResolve = resolve; const signal = vi.mocked(api).mock.calls[0][1]!.signal!;
  hook.rerender({ context: "B", value: draft }); expect(signal.aborted).toBe(true);
  await act(async () => oldResolve(result)); expect(hook.result.current.result).toBeNull();
  act(() => { void hook.result.current.calculate(request); });
  await act(async () => resolve(result));
  hook.rerender({ context: "B", value: { ...draft, frontPartId: "f2" } });
  await act(async () => { expect(await hook.result.current.apply()).toBe(false); }); expect(hook.result.current.error).toContain("stale");
  hook.rerender({ context: "B", value: draft }); setToken("new-session");
  await act(async () => { expect(await hook.result.current.apply()).toBe(false); }); expect(hook.result.current.error).toContain("earlier editor session");
  expect(applied).not.toHaveBeenCalled();
  act(() => hook.result.current.cancel()); await act(async () => { expect(await hook.result.current.apply()).toBe(false); });
});

it("aborts on unmount and ignores superseded responses after another calculation", async () => {
  const responses: ((value: RecommendationResult) => void)[] = [];
  vi.mocked(api).mockImplementation(() => new Promise(done => responses.push(done)));
  const hook = renderHook(() => useBuildRecommendation(draft, emptyLocks(), "A", catalog, applied));
  act(() => { void hook.result.current.calculate(request); });
  act(() => hook.result.current.cancel()); act(() => { void hook.result.current.calculate(request); });
  await act(async () => responses[0](result)); expect(hook.result.current.busy).toBe(true);
  await act(async () => responses[1]({ ...result, alreadyBest: true })); expect(hook.result.current.result?.alreadyBest).toBe(true);
  act(() => { void hook.result.current.calculate(request); }); const signal = vi.mocked(api).mock.calls[2][1]!.signal!;
  hook.unmount(); expect(signal.aborted).toBe(true);
});

it("freezes Balanced reference, defaults to zero losses and restores previous settings and rank", async () => {
  vi.mocked(api).mockImplementation(async path => path.startsWith("/stats/")
    ? { passive: { coverage: "CALCULATED", adjusted: stats } }
    : { ...result, balanced: { minimum: { BOOST: 57, SPEED: 30, ACCELERATION: 20, HANDLING: 40 }, secondaryTieBreakDecided: false } });
  render(<Harness locked />); open();
  fireEvent.click(screen.getByRole("button", { name: "Balanced" }));
  await screen.findByLabelText("Boost current: 60");
  const prioritiesTable = screen.getByRole("table", { name: "Stat priorities" });
  for (const heading of ["Stat", "Move", "Current", "Maximum loss", "Minimum allowed", "Tie-break / Ignore"])
    expect(within(prioritiesTable).getByRole("columnheader", { name: heading })).toBeTruthy();
  expect(within(prioritiesTable).getAllByRole("row")).toHaveLength(6);
  expect(within(prioritiesTable).getAllByText("Maximum loss")).toHaveLength(1);
  const boost = screen.getByLabelText("Boost maximum loss (%)") as HTMLInputElement;
  expect(boost.value).toBe("0");
  fireEvent.change(boost, { target: { value: "5" } });
  expect(screen.getByLabelText("Boost minimum allowed: 57")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Move Boost to tie-break / ignore" }));
  expect(screen.queryByLabelText("Boost maximum loss (%)")).toBeNull();
  const secondaryTable = screen.getByRole("table", { name: "Tie-break / Ignore stats" });
  expect(within(secondaryTable).getByRole("columnheader", { name: "Current" })).toBeTruthy();
  expect(within(secondaryTable).getByRole("rowheader", { name: "Boost" })).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Restore Boost" }));
  expect(within(prioritiesTable).getAllByRole("rowheader").map(row => row.textContent))
    .toEqual(["Acceleration", "Speed", "Handling", "Power", "Boost"]);
  expect((screen.getByRole("button", { name: "Move Boost down" }) as HTMLButtonElement).disabled).toBe(true);
  expect((screen.getByLabelText("Boost maximum loss (%)") as HTMLInputElement).value).toBe("5");
  fireEvent.click(screen.getByRole("button", { name: "Move Power to tie-break / ignore" }));
  calculate(); await screen.findByRole("heading", { name: "Recommended setup" });
  const calls = vi.mocked(api).mock.calls.filter(([path]) => path === "/build-recommendations");
  expect(JSON.parse(calls[0][1]!.body as string)).toMatchObject({ mode: "BALANCED", current: draftSelection(draft),
    priorities: ["ACCELERATION", "SPEED", "HANDLING", "BOOST"],
    balanced: { maximumLossPercent: { BOOST: 5, SPEED: 0, ACCELERATION: 0, HANDLING: 0 }, secondary: ["POWER"] } });
  expect(screen.getByRole("region", { name: "Stat comparison" }).textContent).toContain("Tie-break / Ignore — no minimum");
  expect(screen.getByRole("region", { name: "Stat comparison" }).textContent).toContain("-3.3333%");
  expect(applied).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Back to priorities" }));
  expect(screen.getByLabelText("Boost minimum allowed: 57")).toBeTruthy();
  calculate(); await screen.findByRole("heading", { name: "Recommended setup" });
  const repeated = vi.mocked(api).mock.calls.filter(([path]) => path === "/build-recommendations");
  expect(JSON.parse(repeated[1][1]!.body as string).current).toEqual(draftSelection(draft));
  await act(async () => { fireEvent.click(screen.getByRole("button", { name: "Apply to draft" })); });
  expect(applied).toHaveBeenCalledWith({ ...draft, frontPartId: "f2" });
});

it("allows zero-loss Balanced calculation when the frozen patch's catalog details finish loading", async () => {
  vi.mocked(api).mockImplementation(async path => path.startsWith("/stats/")
    ? { passive: { coverage: "CALCULATED", adjusted: stats } } : result);
  const mounted = render(<Harness versionLoaded={false} />); open();
  fireEvent.click(screen.getByRole("button", { name: "Balanced" }));
  await screen.findByLabelText("Boost current: 60");
  const button = screen.getByRole("button", { name: "Calculate recommendation" }) as HTMLButtonElement;
  expect(button.disabled).toBe(true);
  expect(document.getElementById(button.getAttribute("aria-describedby")!)?.textContent).toContain("patch details");

  mounted.rerender(<Harness />);
  expect(button.disabled).toBe(false);
  expect(button.getAttribute("aria-describedby")).toBeNull();
  calculate(); await screen.findByRole("heading", { name: "Recommended setup" });
  const call = vi.mocked(api).mock.calls.find(([path]) => path === "/build-recommendations")!;
  expect(JSON.parse(call[1]!.body as string)).toMatchObject({ gameVersionId: "patch", mode: "BALANCED",
    current: draftSelection(draft), balanced: { maximumLossPercent: { SPEED: 0, ACCELERATION: 0, HANDLING: 0, BOOST: 0, POWER: 0 } } });
});

it("compares Balanced selections with type badges and explicit unchanged rows, including gadgets", async () => {
  vi.mocked(api).mockImplementation(async path => path.startsWith("/stats/")
    ? { passive: { coverage: "CALCULATED", adjusted: stats } } : result);
  render(<Harness locked />); open();
  fireEvent.click(screen.getByRole("button", { name: "Balanced" }));
  await screen.findByLabelText("Boost current: 60");
  const reference = screen.getByLabelText("Frozen reference").querySelector("details")!;
  expect(reference.open).toBe(true);
  const body = screen.getByRole("dialog").querySelector(".recommendation-body")!;
  body.scrollTop = 200;
  calculate(); await screen.findByRole("heading", { name: "Recommended setup" });
  await waitFor(() => expect(body.scrollTop).toBe(0));
  expect(screen.getByLabelText("Frozen reference").querySelector("details")!.open).toBe(false);
  const comparison = screen.getByRole("region", { name: "Setup comparison" });
  expect(within(comparison).getByRole("img", { name: "Racer type: Speed" })).toBeTruthy();
  expect(within(screen.getByLabelText("Recommended Front")).getByRole("img", { name: "Machine type: Speed" })).toBeTruthy();
  expect(screen.getByLabelText("Current Front").textContent).toContain("Machine f");
  expect(screen.getByLabelText("Recommended Front").textContent).toContain("Machine f2");
  expect(screen.getByLabelText("Recommended Racer").textContent).toBe("No change");
  expect(screen.getByLabelText("Recommended gadgets").textContent).toBe("No change");
  expect(screen.getByLabelText("Current Racer").parentElement?.textContent).toContain("Locked");
  expect(applied).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Back to priorities" }));
  expect(screen.getByLabelText("Frozen reference").querySelector("details")!.open).toBe(true);
  const summary = screen.getByLabelText("Frozen reference").querySelector("summary")!;
  summary.focus();
  const tab = new KeyboardEvent("keydown", { key: "Tab", bubbles: true, cancelable: true });
  fireEvent(summary, tab); expect(tab.defaultPrevented).toBe(false);
});

it("shows a changed gadget list and correctly labels an unchanged empty list", async () => {
  vi.mocked(api).mockImplementation(async path => path.startsWith("/stats/")
    ? { passive: { coverage: "CALCULATED", adjusted: stats } }
    : { ...result, selection: { ...result.selection!, gadgetIds: [] } });
  const mounted = render(<Harness />); open();
  fireEvent.click(screen.getByRole("button", { name: "Balanced" }));
  await screen.findByLabelText("Boost current: 60"); calculate();
  await screen.findByRole("heading", { name: "Recommended setup" });
  expect(within(screen.getByLabelText("Current gadgets")).getByText("Gadget A", { exact: true })).toBeTruthy();
  expect(screen.getByLabelText("Recommended gadgets").textContent).toBe("None");
  fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
  mounted.rerender(<Harness initial={{ ...draft, gadgetIds: [] }} />); open();
  fireEvent.click(screen.getByRole("button", { name: "Balanced" }));
  await screen.findByLabelText("Boost current: 60"); calculate();
  await screen.findByRole("heading", { name: "Recommended setup" });
  expect(screen.getByLabelText("Current gadgets").textContent).toBe("None");
  expect(screen.getByLabelText("Recommended gadgets").textContent).toBe("No change");
});

it("names unsupported gadget effects and offers a usable Strict fallback", () => {
  const partial: BuildStatsResult = { ...loadedStats, passive: { ...loadedStats.passive!, coverage: "PARTIAL", effects: [{
    gadgetId: "a", gadgetName: "Gadget A", effectId: "unreviewed", label: "Unverified effects", status: "UNSUPPORTED",
    adjustment: zeroStats, explanation: "No reviewed rule for this gadget.", sources: [],
  }] } };
  render(<Harness referenceStats={partial} />); open();
  fireEvent.click(screen.getByRole("button", { name: "Balanced" }));
  const button = screen.getByRole("button", { name: "Calculate recommendation" }) as HTMLButtonElement;
  expect(button.disabled).toBe(true);
  expect(screen.getByRole("status").textContent).toContain("Gadget A: No reviewed rule");
  expect(screen.getByRole("status").textContent).not.toContain("complete your racer");
  expect(screen.getByRole("button", { name: "Edit starting setup" })).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Use Strict" }));
  expect(button.disabled).toBe(false);
  expect(api).not.toHaveBeenCalled();
});

it("allows Balanced with reviewed non-stat gadgets without inventing stat bonuses", () => {
  const nonStat: BuildStatsResult = { ...loadedStats, passive: { ...loadedStats.passive!, effects: [{
    gadgetId: "a", gadgetName: "Drift Spinner Kit", effectId: "drift-spinner", label: "Drift charge timing and knockback",
    status: "NON_STAT", adjustment: zeroStats, explanation: "Race-time effects, not five-stat point adjustments.", sources: [],
  }] } };
  render(<Harness referenceStats={nonStat} />); open();
  fireEvent.click(screen.getByRole("button", { name: "Balanced" }));
  expect((screen.getByRole("button", { name: "Calculate recommendation" }) as HTMLButtonElement).disabled).toBe(false);
  expect(screen.getByLabelText("Handling current: 40")).toBeTruthy();
});

it("calculates Balanced for a remixed setup whose race-time gadgets contribute zero passive stats", async () => {
  const gadgets = [
    { id: "70000000-0000-4000-8000-000000000007", name: "Damage Evolution", slotCost: 1 },
    { id: "70000000-0000-4000-8000-000000000004", name: "Invincible Finish", slotCost: 3 },
    { id: "70000000-0000-4000-8000-000000000002", name: "Perfect Landing", slotCost: 1 },
  ].map(gadget => ({ ...gadget, description: null, imagePath: null }));
  const remixed = { ...draft, gadgetIds: gadgets.map(gadget => gadget.id) };
  const reference: BuildStatsResult = { ...loadedStats, passive: { ...loadedStats.passive!, effects: gadgets.map(gadget => ({
    gadgetId: gadget.id, gadgetName: gadget.name, effectId: "other-0", label: "Race-time effect",
    status: "CONDITIONAL", adjustment: zeroStats, explanation: "No race event is assumed.", sources: [],
  })) } };
  vi.mocked(api).mockResolvedValue({ ...result, selection: draftSelection(remixed), recommendedStats: stats });
  render(<BuildRecommendationDialog draft={remixed} locks={emptyLocks()} context="remix"
    catalog={{ ...catalog, gadgets }} version={{ id: "patch", version: "1.4.1", releasedAt: "2026-06-23" }}
    referenceStats={reference} returnFocus={null} onClose={vi.fn()} onApply={applied} />);
  fireEvent.click(screen.getByRole("button", { name: "Balanced" }));
  expect((screen.getByRole("button", { name: "Calculate recommendation" }) as HTMLButtonElement).disabled).toBe(false);
  expect(screen.getByLabelText("Speed current: 30")).toBeTruthy();
  calculate();
  await screen.findByRole("heading", { name: "Recommended setup" });
  const call = vi.mocked(api).mock.calls.find(([path]) => path === "/build-recommendations")!;
  expect(JSON.parse(call[1]!.body as string)).toMatchObject({ mode: "BALANCED", current: draftSelection(remixed) });
  expect(applied).not.toHaveBeenCalled();
});

it("explains a missing patch beside Calculate and requires reopening after the draft changes", async () => {
  vi.mocked(api).mockResolvedValue({ passive: { coverage: "CALCULATED", adjusted: stats } });
  const mounted = render(<Harness initial={{ ...draft, gameVersionId: null }} />); open();
  fireEvent.click(screen.getByRole("button", { name: "Balanced" }));
  const button = screen.getByRole("button", { name: "Calculate recommendation" }) as HTMLButtonElement;
  expect(button.disabled).toBe(true);
  expect(document.getElementById(button.getAttribute("aria-describedby")!)?.textContent).toContain("Game version / Patch");
  expect(api).not.toHaveBeenCalled();

  mounted.rerender(<Harness />);
  expect(button.disabled).toBe(true);
  expect(screen.getByRole("status").textContent).toContain("Close and reopen");
  fireEvent.click(screen.getByRole("button", { name: "Cancel" })); open();
  fireEvent.click(screen.getByRole("button", { name: "Balanced" }));
  await screen.findByLabelText("Boost current: 60");
  expect((screen.getByRole("button", { name: "Calculate recommendation" }) as HTMLButtonElement).disabled).toBe(false);
});

it("requires one active stat and valid finite losses, preserves mode settings, and cancels without mutation", async () => {
  vi.mocked(api).mockResolvedValue({ passive: { coverage: "CALCULATED", adjusted: stats } });
  render(<Harness />); open(); fireEvent.click(screen.getByRole("button", { name: "Balanced" }));
  await screen.findByLabelText("Boost current: 60");
  for (const name of ["Power", "Handling", "Speed", "Acceleration"])
    fireEvent.click(screen.getByRole("button", { name: `Move ${name} to tie-break / ignore` }));
  expect((screen.getByRole("button", { name: "Move Boost to tie-break / ignore" }) as HTMLButtonElement).disabled).toBe(true);
  for (const value of ["", "-1", "101"]) {
    fireEvent.change(screen.getByLabelText("Boost maximum loss (%)"), { target: { value } });
    expect((screen.getByRole("button", { name: "Calculate recommendation" }) as HTMLButtonElement).disabled).toBe(true);
  }
  fireEvent.change(screen.getByLabelText("Boost maximum loss (%)"), { target: { value: "5.5" } });
  fireEvent.click(screen.getByRole("button", { name: "Strict" }));
  expect(screen.getByRole("button", { name: "Move Power up" })).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Balanced" })); await screen.findByLabelText("Boost current: 60");
  expect((screen.getByLabelText("Boost maximum loss (%)") as HTMLInputElement).value).toBe("5.5");
  fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
  expect(applied).not.toHaveBeenCalled();
  expect(vi.mocked(api).mock.calls.every(([path]) => path.startsWith("/stats/"))).toBe(true);
});

it("reports unsupported or negative reference values instead of offering Balanced calculation", async () => {
  vi.mocked(api).mockResolvedValue({ passive: { coverage: "CALCULATED", adjusted: { ...stats, power: -1 } } });
  render(<Harness />); open(); fireEvent.click(screen.getByRole("button", { name: "Balanced" }));
  await screen.findByText("Balanced cannot use a starting stat below zero. Change your setup in the editor, or use Strict.");
  expect((screen.getByRole("button", { name: "Calculate recommendation" }) as HTMLButtonElement).disabled).toBe(true);
});

it("configuration changes abort calculations and discard late responses and proposals", async () => {
  const responses: ((value: RecommendationResult) => void)[] = [];
  vi.mocked(api).mockImplementation(() => new Promise(done => responses.push(done)));
  const hook = renderHook(({ configuration }) => useBuildRecommendation(draft, emptyLocks(), "A", catalog, applied, configuration),
    { initialProps: { configuration: "strict" } });
  act(() => { void hook.result.current.calculate(request); });
  const signal = vi.mocked(api).mock.calls[0][1]!.signal!;
  hook.rerender({ configuration: "balanced-loss5" }); expect(signal.aborted).toBe(true);
  act(() => { void hook.result.current.calculate(request); });
  await act(async () => responses[0](result)); expect(hook.result.current.result).toBeNull();
  await act(async () => responses[1](result)); expect(hook.result.current.result).toEqual(result);
  for (const configuration of ["balanced-reordered", "balanced-loss10", "balanced-secondary", "balanced-boost"]) {
    hook.rerender({ configuration }); expect(hook.result.current.result).toBeNull();
    await act(async () => { expect(await hook.result.current.apply()).toBe(false); });
  }
  expect(applied).not.toHaveBeenCalled();
});

it("rejects a changed draft before calculation against an earlier frozen reference", () => {
  const hook = renderHook(() => useBuildRecommendation({ ...draft, frontPartId: "f2" }, emptyLocks(), "A", catalog, applied,
    "balanced", recommendationIdentity(draft, emptyLocks())));
  act(() => { void hook.result.current.calculate(request); });
  expect(hook.result.current.error).toContain("Close and reopen"); expect(api).not.toHaveBeenCalled();
});

it("validates stat partitions and renders positive, negative and zero-baseline changes honestly", () => {
  const losses = { BOOST: "5", SPEED: "10", ACCELERATION: "25", HANDLING: "50", POWER: "0" };
  expect(validBalanced(["BOOST"], ["SPEED", "ACCELERATION", "HANDLING", "POWER"], losses)).toBe(true);
  expect(validBalanced([], defaultPriorities, losses)).toBe(false);
  expect(validBalanced(["BOOST"], ["BOOST", "SPEED", "ACCELERATION", "HANDLING"], losses)).toBe(false);
  for (const value of ["NaN", "Infinity", "-1", "101", ""])
    expect(validBalanced(defaultPriorities, [], { ...losses, BOOST: value })).toBe(false);
  expect(signedStatChange(80, 76)).toBe("-5%"); expect(signedStatChange(80, 84)).toBe("+5%");
  expect(signedStatChange(0, 1)).toBe("+1 points"); expect(signedStatChange(0, 0)).toBe("0 points");
  expect(signedStatChange(null, 10)).toBe("Unavailable");
  expect(signedStatChange(80, 79.9999999)).toBe("−<0.0001%");
});
