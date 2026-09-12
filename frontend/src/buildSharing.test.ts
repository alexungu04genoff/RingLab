import { expect, it } from "vitest";
import { formatBuildForSharing } from "./buildSharing";
import type { Build } from "./types";

const build: Build = {
  id: "internal-build-id", title: "Amy is still my first pick", description: "Do not share this description.",
  author: { id: "internal-author-id", username: "amy-fan" },
  racer: { id: "racer-id", name: "Amy Rose", racingType: "SPEED", imagePath: null },
  frontPart: { id: "front-id", type: "FRONT", sourceMachineId: "machine-id", sourceMachineName: "Speedster Lightning", sourceMachineImagePath: null, racingType: "SPEED" },
  rearPart: { id: "rear-id", type: "REAR", sourceMachineId: "machine-id", sourceMachineName: "Speedster Lightning", sourceMachineImagePath: null, racingType: "SPEED" },
  tirePart: { id: "tire-id", type: "TIRE", sourceMachineId: "machine-id", sourceMachineName: "Speedster Lightning", sourceMachineImagePath: null, racingType: "SPEED" },
  gameVersion: { id: "version-id", version: "1.4.1", releasedAt: "2026-06-23" },
  gadgets: [
    { id: "first-gadget", name: "Route Planner Bounty", description: null, slotCost: 1, imagePath: null },
    { id: "second-gadget", name: "Ring Doubler", description: null, slotCost: 3, imagePath: null },
    { id: "third-gadget", name: "Strong Finish", description: null, slotCost: 2, imagePath: null },
  ],
  createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z", score: 40, upvotes: 41, downvotes: 1,
};

it("formats a complete player-facing build setup", () => {
  const text = formatBuildForSharing(build, "https://ringlab.example/builds/build-id");
  expect(text).toContain("**Amy is still my first pick** — RingLab");
  expect(text).toContain("**Racer:** Amy Rose");
  expect(text).toContain("**Patch:** Ver. 1.4.1");
  expect(text).toContain("Front: Speedster Lightning");
  expect(text).toContain("Rear: Speedster Lightning");
  expect(text).toContain("Tires: Speedster Lightning");
  expect(text).toContain("**Gadgets — 6/6 slots**");
  expect(text.indexOf("Route Planner Bounty")).toBeLessThan(text.indexOf("Ring Doubler"));
  expect(text.indexOf("Ring Doubler")).toBeLessThan(text.indexOf("Strong Finish"));
  expect(text).toContain("• Route Planner Bounty — 1 slot");
  expect(text).toContain("• Ring Doubler — 3 slots");
  expect(text).toContain("**Community:** ↑ 41 · ↓ 1");
  expect(text).toContain("https://ringlab.example/builds/build-id");
  expect(text).not.toContain("Do not share this description.");
  expect(text).not.toContain("internal-build-id");
  expect(text).not.toContain("score: 40");
});

it("handles an unspecified patch, unknown costs, and no gadgets without inventing totals", () => {
  const incomplete = formatBuildForSharing({
    ...build,
    gameVersion: null,
    gadgets: [{ id: "unknown", name: "Mystery Gadget", description: null, slotCost: null, imagePath: null }],
  }, "https://ringlab.example/builds/build-id");
  expect(incomplete).toContain("**Patch:** Unspecified");
  expect(incomplete).toContain("**Gadgets — cost incomplete**");
  expect(incomplete).toContain("• Mystery Gadget — cost unknown");
  expect(incomplete).not.toContain("0/6");

  const empty = formatBuildForSharing({ ...build, gadgets: [] }, "https://ringlab.example/builds/build-id");
  expect(empty).toContain("**Gadgets**\nNone");
});
