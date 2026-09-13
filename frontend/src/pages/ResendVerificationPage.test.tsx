import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { api, ApiError } from "../api";
import { AuthPage } from "./AuthPage";
import { ResendVerificationPage } from "./ResendVerificationPage";

const { accept } = vi.hoisted(() => ({ accept: vi.fn() }));
vi.mock("../auth", () => ({ useAuth: () => ({ accept }) }));
vi.mock("../GoogleSignIn", () => ({ GoogleSignIn: () => null }));
vi.mock("../api", async (original) => ({ ...await original<typeof import("../api")>(), api: vi.fn() }));
beforeEach(() => vi.resetAllMocks());
afterEach(cleanup);
function page(path: string) {
  return render(<MemoryRouter initialEntries={[path]}><Routes>
    <Route path="/register" element={<AuthPage register />} />
    <Route path="/login" element={<AuthPage />} />
    <Route path="/resend-verification" element={<ResendVerificationPage />} />
  </Routes></MemoryRouter>);
}

it("keeps resend reachable after registration delivery failure", async () => {
  vi.mocked(api).mockRejectedValueOnce(new ApiError(503, "Could not send verification email"));
  page("/register");
  const user = userEvent.setup();
  await user.type(screen.getByLabelText(/^Username/), "driver");
  await user.type(screen.getByLabelText("Email"), "driver@example.test");
  await user.type(screen.getByLabelText(/^Password/), "password123");
  await user.click(screen.getByRole("button", { name: "Create account" }));
  await screen.findByRole("alert");
  await user.click(screen.getByRole("link", { name: "Resend verification email" }));
  expect(screen.getByRole("button", { name: "Send verification link" })).toBeTruthy();
  expect(accept).not.toHaveBeenCalled();
});

it.each(["/register", "/login"])("keeps recovery reachable at %s after registration state is lost", async (path) => {
  vi.mocked(api).mockResolvedValue({});
  const view = page("/register");
  const user = userEvent.setup();
  await user.type(screen.getByLabelText(/^Username/), "driver");
  await user.type(screen.getByLabelText("Email"), "driver@example.test");
  await user.type(screen.getByLabelText(/^Password/), "password123");
  await user.click(screen.getByRole("button", { name: "Create account" }));
  await screen.findByRole("heading", { name: "Verify your account" });
  view.unmount();
  page(path);
  await user.click(screen.getByRole("link", { name: "Resend verification email" }));
  expect(screen.getByLabelText("Email")).toBeTruthy();
});

it.each(["unknown", "verified", "unverified"])("shows the same generic success for an %s account", async (kind) => {
  vi.mocked(api).mockResolvedValue({ message: "Check your email to verify your account." });
  page("/resend-verification");
  const user = userEvent.setup();
  await user.type(screen.getByLabelText("Email"), `${kind}@example.test`);
  await user.click(screen.getByRole("button", { name: "Send verification link" }));
  expect((await screen.findByRole("status")).textContent).toBe("If this address belongs to an account awaiting verification, a new link has been sent. Check your inbox and spam folder.");
  expect(api).toHaveBeenCalledWith("/auth/resend-verification", { method: "POST", body: JSON.stringify({ email: `${kind}@example.test` }) });
  expect(accept).not.toHaveBeenCalled();
});
