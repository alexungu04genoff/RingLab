import { StrictMode } from "react";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, useLocation } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { api, setToken } from "./api";
import { SavedBuildsProvider, SaveBuildButton } from "./SavedBuilds";

vi.mock("./api", async original => ({ ...await original<typeof import("./api")>(), api: vi.fn() }));
const auth = vi.hoisted(() => ({ user: { id: "a" } as { id: string } | null }));
vi.mock("./auth", () => ({ useAuth: () => auth }));
beforeEach(() => { vi.resetAllMocks(); auth.user = { id: "a" }; setToken("a"); });
afterEach(() => { cleanup(); setToken(null); });
const build = { id: "one", title: "First setup" };
function Location() { const location = useLocation(); return <output>{location.pathname}:{location.state?.from}</output>; }
function buttons(count = 2) {
  return <MemoryRouter><SavedBuildsProvider>{Array.from({ length: count }, (_, i) =>
    <SaveBuildButton key={i} build={build} />)}<Location /></SavedBuildsProvider></MemoryRouter>;
}
const pressed = (value: string) => screen.getAllByRole("button").every(button => button.getAttribute("aria-pressed") === value);

it("deduplicates visible IDs, synchronizes confirmed changes, and blocks repeated clicks", async () => {
  let complete!: () => void;
  vi.mocked(api).mockImplementation(async (path, options) => {
    if (path.includes("/status?")) return { savedIds: [] };
    if (options?.method === "PUT") return new Promise<void>(resolve => { complete = resolve; });
    return undefined;
  });
  render(<StrictMode>{buttons()}</StrictMode>);
  await waitFor(() => expect(pressed("false")).toBe(true));
  expect(vi.mocked(api).mock.calls).toHaveLength(1);
  expect(new URLSearchParams(vi.mocked(api).mock.calls[0][0].split("?")[1]).getAll("buildId")).toEqual(["one"]);
  fireEvent.click(screen.getAllByRole("button")[0]);
  fireEvent.click(screen.getAllByRole("button")[1]);
  expect(screen.getAllByRole("button").every(button => button.hasAttribute("disabled"))).toBe(true);
  expect(vi.mocked(api).mock.calls).toHaveLength(2);
  await act(async () => complete());
  expect(pressed("true")).toBe(true);
  expect(screen.getAllByRole("button", { name: "Remove First setup from saved builds" })).toHaveLength(2);
  fireEvent.click(screen.getAllByRole("button")[1]);
  await waitFor(() => expect(pressed("false")).toBe(true));
  expect(vi.mocked(api).mock.calls.at(-1)?.[1]?.method).toBe("DELETE");
});

it("keeps unknown status unknown on failure, retries, and retains saved state on mutation failure", async () => {
  vi.mocked(api).mockRejectedValueOnce(new Error("offline")).mockResolvedValueOnce({ savedIds: ["one"] })
    .mockRejectedValueOnce(new Error("offline")).mockResolvedValueOnce(undefined);
  render(buttons(1));
  const retry = await screen.findByRole("button", { name: "Retry saved status for First setup" });
  expect(retry.hasAttribute("aria-pressed")).toBe(false);
  fireEvent.click(retry);
  const remove = await screen.findByRole("button", { name: "Remove First setup from saved builds" });
  fireEvent.click(remove);
  await screen.findByText("Could not update saved build. Try again.");
  expect(pressed("true")).toBe(true);
  fireEvent.click(remove);
  await waitFor(() => expect(pressed("false")).toBe(true));
});

it("sends signed-out visitors to login with a build return path and makes no private requests", async () => {
  auth.user = null; setToken(null);
  render(buttons(1));
  fireEvent.click(screen.getByRole("link", { name: "Save First setup" }));
  await screen.findByText("/login:/builds/one");
  expect(api).not.toHaveBeenCalled();
});

it.each([["status", false], ["save", false], ["status", true], ["save", true]] as const)("ignores an old account's delayed %s response (failure=%s)", async (kind, failure) => {
  let finish!: (value: unknown) => void;
  let oldSignal: AbortSignal | null | undefined;
  vi.mocked(api).mockImplementation(async (path, options) => {
    if (auth.user?.id === "b") return { savedIds: [] };
    if (kind === "save" && path.includes("/status?")) return { savedIds: [] };
    oldSignal = options?.signal;
    return new Promise((resolve, reject) => { finish = failure ? () => reject(new Error("Old session failed")) : resolve; });
  });
  const view = render(buttons(1));
  if (kind === "save") {
    await waitFor(() => expect(pressed("false")).toBe(true));
    fireEvent.click(screen.getByRole("button"));
  } else await waitFor(() => expect(api).toHaveBeenCalled());
  auth.user = { id: "b" }; setToken("b"); view.rerender(buttons(1));
  await waitFor(() => expect(pressed("false")).toBe(true));
  expect(oldSignal?.aborted).toBe(true);
  await act(async () => finish({ savedIds: ["one"] }));
  expect(pressed("false")).toBe(true);
  expect(screen.queryByRole("alert")).toBeNull();
});

it("splits more than fifty distinct visible IDs into bounded batches", async () => {
  vi.mocked(api).mockResolvedValue({ savedIds: [] });
  render(<MemoryRouter><SavedBuildsProvider>{Array.from({ length: 53 }, (_, i) =>
    <SaveBuildButton key={i} build={{ id: String(i), title: String(i) }} />)}</SavedBuildsProvider></MemoryRouter>);
  await waitFor(() => expect(api).toHaveBeenCalledTimes(2));
  expect(vi.mocked(api).mock.calls.map(([path]) => new URLSearchParams(path.split("?")[1]).getAll("buildId").length)).toEqual([50, 3]);
});
