import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "./api";
import { useLoad } from "./useLoad";

vi.mock("./api", () => ({ api: vi.fn() }));

describe("useLoad", () => {
  beforeEach(() => vi.mocked(api).mockReset());

  it("reports successful and failed requests without retaining stale data", async () => {
    vi.mocked(api).mockResolvedValueOnce({ id: "first" });
    const { result, rerender } = renderHook(
      ({ path }) => useLoad<{ id: string }>(path),
      { initialProps: { path: "/first" } },
    );

    await waitFor(() => expect(result.current.data).toEqual({ id: "first" }));
    expect(result.current).toEqual({ data: { id: "first" }, error: "", loading: false });

    vi.mocked(api).mockRejectedValueOnce(new Error("Network unavailable"));
    rerender({ path: "/second" });
    expect(result.current.data).toBeUndefined();
    await waitFor(() => expect(result.current.error).toBe("Network unavailable"));
    expect(result.current.loading).toBe(false);
  });

  it("does not let an obsolete request update state after navigation", async () => {
    let resolveFirst: (value: { id: string }) => void = () => undefined;
    vi.mocked(api)
      .mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve; }))
      .mockResolvedValueOnce({ id: "current" });
    const { result, rerender } = renderHook(
      ({ path }) => useLoad<{ id: string }>(path),
      { initialProps: { path: "/first" } },
    );

    rerender({ path: "/current" });
    await waitFor(() => expect(result.current.data).toEqual({ id: "current" }));
    await act(async () => resolveFirst({ id: "obsolete" }));

    expect(result.current.data).toEqual({ id: "current" });
  });

  it("settles immediately when there is no path", () => {
    const { result } = renderHook(() => useLoad(""));

    expect(result.current).toEqual({ data: undefined, error: "", loading: false });
    expect(api).not.toHaveBeenCalled();
  });
});
