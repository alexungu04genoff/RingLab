import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes, useNavigate } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { api, setToken } from "../api";
import { AuthPage } from "./AuthPage";

const { accept } = vi.hoisted(() => ({ accept: vi.fn() }));
vi.mock("../auth", () => ({ useAuth: () => ({ accept }) }));
vi.mock("../api", async (original) => ({ ...await original<typeof import("../api")>(), api: vi.fn() }));
const session = { token: "ringlab-jwt", user: { id: "user", username: "alex", email: "alex@example.test", createdAt: "2026-01-01" } };

beforeEach(() => {
  vi.clearAllMocks();
  setToken(null);
  vi.stubEnv("VITE_GOOGLE_CLIENT_ID", "test-client");
  let receive: (response: { credential: string }) => void;
  vi.stubGlobal("google", { accounts: { id: {
    initialize: vi.fn((options) => { receive = options.callback; }),
    renderButton: vi.fn((element: HTMLElement) => {
      const button = document.createElement("button");
      button.type = "button";
      button.textContent = "Continue with Google";
      button.onclick = () => receive({ credential: "google-credential" });
      element.appendChild(button);
    }),
  } } });
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.unstubAllEnvs(); });

function page(register = false) {
  let navigate!: ReturnType<typeof useNavigate>;
  function Navigation() { navigate = useNavigate(); return null; }
  const result = render(<MemoryRouter initialEntries={[{ pathname: "/login", state: { from: "/builds/new" } }]}>
    <Navigation />
    <Routes><Route path="/login" element={<AuthPage register={register} />} />
      <Route path="/outside" element={<p>Outside login</p>} />
      <Route path="/builds/new" element={<p>Build editor destination</p>} /></Routes>
  </MemoryRouter>);
  return { ...result, navigate };
}

it("exchanges the GIS credential and accepts only the normal RingLab session", async () => {
  vi.mocked(api).mockResolvedValue(session);
  page();
  await userEvent.click(await screen.findByRole("button", { name: "Continue with Google" }));
  await screen.findByText("Build editor destination");
  expect(api).toHaveBeenCalledWith("/auth/google", expect.objectContaining({
    method: "POST", anonymous: true, body: JSON.stringify({ credential: "google-credential" }),
  }));
  expect(accept).toHaveBeenCalledWith(session);
  expect(sessionStorage.getItem("google-credential")).toBeNull();
});

it.each(["https://example.test", "//example.test", "/\\example.test", "/\n/example.test"])("rejects unsafe login return %s", async from => {
  vi.mocked(api).mockResolvedValue(session);
  render(<MemoryRouter initialEntries={[{ pathname: "/login", state: { from } }]}><Routes>
    <Route path="/login" element={<AuthPage />} /><Route path="/" element={<p>Safe home destination</p>} />
  </Routes></MemoryRouter>);
  await userEvent.click(await screen.findByRole("button", { name: "Continue with Google" }));
  await screen.findByText("Safe home destination");
  expect(vi.mocked(api).mock.calls.every(([path]) => !path.startsWith("/saved-builds"))).toBe(true);
});

it("shows an email conflict without accepting a session and allows a retry", async () => {
  vi.mocked(api).mockRejectedValueOnce(new Error("Sign in with your username and password, then link Google from your account page.")).mockResolvedValueOnce(session);
  page(true);
  const button = await screen.findByRole("button", { name: "Continue with Google" });
  await userEvent.click(button);
  await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("account page"));
  expect(accept).not.toHaveBeenCalled();
  expect(screen.getByLabelText(/^Password/)).toBeTruthy();
  await userEvent.click(button);
  await screen.findByText("Build editor destination");
});

it("keeps local login available when Google is unconfigured", async () => {
  vi.stubEnv("VITE_GOOGLE_CLIENT_ID", "");
  vi.mocked(api).mockResolvedValue(session);
  page();
  expect(screen.queryByRole("button", { name: "Continue with Google" })).toBeNull();
  const user = userEvent.setup();
  await user.type(screen.getByLabelText("Username"), "alex");
  await user.type(screen.getByLabelText("Password"), "password123");
  await user.click(screen.getByRole("button", { name: "Log in" }));
  await screen.findByText("Build editor destination");
  expect(api).toHaveBeenCalledWith("/auth/login", expect.objectContaining({
    method: "POST", anonymous: true, body: JSON.stringify({ username: "alex", password: "password123" }),
  }));
});

it.each(["Google", "password"])("ignores a delayed %s login after leaving the page", async (method) => {
  let finish!: (value: typeof session) => void;
  vi.mocked(api).mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  const router = page();
  if (method === "Google") {
    await userEvent.click(await screen.findByRole("button", { name: "Continue with Google" }));
  } else {
    fireEvent.change(screen.getByLabelText("Username"), { target: { value: "alex" } });
    fireEvent.change(screen.getByLabelText("Password"), { target: { value: "password123" } });
    fireEvent.submit(screen.getByLabelText("Username").closest("form")!);
  }
  await act(async () => { await router.navigate("/outside"); });
  await act(async () => { finish(session); });

  expect(accept).not.toHaveBeenCalled();
  expect(screen.getByText("Outside login")).toBeTruthy();
  expect(screen.queryByText("Build editor destination")).toBeNull();
});

it("does not replace a newer session with a delayed Google login", async () => {
  let finish!: (value: typeof session) => void;
  vi.mocked(api).mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  page();
  await userEvent.click(await screen.findByRole("button", { name: "Continue with Google" }));
  setToken("newer-session");
  await act(async () => { finish(session); });

  expect(accept).not.toHaveBeenCalled();
  expect(sessionStorage.getItem("ringlab-token")).toBe("newer-session");
  expect(screen.queryByText("Build editor destination")).toBeNull();
});

it("registers once and shows verification instructions without accepting a session", async () => {
  let finish!: (value: unknown) => void;
  vi.mocked(api).mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  page(true);
  fireEvent.change(screen.getByLabelText(/^Username/), { target: { value: "new_user" } });
  fireEvent.change(screen.getByLabelText("Email"), { target: { value: "new@example.test" } });
  fireEvent.change(screen.getByLabelText(/^Password/), { target: { value: "password123" } });
  const form = screen.getByLabelText("Email").closest("form")!;
  fireEvent.submit(form);
  fireEvent.submit(form);
  expect(api).toHaveBeenCalledTimes(1);
  expect(JSON.parse(vi.mocked(api).mock.calls[0][1]!.body as string)).toEqual({
    username: "new_user", email: "new@example.test", password: "password123",
  });
  await act(async () => { finish({ message: "Check your email" }); });
  expect(screen.getByText(/We sent a verification link to new@example.test/)).toBeTruthy();
  expect(accept).not.toHaveBeenCalled();
});
