import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it } from "vitest";
import { StockMachineCard } from "./StockMachineCard";
import type { BaseStats, Machine, MachinePart, StatsCatalog } from "./types";

const machine: Machine = { id: "m", name: "Stock machine", racingType: "SPEED", imagePath: null };
const parts: MachinePart[] = (["TIRE", "REAR", "FRONT"] as const).map((type) => ({ id: type, type,
  sourceMachineId: "m", sourceMachineName: machine.name, sourceMachineImagePath: null, racingType: "SPEED" }));
const values: BaseStats = { speed: 0, acceleration: 2.75, handling: null, power: 1, boost: 3 };
const catalog: StatsCatalog = { gameVersionId: "patch", racers: { racer: { ...values, speed: 100 } },
  machineParts: Object.fromEntries(parts.map(({ id }) => [id, values])), machines: { m: { ...values, speed: 17.5 } } };
const props = { machine, parts, catalog, version: "1.4.1", loading: false, error: "" };
const panelRegion = () => screen.queryByRole("region", { name: "Stock machine part stats" });
afterEach(cleanup);

it("opens by keyboard and tap, survives pointer leave, and unmounts hidden controls", async () => {
  const { container } = render(<StockMachineCard {...props} />);
  const trigger = screen.getByRole("button", { name: "View part stats" });
  expect(trigger.getAttribute("aria-expanded")).toBe("false");
  expect(panelRegion()).toBeNull();
  trigger.focus(); await userEvent.keyboard("{Enter}");
  expect(trigger.getAttribute("aria-expanded")).toBe("true");
  expect(panelRegion()!.id).toBe(trigger.getAttribute("aria-controls"));
  fireEvent.mouseLeave(trigger); expect(panelRegion()).toBeTruthy();
  const close = screen.getByRole("button", { name: "Close part stats" });
  close.focus(); await userEvent.keyboard("{Escape}");
  expect(document.activeElement).toBe(trigger);
  expect(panelRegion()).toBeNull();
  expect(container.querySelectorAll("button")).toHaveLength(1);
  await userEvent.click(trigger);
  await userEvent.click(screen.getByRole("button", { name: "Close part stats" }));
  expect(document.activeElement).toBe(trigger);
  trigger.focus(); await userEvent.keyboard(" "); expect(panelRegion()).toBeTruthy();
  await userEvent.click(trigger); expect(panelRegion()).toBeNull();
});

it("does not steal focus from another control when the panel closes", () => {
  render(<><button>Elsewhere</button><StockMachineCard {...props} /></>);
  fireEvent.click(screen.getByRole("button", { name: "View part stats" }));
  const outside = screen.getByRole("button", { name: "Elsewhere" }); outside.focus();
  fireEvent.keyDown(outside, { key: "Escape" });
  expect(panelRegion()).toBeTruthy();
  fireEvent.click(screen.getByRole("button", { name: "Close part stats" }));
  expect(document.activeElement).toBe(outside);
});

it("orders slots explicitly, shows partial values and uses the catalog machine total without the racer", () => {
  render(<StockMachineCard {...props} />);
  fireEvent.click(screen.getByRole("button", { name: "View part stats" }));
  const panel = panelRegion()!;
  expect(within(panel).getAllByRole("heading", { level: 3 }).map((heading) => heading.textContent))
    .toEqual(["Stock machine", "Front", "Rear", "Tire", "Machine contribution"]);
  expect(within(panel).getByText("Ver. 1.4.1")).toBeTruthy();
  expect(within(panel).getByText("Stock machine parts only. Racer not included.")).toBeTruthy();
  expect(screen.getByRole("progressbar", { name: "Speed: 17.5" })).toBeTruthy();
  expect(within(panel).queryByText("100")).toBeNull();
  expect(within(panel).getAllByText("—")).toHaveLength(4);
  expect(within(panel).getAllByText("0")).toHaveLength(3);
});

it("shows only front and rear for Boost / Extreme Gear even if a stale catalog includes a tire", () => {
  render(<StockMachineCard {...props} machine={{ ...machine, racingType: "BOOST" }} />);
  fireEvent.click(screen.getByRole("button", { name: "View part stats" }));
  expect(screen.getByLabelText("Front part stats")).toBeTruthy();
  expect(screen.getByLabelText("Rear part stats")).toBeTruthy();
  expect(screen.queryByLabelText("Tire part stats")).toBeNull();
});

it("distinguishes pending data, network failure and unavailable stats without fabricating a patch", () => {
  const { rerender } = render(<StockMachineCard {...props} loading />);
  fireEvent.click(screen.getByRole("button", { name: "View part stats" }));
  expect(screen.getByRole("status").textContent).toBe("Loading part stats…");
  rerender(<StockMachineCard {...props} error="Network unavailable" />);
  expect(screen.getByRole("alert").textContent).toContain("Couldn’t load part stats");
  expect(screen.queryByLabelText("Front part stats")).toBeNull();
  rerender(<StockMachineCard {...props} catalog={undefined} />);
  expect(screen.getByText("Part stats unavailable for Ver. 1.4.1.")).toBeTruthy();
  expect(screen.getAllByText("—")).toHaveLength(20);
  rerender(<StockMachineCard {...props} catalog={undefined} parts={[]} version={null} />);
  expect(screen.getByText("Patch unavailable")).toBeTruthy();
  expect(screen.getByText("Part stats unavailable.")).toBeTruthy();
});
