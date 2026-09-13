import { StrictMode } from "react";
import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes, useNavigate, useLocation } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { api } from "../api";
import { VerifyEmailPage } from "./VerifyEmailPage";
import { ResendVerificationPage } from "./ResendVerificationPage";

vi.mock("../api", async (original) => ({ ...await original<typeof import("../api")>(), api: vi.fn() }));
beforeEach(() => vi.resetAllMocks());
afterEach(cleanup);

function page(url: string, strict = false) {
  let navigate!: ReturnType<typeof useNavigate>;
  let search = "";
  function Harness() {
    navigate = useNavigate();
    search = useLocation().search;
    return <Routes>
      <Route path="/verify-email" element={<VerifyEmailPage />} />
      <Route path="/resend-verification" element={<ResendVerificationPage />} />
    </Routes>;
  }
  const content = <MemoryRouter initialEntries={[url]}><Harness /></MemoryRouter>;
  render(strict ? <StrictMode>{content}</StrictMode> : content);
  return { navigate: (path: string) => navigate(path), get search() { return search; } };
}

it.each([false, true])("verifies once and removes only the token (StrictMode=%s)", async (strict) => {
  vi.mocked(api).mockResolvedValue({});
  const router = page("/verify-email?token=first&from=email", strict);
  await screen.findByRole("heading", { name: "Email verified" });
  await waitFor(() => expect(router.search).toBe("?from=email"));
  expect(api).toHaveBeenCalledTimes(1);
  expect(api).toHaveBeenCalledWith("/auth/verify-email", { method: "POST", body: '{"token":"first"}' });
  expect(screen.queryByText("Verification unavailable")).toBeNull();
});

it("shows an invalid/expired link with a public recovery route", async () => {
  vi.mocked(api).mockRejectedValue(new Error("Verification link is invalid or expired"));
  const router = page("/verify-email?token=expired");
  await screen.findByText("Verification link is invalid or expired");
  const link = screen.getByRole("link", { name: "Resend verification email" });
  await act(async () => { await router.navigate(link.getAttribute("href")!); });
  expect(screen.getByRole("button", { name: "Send verification link" })).toBeTruthy();
});

it("does not submit a missing token", async () => {
  page("/verify-email", true);
  await screen.findByRole("heading", { name: "Verification unavailable" });
  expect(api).not.toHaveBeenCalled();
});

it.each(["resolve", "reject"])("ignores an obsolete token response that %s after a newer success", async (outcome) => {
  let resolveOld!: (value: unknown) => void;
  let rejectOld!: (error: Error) => void;
  vi.mocked(api).mockImplementationOnce(() => new Promise((resolve, reject) => {
    resolveOld = resolve; rejectOld = reject;
  })).mockResolvedValueOnce({});
  const router = page("/verify-email?token=old", true);
  await act(async () => { await router.navigate("/verify-email?token=new"); });
  await screen.findByRole("heading", { name: "Email verified" });
  await act(async () => {
    if (outcome === "resolve") resolveOld({});
    else rejectOld(new Error("Old link expired"));
  });
  expect(api).toHaveBeenCalledTimes(2);
  expect(router.search).toBe("");
  expect(screen.getByRole("heading", { name: "Email verified" })).toBeTruthy();
});
