import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect, useState } from "react";
import { MemoryRouter, Route, Routes, useLocation } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { api, ApiError, hasToken, setToken } from "../shared/api/api";
import { ForgotPasswordPage } from "./ForgotPasswordPage";
import { ResetPasswordPage } from "./ResetPasswordPage";

vi.mock("../shared/api/api", async original => ({ ...await original<typeof import("../shared/api/api")>(), api: vi.fn() }));
beforeEach(() => { vi.clearAllMocks(); setToken(null); });
afterEach(cleanup);
const resetToken = "a".repeat(43);
function Location() { const location = useLocation(); return <div aria-label="Location">{location.pathname + location.search}</div>; }
function SessionBoundary() {
  const [generation, setGeneration] = useState(0);
  useEffect(() => {
    const expire = () => setGeneration(value => value + 1);
    window.addEventListener("ringlab-session-expired", expire);
    return () => window.removeEventListener("ringlab-session-expired", expire);
  }, []);
  return <ResetPasswordPage key={generation} />;
}
function page(reset = false, token = resetToken) {
  return render(<MemoryRouter initialEntries={[reset ? `/reset-password?token=${token}` : "/forgot-password"]}>
    <Location /><Routes>
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<SessionBoundary />} />
      <Route path="/login" element={<h1>Normal login</h1>} />
    </Routes>
  </MemoryRouter>);
}
async function fill(password = "new-password", confirmation = password) {
  const user = userEvent.setup();
  await user.type(screen.getByLabelText("New password"), password);
  await user.type(screen.getByLabelText("Confirm password"), confirmation);
  return user;
}

it.each(["known@example.test", "missing@example.test", "google@example.test"])("gives the same confirmation for %s", async email => {
  vi.mocked(api).mockResolvedValue({ message: "Server response" }); page();
  const user = userEvent.setup(); await user.type(screen.getByLabelText("Email"), email);
  await user.click(screen.getByRole("button", { name: "Send reset link" }));
  expect(screen.getByRole("status").textContent).toBe("If an eligible account exists for that email, a password reset link has been sent.");
  expect(api).toHaveBeenCalledWith("/auth/forgot-password", expect.objectContaining({ anonymous: true, body: JSON.stringify({ email }) }));
});

it("disables duplicate requests and allows retry after rate or network errors", async () => {
  let reject!: (error: Error) => void;
  vi.mocked(api).mockImplementationOnce(() => new Promise((_, no) => { reject = no; })); page();
  const user = userEvent.setup(); await user.type(screen.getByLabelText("Email"), "account@example.test");
  const form = screen.getByRole("button", { name: "Send reset link" }).closest("form")!;
  fireEvent.submit(form); fireEvent.submit(form); expect(api).toHaveBeenCalledTimes(1);
  expect((screen.getByRole("button", { name: "Please wait…" }) as HTMLButtonElement).disabled).toBe(true);
  await act(async () => reject(new ApiError(429, "Too many requests", 60)));
  expect(screen.getByRole("alert").textContent).toContain("60 seconds");
  vi.mocked(api).mockResolvedValue({}); await user.click(screen.getByRole("button", { name: "Send reset link" }));
  expect(screen.getByRole("status")).toBeTruthy();
});

it("keeps the reset credential out of storage and validates confirmation", async () => {
  page(true);
  expect(JSON.stringify(localStorage)).not.toContain(resetToken); expect(JSON.stringify(sessionStorage)).not.toContain(resetToken);
  const user = await fill("new-password", "different-password");
  await user.click(screen.getByRole("button", { name: "Reset password" }));
  expect(screen.getByRole("alert").textContent).toContain("Passwords do not match"); expect(api).not.toHaveBeenCalled();
});

it("retains the bcrypt UTF-8 byte limit before submission", async () => {
  page(true); const user = await fill("€".repeat(25));
  await user.click(screen.getByRole("button", { name: "Reset password" }));
  expect(screen.getByRole("alert").textContent).toContain("72 UTF-8 bytes"); expect(api).not.toHaveBeenCalled();
});

it.each(["", "malformed"])("rejects a missing or malformed link without a request", token => {
  page(true, token); expect(screen.getByRole("alert").textContent).toBe("This password reset link is invalid or has expired.");
  expect(screen.getByRole("link", { name: "Request a new reset link" }).getAttribute("href")).toBe("/forgot-password");
  expect(api).not.toHaveBeenCalled();
});

it("shows the same invalid-link state for expired or consumed tokens", async () => {
  vi.mocked(api).mockRejectedValue(new ApiError(400, "This password reset link is invalid or has expired."));
  page(true); const user = await fill(); await user.click(screen.getByRole("button", { name: "Reset password" }));
  expect(screen.getByRole("heading", { name: "Reset link unavailable" })).toBeTruthy();
  expect(screen.queryByLabelText("New password")).toBeNull();
});

it("keeps a transient failure retryable and succeeds without automatic login", async () => {
  setToken("existing-session");
  vi.mocked(api).mockRejectedValueOnce(new Error("Network unavailable")).mockResolvedValueOnce({});
  page(true); const user = await fill(); await user.click(screen.getByRole("button", { name: "Reset password" }));
  expect(screen.getByRole("alert").textContent).toContain("Network unavailable");
  await user.click(screen.getByRole("button", { name: "Reset password" }));
  expect((await screen.findByRole("status")).textContent).toContain("Please log in"); expect(hasToken()).toBe(false);
  await waitFor(() => expect(screen.getByLabelText("Location").textContent).toBe("/reset-password"));
  expect(api).toHaveBeenLastCalledWith("/auth/reset-password", expect.objectContaining({ anonymous: true,
    body: JSON.stringify({ token: resetToken, password: "new-password", confirmPassword: "new-password" }) }));
  await user.click(screen.getByRole("link", { name: "Log in" }));
  expect(screen.getByRole("heading", { name: "Normal login" })).toBeTruthy();
});

it("submits a pending reset only once", async () => {
  let finish!: (value: unknown) => void;
  vi.mocked(api).mockImplementation(() => new Promise(resolve => { finish = resolve; }));
  page(true); await fill();
  const form = screen.getByRole("button", { name: "Reset password" }).closest("form")!;
  fireEvent.submit(form); fireEvent.submit(form); expect(api).toHaveBeenCalledTimes(1);
  await act(async () => finish({})); await waitFor(() => expect(screen.getByRole("status")).toBeTruthy());
});
