// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { api } from "../api";
import { AuthPage } from "./AuthPage";

const { accept } = vi.hoisted(() => ({ accept: vi.fn() }));
vi.mock("../auth", () => ({ useAuth: () => ({ accept }) }));
vi.mock("../api", async (original) => ({ ...await original<typeof import("../api")>(), api: vi.fn() }));
const session = { token: "ringlab-jwt", user: { id: "user", username: "alex", email: "alex@example.test", createdAt: "2026-01-01" } };

beforeEach(() => {
  vi.clearAllMocks();
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
  return render(<MemoryRouter initialEntries={[{ pathname: "/login", state: { from: "/builds/new" } }]}>
    <Routes><Route path="/login" element={<AuthPage register={register} />} />
      <Route path="/builds/new" element={<p>Build editor destination</p>} /></Routes>
  </MemoryRouter>);
}

it("exchanges the GIS credential and accepts only the normal RingLab session", async () => {
  vi.mocked(api).mockResolvedValue(session);
  page();
  await userEvent.click(await screen.findByRole("button", { name: "Continue with Google" }));
  await screen.findByText("Build editor destination");
  expect(api).toHaveBeenCalledWith("/auth/google", { method: "POST", body: JSON.stringify({ credential: "google-credential" }) });
  expect(accept).toHaveBeenCalledWith(session);
  expect(sessionStorage.getItem("google-credential")).toBeNull();
});

it("shows an email conflict without accepting a session and allows a retry", async () => {
  vi.mocked(api).mockRejectedValueOnce(new Error("Sign in with your existing account first.")).mockResolvedValueOnce(session);
  page(true);
  const button = await screen.findByRole("button", { name: "Continue with Google" });
  await userEvent.click(button);
  await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("existing account"));
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
  expect(api).toHaveBeenCalledWith("/auth/login", { method: "POST", body: JSON.stringify({ username: "alex", password: "password123" }) });
});
