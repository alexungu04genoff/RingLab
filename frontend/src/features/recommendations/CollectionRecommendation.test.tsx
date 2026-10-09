import { act, cleanup, fireEvent, renderHook, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { CollectionProvider, useCollection, type CollectionExclusions } from "../collection/Collection";
import { useBuildRecommendation } from "./useBuildRecommendation";
import { defaultPriorities, draftSelection, emptyLocks } from "./recommendation";
import type { RecommendationCatalog, RecommendationRequest, RecommendationResult } from "./recommendation";
import type { BuildDraft } from "../../shared/types";
import { api, setToken } from "../../shared/api/api";

const auth = vi.hoisted(() => ({ user: { id: "a" }, loading: false, sessionError: "" }));
vi.mock("../auth/auth", () => ({ useAuth: () => auth }));
vi.mock("../../shared/api/api", async original => ({ ...await original<typeof import("../../shared/api/api")>(), api: vi.fn() }));
const empty: CollectionExclusions = { racers: [], machines: [], gadgets: [] };
const draft: BuildDraft = { visibility: "PUBLIC", title: "", description: "", racerId: "r", frontPartId: "f", rearPartId: "b", tirePartId: null,
  machineType: "BOOST", gameVersionId: "v", gadgetIds: [], recommendedMapIds: [], mapRecommendationMode: "SELECTED", remixedFromBuildId: null };
const catalog: RecommendationCatalog = {
  racers: [{ id: "r", name: "Racer", racingType: "BOOST", imagePath: null }],
  parts: ["f", "b"].map((id, index) => ({ id, type: index === 0 ? "FRONT" : "REAR", sourceMachineId: "m",
    sourceMachineName: "Machine", racingType: "BOOST", sourceMachineImagePath: null })),
  gadgets: []
};
const stats = { speed: 10, acceleration: 20, handling: 30, power: 40, boost: 50 };
const result: RecommendationResult = { outcome: "ESTABLISHED", selection: draftSelection(draft),
  currentStats: stats, recommendedStats: stats, alreadyBest: true, reason: "Test", restrictions: [],
  ruleset: "test", note: "", work: 1, elapsedMillis: 0 };
const request: RecommendationRequest = { gameVersionId: "v", machineType: "BOOST", priorities: defaultPriorities,
  current: draftSelection(draft), locked: emptyLocks() };
const applied = vi.fn();
function mount() {
  return renderHook(() => ({ collection: useCollection(),
    recommendation: useBuildRecommendation(draft, emptyLocks(), "editor", catalog, applied) }), { wrapper: CollectionProvider });
}
beforeEach(() => {
  vi.clearAllMocks(); auth.user = { id: "a" }; setToken("test-a");
  vi.mocked(api).mockImplementation(async path => path === "/collection" ? empty : result);
});
afterEach(cleanup);

it("keeps a recommendation applicable after an identical focus refresh", async () => {
  const hook = mount();
  await waitFor(() => expect(hook.result.current.collection.status).toBe("ready"));
  await act(async () => { await hook.result.current.recommendation.calculate(request); });
  const revision = hook.result.current.collection.revision;
  fireEvent.focus(window);
  await waitFor(() => expect(hook.result.current.collection.status).toBe("ready"));
  await act(async () => { expect(await hook.result.current.recommendation.apply()).toBe(true); });
  expect(hook.result.current.collection.revision).toBe(revision);
  expect(applied).toHaveBeenCalledOnce();
});

it("Apply joins an identical background refresh and waits for its authoritative result", async () => {
  const hook = mount();
  await waitFor(() => expect(hook.result.current.collection.status).toBe("ready"));
  await act(async () => { await hook.result.current.recommendation.calculate(request); });
  let finish!: (data: CollectionExclusions) => void;
  vi.mocked(api).mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
  fireEvent.focus(window);
  expect(hook.result.current.collection.status).toBe("loading");
  let applying!: Promise<boolean>;
  act(() => { applying = hook.result.current.recommendation.apply(); });
  expect(applied).not.toHaveBeenCalled();
  expect(vi.mocked(api).mock.calls.filter(([path]) => path === "/collection")).toHaveLength(2);
  await act(async () => { finish(empty); expect(await applying).toBe(true); });
  expect(applied).toHaveBeenCalledOnce();
});

it.each(["racers", "machines", "gadgets"] as const)("rejects real %s changes returned by Apply before a React render", async category => {
  const hook = mount();
  await waitFor(() => expect(hook.result.current.collection.status).toBe("ready"));
  await act(async () => { await hook.result.current.recommendation.calculate(request); });
  // Even an unrelated item changes the population and can change the optimum.
  vi.mocked(api).mockResolvedValueOnce({ ...empty, [category]: ["unrelated"] });
  await act(async () => { expect(await hook.result.current.recommendation.apply()).toBe(false); });
  expect(hook.result.current.recommendation.error).toContain("Collection settings changed");
  expect(applied).not.toHaveBeenCalled();
});

it("rejects an already stale proposal after a real focus change", async () => {
  const hook = mount();
  await waitFor(() => expect(hook.result.current.collection.status).toBe("ready"));
  await act(async () => { await hook.result.current.recommendation.calculate(request); });
  vi.mocked(api).mockResolvedValueOnce({ ...empty, racers: ["r"] });
  fireEvent.focus(window);
  await waitFor(() => expect(hook.result.current.collection.revision).toBe(1));
  await act(async () => { expect(await hook.result.current.recommendation.apply()).toBe(false); });
  expect(applied).not.toHaveBeenCalled();
});

it("a failed background refresh blocks Apply without advancing ownership revision", async () => {
  const hook = mount();
  await waitFor(() => expect(hook.result.current.collection.status).toBe("ready"));
  await act(async () => { await hook.result.current.recommendation.calculate(request); });
  let fail!: (error: Error) => void;
  vi.mocked(api).mockImplementationOnce(() => new Promise((_, reject) => { fail = reject; }));
  fireEvent.focus(window);
  let applying!: Promise<boolean>;
  act(() => { applying = hook.result.current.recommendation.apply(); });
  await act(async () => { fail(new Error("Offline")); expect(await applying).toBe(false); });
  expect(hook.result.current.collection.revision).toBe(0);
  expect(hook.result.current.collection.status).toBe("error");
  await act(async () => { expect(await hook.result.current.recommendation.apply()).toBe(false); });
  expect(applied).not.toHaveBeenCalled();
});

it("session changes still invalidate a proposal even when both collections are identical", async () => {
  const hook = mount();
  await waitFor(() => expect(hook.result.current.collection.status).toBe("ready"));
  await act(async () => { await hook.result.current.recommendation.calculate(request); });
  auth.user = { id: "b" }; setToken("test-b"); hook.rerender();
  await waitFor(() => expect(hook.result.current.collection.status).toBe("ready"));
  await act(async () => { expect(await hook.result.current.recommendation.apply()).toBe(false); });
  expect(hook.result.current.recommendation.error).toContain("earlier editor session");
  expect(applied).not.toHaveBeenCalled();
});

it("pending ownership mutation blocks Apply even before refreshed ownership is known", async () => {
  const hook = mount();
  await waitFor(() => expect(hook.result.current.collection.status).toBe("ready"));
  await act(async () => { await hook.result.current.recommendation.calculate(request); });
  let finish!: () => void;
  vi.mocked(api).mockImplementationOnce(() => new Promise(resolve => { finish = () => resolve(undefined); }));
  let updating!: Promise<void>;
  act(() => { updating = hook.result.current.collection.update("RACER", "r", false); });
  await act(async () => { expect(await hook.result.current.recommendation.apply()).toBe(false); });
  expect(applied).not.toHaveBeenCalled();
  await act(async () => { finish(); await updating; });
});
