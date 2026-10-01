import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import { InfoPopover } from "./InfoPopover";
import { GadgetRuleDetails } from "./PassiveStats";

afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it("opens Calculation notes by keyboard, stays open after pointer leave, and restores focus on dismissal", async () => {
  const user = userEvent.setup();
  render(<><button>Outside</button><InfoPopover label="Calculation notes" hint="About stat calculations"><p>Reviewed effects</p></InfoPopover></>);
  const trigger = screen.getByRole("button", { name: "Calculation notes" });
  expect(trigger.getAttribute("aria-expanded")).toBe("false");
  await user.hover(trigger);
  expect(screen.queryByRole("dialog")).toBeNull();
  trigger.focus();
  await user.keyboard("{Enter}");
  const panel = screen.getByRole("dialog", { name: "Calculation notes" });
  expect(panel.id).toBe(trigger.getAttribute("aria-controls"));
  expect(document.activeElement).toBe(panel);
  await user.unhover(trigger);
  expect(screen.getByRole("dialog")).toBeTruthy();
  await user.keyboard("{Escape}");
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(document.activeElement).toBe(trigger);
  await user.click(trigger);
  await user.click(screen.getByRole("button", { name: "Outside" }));
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(document.activeElement).toBe(trigger);
  await user.click(trigger);
  await user.click(screen.getByRole("button", { name: "Close calculation notes" }));
  expect(screen.queryByRole("dialog")).toBeNull();
  expect(document.activeElement).toBe(trigger);
  await user.click(trigger);
  await user.click(trigger);
  expect(screen.queryByRole("dialog")).toBeNull();
});

it("removes shared prose from card bodies while retaining each effect and showing notes on demand", async () => {
  const zero = { speed: 0, acceleration: 0, handling: 0, power: 0, boost: 0 };
  const catalog = { ruleset: "test", supportedVersion: "1.4.1",
    note: "Known passive stat-point arithmetic; effective in-game caps are not established. Race-time effects are excluded.",
    gadgets: [{ gadgetId: "g", effects: [{ effectId: "stats", label: "Reviewed bonus", kind: "PASSIVE" as const,
      subject: "ANY" as const, requiredType: null, matching: { ...zero, speed: 20 }, nonMatching: zero,
      explanation: "Gadget-specific explanation", sources: [], stackingGroup: null }] }],
  };
  const { container } = render(<><GadgetRuleDetails id="g" catalog={catalog} /><GadgetRuleDetails id="g" catalog={catalog} /></>);
  for (const details of container.querySelectorAll("details")) {
    details.open = true;
    expect(details.textContent).toContain("Reviewed bonus");
    expect(details.textContent).toContain("Always: Speed +20");
    expect(details.textContent).toContain("Gadget-specific explanation");
    expect(details.textContent).not.toContain(catalog.note);
    expect(details.textContent).not.toContain("Machine tuner bonuses");
    expect(details.textContent).not.toContain("Verified compatible modifiers");
  }
  await userEvent.click(screen.getAllByRole("button", { name: "Calculation notes" })[0]);
  const panel = screen.getByRole("dialog");
  expect(within(panel).getByText(/RingLab shows reviewed passive stat effects/)).toBeTruthy();
  expect(within(panel).getByText(/Acceleration Tuner 2 \+ Acceleration Machine Kit is verified/)).toBeTruthy();
});

it.each([320, 375])("keeps the popover placement inside a %ipx mobile viewport, including after resize", async width => {
  vi.spyOn(window, "innerWidth", "get").mockReturnValue(width);
  vi.spyOn(window, "innerHeight", "get").mockReturnValue(568);
  // jsdom has no layout; supply rendered sizes to exercise edge placement.
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockImplementation(function (this: HTMLElement) {
    const isPanel = this.classList.contains("info-popover");
    return { left: isPanel ? 0 : width - 60, top: isPanel ? 0 : 520,
      bottom: isPanel ? 240 : 564, width: isPanel ? width - 24 : 48, height: isPanel ? 240 : 44 } as DOMRect;
  });
  render(<InfoPopover label="Calculation notes" hint="About stat calculations"><p>Reviewed effects</p></InfoPopover>);
  await userEvent.click(screen.getByRole("button", { name: "Calculation notes" }));
  const panel = screen.getByRole("dialog");
  const check = () => {
    expect(Number.parseFloat(panel.style.left)).toBeGreaterThanOrEqual(12);
    expect(Number.parseFloat(panel.style.left) + width - 24).toBeLessThanOrEqual(width - 12);
    expect(Number.parseFloat(panel.style.top)).toBeGreaterThanOrEqual(12);
    expect(Number.parseFloat(panel.style.top) + 240).toBeLessThanOrEqual(568 - 12);
  };
  check(); fireEvent(window, new Event("resize")); check();
});
