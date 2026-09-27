import { useRef, useState } from "react";
import { act, cleanup, fireEvent, render, renderHook, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { BuildRecommendationDialog, SelectionLock } from "./BuildRecommendationDialog";
import { api, setToken } from "./api";
import { useBuildRecommendation } from "./useBuildRecommendation";
import { defaultPriorities, draftSelection, emptyLocks } from "./recommendation";
import type { RecommendationCatalog, RecommendationRequest, RecommendationResult } from "./recommendation";
import type { BuildDraft } from "./types";

vi.mock("./api", async original => ({ ...await original<typeof import("./api")>(), api: vi.fn() }));
const draft: BuildDraft = { title: "Keep", description: "Details", racerId: "r", frontPartId: "f", rearPartId: "b", tirePartId: "t",
  machineType: "SPEED", gameVersionId: "patch", gadgetIds: ["a"], recommendedMapIds: ["map"], mapRecommendationMode: "SELECTED", remixedFromBuildId: "source" };
const catalog: RecommendationCatalog = { racers: [{ id: "r", name: "Sonic", racingType: "SPEED", imagePath: null }],
  parts: ["f", "b", "t", "f2"].map((id, index) => ({ id, type: index === 1 ? "REAR" : index === 2 ? "TIRE" : "FRONT",
    sourceMachineId: id, sourceMachineName: `Machine ${id}`, racingType: "SPEED", sourceMachineImagePath: null })),
  gadgets: [{ id: "a", name: "Gadget A", description: null, slotCost: 1, imagePath: null }] };
const stats = { speed: 30, acceleration: 20, handling: 40, boost: 60, power: 50 };
const result: RecommendationResult = { outcome: "ESTABLISHED", selection: { ...draftSelection(draft), frontPartId: "f2" },
  currentStats: stats, recommendedStats: { ...stats, speed: 29, acceleration: 21 }, alreadyBest: false,
  reason: "Compared with current: first differing priority is ACCELERATION.", restrictions: ["Unreviewed rules are excluded."],
  ruleset: "test-rules", note: "Caps are not established.", work: 123, elapsedMillis: 2 };
const request: RecommendationRequest = { gameVersionId: "patch", machineType: "SPEED", priorities: defaultPriorities, current: draftSelection(draft), locked: emptyLocks() };
beforeEach(() => {
  vi.clearAllMocks(); vi.mocked(api).mockResolvedValue(result);
  Object.defineProperty(HTMLDialogElement.prototype, "showModal", { configurable: true, value: function(this: HTMLDialogElement) { this.open = true; } });
  Object.defineProperty(HTMLDialogElement.prototype, "close", { configurable: true, value: function(this: HTMLDialogElement) { this.open = false; } });
});
afterEach(() => { cleanup(); Reflect.deleteProperty(HTMLDialogElement.prototype, "showModal"); Reflect.deleteProperty(HTMLDialogElement.prototype, "close"); });
const applied = vi.fn();
function Harness({ patch = "1.4.1", locked = false, initial = draft }: { patch?: string; locked?: boolean; initial?: BuildDraft }) {
  const [open, setOpen] = useState(false); const trigger = useRef<HTMLButtonElement>(null);
  return <><button ref={trigger} onClick={() => setOpen(true)}>Recommend</button>{open && <BuildRecommendationDialog draft={initial}
    locks={locked ? { ...emptyLocks(), racerId: "r", tirePartId: "t", gadgetIds: ["a"] } : emptyLocks()} context="editor"
    catalog={catalog} version={{ id: "patch", version: patch, releasedAt: "2026-01-01" }} returnFocus={trigger.current}
    onClose={() => setOpen(false)} onApply={applied} />}</>;
}
function open() { fireEvent.click(screen.getByRole("button", { name: "Recommend" })); }
function calculate() { fireEvent.click(screen.getByRole("button", { name: "Calculate recommendation" })); }

it("opens configuration without requesting, exposes priorities/type and cancels without mutation", () => {
  render(<Harness />); open();
  expect(api).not.toHaveBeenCalled(); expect(screen.getByRole("heading", { name: "Recommend a build" })).toBe(document.activeElement);
  expect((screen.getByLabelText("Recommendation machine type") as HTMLSelectElement).value).toBe("SPEED");
  expect(screen.getByText("Nothing is locked. All selections may change.")).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Move Speed up" }));
  expect(within(screen.getByRole("list")).getAllByRole("listitem")[0].textContent).toContain("Speed");
  fireEvent.click(screen.getByRole("button", { name: "Move Speed down" }));
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
  expect(JSON.parse(vi.mocked(api).mock.calls[0][1]!.body as string)).toEqual(request);
  const table = screen.getByRole("table"); expect(table.textContent).toContain("+1"); expect(table.textContent).toContain("-1");
  expect(screen.getByLabelText("Recommendation explanation").textContent).toContain(result.reason);
  expect(applied).not.toHaveBeenCalled(); fireEvent.click(screen.getByRole("button", { name: "Apply to draft" }));
  expect(applied).toHaveBeenCalledWith({ ...draft, frontPartId: "f2" }); expect(screen.queryByRole("dialog")).toBeNull();
});

it("shows unavailable current stats and result outcome without claiming improvement", async () => {
  vi.mocked(api).mockResolvedValue({ ...result, currentStats: null, recommendedStats: null, outcome: "BEST_FOUND" });
  render(<Harness initial={{ ...draft, machineType: "BOOST", tirePartId: null }} />); open(); calculate();
  await screen.findByRole("heading", { name: "Recommended setup" });
  expect(screen.getByRole("table").textContent).toContain("Unavailable");
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
  act(() => { expect(hook.result.current.apply()).toBe(false); }); expect(hook.result.current.error).toContain("stale");
  hook.rerender({ context: "B", value: draft }); setToken("new-session");
  act(() => { expect(hook.result.current.apply()).toBe(false); }); expect(hook.result.current.error).toContain("earlier editor session");
  expect(applied).not.toHaveBeenCalled();
  act(() => hook.result.current.cancel()); act(() => { expect(hook.result.current.apply()).toBe(false); });
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
