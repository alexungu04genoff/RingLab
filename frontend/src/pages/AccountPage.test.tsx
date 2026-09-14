import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { api } from "../api";
import { AccountPage } from "./AccountPage";

vi.mock("../auth", () => ({
  useAuth: () => ({
    user: { id: "user", username: "alex", email: "alex@example.test", createdAt: "2026-01-01" },
  }),
}));
vi.mock("../api", async (original) => ({
  ...await original<typeof import("../api")>(),
  api: vi.fn(),
}));

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

it("links Google through the authenticated API and confirms success", async () => {
  vi.mocked(api).mockResolvedValue({ message: "Google sign-in is now linked to your account." });
  render(<MemoryRouter><AccountPage /></MemoryRouter>);

  expect(screen.getByText("alex@example.test")).toBeTruthy();
  await userEvent.click(await screen.findByRole("button", { name: "Continue with Google" }));

  expect((await screen.findByRole("status")).textContent).toContain("Google sign-in is now linked");
  expect(api).toHaveBeenCalledWith(
    "/auth/google/link",
    { method: "POST", body: JSON.stringify({ credential: "google-credential" }) },
  );
});
