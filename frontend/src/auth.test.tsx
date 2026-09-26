import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { hasToken, setToken } from "./api";
import { AuthProvider, RequireAuth, useAuth } from "./auth";

const currentUser = { id: "owner", username: "alex", email: "alex@example.test", createdAt: "2026-01-01" };
function PrivatePage() { return <p>Signed in as {useAuth().user?.username}</p>; }
function page() {
  render(<MemoryRouter initialEntries={["/private"]}><AuthProvider><Routes>
    <Route path="/private" element={<RequireAuth><PrivatePage /></RequireAuth>} />
    <Route path="/login" element={<p>Login destination</p>} />
  </Routes></AuthProvider></MemoryRouter>);
}
beforeEach(() => { setToken("valid-token"); });
afterEach(() => { cleanup(); setToken(null); vi.unstubAllGlobals(); });

it.each([200, 401])("ignores an old bootstrap response (%s) after accepting another session", async (status) => {
  let finish!: (response: Response) => void;
  vi.stubGlobal("fetch", vi.fn(() => new Promise<Response>(resolve => { finish = resolve; })));
  function Controls() {
    const auth = useAuth();
    return <><button onClick={() => auth.accept({ token: "new-session", user: { ...currentUser, username: "new-user" } })}>Accept session</button>
      <p>{auth.user?.username}</p></>;
  }
  render(<AuthProvider><Controls /></AuthProvider>);
  await userEvent.click(screen.getByRole("button", { name: "Accept session" }));
  await act(async () => finish(new Response(status === 200 ? JSON.stringify(currentUser) : null, { status })));
  expect(screen.getByText("new-user")).toBeTruthy();
  expect(sessionStorage.getItem("ringlab-token")).toBe("new-session");
});

it("clears a rejected session and redirects only for 401", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 401 })));
  page();
  await screen.findByText("Login destination");
  expect(hasToken()).toBe(false);
  expect(sessionStorage.getItem("ringlab-token")).toBeNull();
  expect(screen.queryByRole("button", { name: "Retry session check" })).toBeNull();
});

it.each(["network", 429, 503, 500])("retains credentials after %s and recovers on retry", async (failure) => {
  const fetchMock = vi.fn();
  if (failure === "network") fetchMock.mockRejectedValueOnce(new TypeError("Network unavailable"));
  else fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ message: "Temporarily unavailable" }),
    { status: failure as number, headers: failure === 429 ? { "Retry-After": "6" } : {} }));
  fetchMock.mockResolvedValueOnce(new Response(JSON.stringify(currentUser)));
  vi.stubGlobal("fetch", fetchMock);
  page();
  const retry = await screen.findByRole("button", { name: "Retry session check" });
  expect(hasToken()).toBe(true);
  expect(sessionStorage.getItem("ringlab-token")).toBe("valid-token");
  expect(screen.queryByText("Login destination")).toBeNull();
  if (failure === 429) expect(screen.getByRole("alert").textContent).toContain("6 seconds");
  await userEvent.click(retry);
  await screen.findByText("Signed in as alex");
  await waitFor(() => expect(screen.queryByRole("alert")).toBeNull());
  expect(fetchMock.mock.calls[1][1].headers.get("Authorization")).toBe("Bearer valid-token");
  expect(hasToken()).toBe(true);
});
