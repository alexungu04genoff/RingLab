import { beforeEach, describe, expect, it, vi } from "vitest";

const storage = new Map<string, string>();
vi.stubGlobal("sessionStorage", {
  getItem: (key: string) => storage.get(key) ?? null,
  setItem: (key: string, value: string) => storage.set(key, value),
  removeItem: (key: string) => storage.delete(key),
});
vi.stubGlobal("window", { dispatchEvent: vi.fn() });
const { api, ApiError, json, setToken } = await import("./api");

describe("HTTP client contract", () => {
  beforeEach(() => {
    setToken(null);
    vi.clearAllMocks();
  });
  it("sends the JWT and JSON body and handles empty deletion responses", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);
    setToken("test-token");
    expect(await api("/builds/example", json("DELETE"))).toBeUndefined();
    expect(fetchMock.mock.calls[0][0]).toBe("/api/builds/example");
    expect(fetchMock.mock.calls[0][1].headers.get("Authorization")).toBe(
      "Bearer test-token",
    );
  });
  it("clears expired credentials and notifies authentication state", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(new Response(null, { status: 401 })),
    );
    setToken("expired");
    await expect(api("/auth/me")).rejects.toBeInstanceOf(ApiError);
    expect(storage.has("ringlab-token")).toBe(false);
    expect(window.dispatchEvent).toHaveBeenCalledOnce();
  });
  it("surfaces backend validation messages without losing their status", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            JSON.stringify({
              violations: [{ message: "Title must not be blank" }],
            }),
            { status: 400 },
          ),
        ),
    );
    await expect(api("/builds", json("POST", {}))).rejects.toMatchObject({
      status: 400,
      message: "Title must not be blank",
    });
  });
  it("retains Retry-After and authentication on throttling", async () => {
    setToken("valid-token");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ message: "Too many requests" }),
      { status: 429, headers: { "Retry-After": "6" } },
    )));
    await expect(api("/auth/me")).rejects.toMatchObject({
      status: 429, retryAfterSeconds: 6,
      message: "Too many requests Try again in 6 seconds.",
    });
    expect(storage.get("ringlab-token")).toBe("valid-token");
    expect(window.dispatchEvent).not.toHaveBeenCalled();
  });
  it.each(["invalid", "-1"])("ignores an unusable Retry-After value: %s", async (value) => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(
      JSON.stringify({ message: "Too many requests" }), { status: 429, headers: { "Retry-After": value } },
    )));
    await expect(api("/builds")).rejects.toMatchObject({ retryAfterSeconds: undefined, message: "Too many requests" });
  });
});
