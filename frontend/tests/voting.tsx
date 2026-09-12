import { act } from "react";
import { createRoot } from "react-dom/client";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "../src/auth";
import { setToken } from "../src/api";
import { BuildDetails } from "../src/pages/BuildDetails";

// A real-browser interaction test using the existing Vite/React dependencies.
// Fixed server responses deliberately avoid reproducing vote arithmetic in the test.
const part = (type: string) => ({ id: type, type, sourceMachineId: "machine", sourceMachineName: "Machine" });
const build = {
  id: "build", title: "Voting fixture", description: "", author: { id: "author", username: "driver" },
  racer: { id: "racer", name: "Sonic", racingType: "SPEED", imagePath: null },
  frontPart: part("FRONT"), rearPart: part("REAR"), tirePart: part("TIRE"),
  gadgets: [], gameVersion: null, createdAt: "2026-01-01", updatedAt: "2026-01-01",
  score: -20, upvotes: 20, downvotes: 40,
};
const initial = { score: -20, upvotes: 20, downvotes: 40, myVote: 0 };
let nextVote = initial;
let failVote = false;
let buildReads = 0;
const mutations: string[] = [];
const originalFetch = window.fetch;
const originalToken = sessionStorage.getItem("ringlab-token");
const container = document.getElementById("test-root")!;
const root = createRoot(container);
const result = document.getElementById("result")!;
Object.assign(globalThis, { IS_REACT_ACT_ENVIRONMENT: true });

function check(condition: boolean, message: string) {
  if (!condition) throw new Error(message);
}

async function waitForUpdate() {
  for (let attempt = 0; attempt < 100; attempt++) {
    await act(async () => { await new Promise((resolve) => setTimeout(resolve, 10)); });
    const button = container.querySelector<HTMLButtonElement>(".vote-buttons button");
    if (button && !button.disabled) return;
  }
  throw new Error("Voting did not become ready within one second");
}

function checkSummary(expected: typeof initial) {
  check(container.querySelector(".big-score")?.textContent === String(expected.score), "Net score mismatch");
  check(container.querySelector(".vote-panel")!.textContent!
    .includes(`${expected.upvotes} upvotes · ${expected.downvotes} downvotes`), "Vote counts mismatch");
  const buttons = container.querySelectorAll(".vote-buttons button");
  check(buttons[0].getAttribute("aria-pressed") === String(expected.myVote === 1), "Upvote selection mismatch");
  check(buttons[1].getAttribute("aria-pressed") === String(expected.myVote === -1), "Downvote selection mismatch");
  check(!container.textContent!.includes("↑ -20"), "Net score shown as upvote count");
}

window.fetch = async (input, options) => {
  const path = String(input);
  if (path === "/api/auth/me") return Response.json({ id: "voter", username: "voter" });
  if (path.includes("/comments")) return Response.json({ items: [], total: 0, page: 0, size: 20 });
  if (path === "/api/builds/build/vote") {
    if (options?.method) {
      mutations.push(`${options.method}:${options.body ?? ""}`);
      if (failVote) return Response.json({ message: "Vote unavailable" }, { status: 503 });
    }
    return Response.json(nextVote);
  }
  if (path === "/api/builds/build") { buildReads++; return Response.json(build); }
  throw new Error(`Unexpected test request: ${path}`);
};

try {
  setToken("browser-test-fixture");
  await act(async () => root.render(
    <AuthProvider><MemoryRouter initialEntries={["/builds/build"]}>
      <Routes><Route path="/builds/:id" element={<BuildDetails />} /></Routes>
    </MemoryRouter></AuthProvider>,
  ));
  await waitForUpdate();
  checkSummary(initial);
  for (const [buttonIndex, summary] of [
    [0, { score: -19, upvotes: 21, downvotes: 40, myVote: 1 }],
    [1, { score: -21, upvotes: 20, downvotes: 41, myVote: -1 }],
    [1, initial],
  ] as const) {
    nextVote = summary;
    await act(async () => container.querySelectorAll<HTMLButtonElement>(".vote-buttons button")[buttonIndex].click());
    await waitForUpdate();
    checkSummary(summary);
  }
  check(JSON.stringify(mutations) === JSON.stringify(['PUT:{"value":1}', 'PUT:{"value":-1}', 'DELETE:']),
    "Incorrect add/switch/remove requests");
  failVote = true;
  await act(async () => container.querySelector<HTMLButtonElement>(".vote-buttons button")!.click());
  await waitForUpdate();
  checkSummary(initial);
  check(container.querySelector('[role="alert"]')?.textContent === "Vote unavailable", "Missing mutation error");
  check(buildReads === 1, "Voting reloaded the build");
  result.textContent = "PASS: initial summary; add; switch; remove; failed vote; no build reload.";
} catch (error) {
  result.textContent = `FAIL: ${error instanceof Error ? error.message : error}`;
} finally {
  await act(async () => root.unmount());
  setToken(originalToken);
  window.fetch = originalFetch;
}
