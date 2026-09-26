import { act, cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import { MemoryRouter } from "react-router-dom";
import { RacingTypeBadge } from "./RacingTypeBadge";
import { RacerIcon, SteeringWheelIcon } from "./icons";
import { SnapshotTime, snapshotAge } from "./SnapshotTime";
import { BuildCard } from "./components";
import type { Build } from "./types";

afterEach(() => { cleanup(); vi.useRealTimers(); });

it("reuses the racer and steering-wheel icons with full names, visible types and no static tab stops", () => {
  const { container } = render(<><RacingTypeBadge kind="racer" type="SPEED" />
    <RacingTypeBadge kind="machine" type="POWER" /><RacingTypeBadge kind="machine" type={null} /></>);
  expect(screen.getByRole("img", { name: "Racer type: Speed" }).textContent).toBe("SPEED");
  expect(screen.getByRole("img", { name: "Machine type: Power" }).textContent).toBe("POWER");
  expect(screen.getByRole("img", { name: "Machine type: Unknown" }).textContent).toBe("Unknown");
  expect(container.querySelectorAll("[tabindex], button")).toHaveLength(0);
  const icons = container.querySelectorAll("svg");
  expect(icons[0].innerHTML).toBe(new DOMParser().parseFromString(renderToStaticMarkup(<RacerIcon />), "text/html").querySelector("svg")!.innerHTML);
  expect(icons[1].innerHTML).toBe(new DOMParser().parseFromString(renderToStaticMarkup(<SteeringWheelIcon />), "text/html").querySelector("svg")!.innerHTML);
  for (const icon of icons) expect(icon.getAttribute("aria-hidden")).toBe("true");
});

it("explains base stats by disclosure activation without changing partial values or guessing demo provenance", async () => {
  const part = { id: "p", sourceMachineId: "m", sourceMachineName: "Machine", sourceMachineImagePath: null, racingType: "SPEED" as const };
  const build: Build = { id: "b", title: "My demo build", description: "", author: { id: "user", username: "ringlab_demo_person" },
    racer: { id: "r", name: "Racer", racingType: null, imagePath: null }, frontPart: { ...part, type: "FRONT" },
    rearPart: { ...part, type: "REAR" }, tirePart: { ...part, type: "TIRE" }, gameVersion: null, remixedFrom: null,
    gadgets: [], score: 0, upvotes: 0, downvotes: 0, createdAt: "2026-01-01", updatedAt: "2026-01-01" };
  const values = { speed: 0, acceleration: 2.75, handling: null, power: 5, boost: null };
  const { container } = render(<MemoryRouter><BuildCard build={build} stats={{ ...values, character: values, machine: values }} /></MemoryRouter>);
  expect(screen.getByRole("heading", { name: "Base stats" })).toBeTruthy();
  const summary = screen.getByText("About base stats");
  await userEvent.click(summary);
  expect(summary.closest("details")!.open).toBe(true);
  expect(screen.getByText("Racer + machine parts. Gadget effects not included.")).toBeTruthy();
  expect(screen.getByText("2.75")).toBeTruthy();
  expect(screen.getByText("0")).toBeTruthy();
  expect(container.querySelectorAll(".card-stat-track.unknown")).toHaveLength(2);
  expect(screen.queryByText("Demo build")).toBeNull(); expect(screen.queryByText("Demo account")).toBeNull();
  await userEvent.click(summary); expect(summary.closest("details")!.open).toBe(false);
});

it.each([
  [0, "Updated just now"], [59_999, "Updated just now"], [60_000, "Updated 1 minute ago"],
  [180_000, "Updated 3 minutes ago"], [3_600_000, "Updated 1 hour ago"],
  [86_400_000, "Updated 1 day ago"], [172_800_000, "Updated 2 days ago"],
  [-30_000, "Updated just now"], [-61_000, "Update time ahead of this device’s clock"],
])("formats snapshot age at %i ms", (age, expected) => {
  expect(snapshotAge("2026-09-26T12:00:00Z", Date.parse("2026-09-26T12:00:00Z") + age)).toBe(expected);
});

it("ages the real snapshot, retains the exact time on demand, and cleans up its single timer", () => {
  vi.useFakeTimers(); vi.setSystemTime(new Date("2026-09-26T12:03:00Z"));
  const timestamp = "2026-09-26T12:00:00Z";
  const { container, rerender, unmount } = render(<SnapshotTime timestamp={timestamp} />);
  expect(screen.getByText("Updated 3 minutes ago")).toBeTruthy();
  expect(container.querySelector("time")!.dateTime).toBe(timestamp);
  expect(screen.getByText(`Snapshot taken ${new Date(timestamp).toLocaleString()}`)).toBeTruthy();
  act(() => { vi.advanceTimersByTime(60_000); });
  rerender(<SnapshotTime timestamp={timestamp} />); // Same cached snapshot, fresh render.
  expect(screen.getByText("Updated 4 minutes ago")).toBeTruthy();
  expect(vi.getTimerCount()).toBe(1);
  rerender(<SnapshotTime timestamp="invalid" />);
  expect(screen.getByText("Update time unavailable")).toBeTruthy();
  expect(container.querySelector("time")).toBeNull();
  unmount(); expect(vi.getTimerCount()).toBe(0);
  expect(snapshotAge("invalid", Date.now())).toBe("Update time unavailable");
});
