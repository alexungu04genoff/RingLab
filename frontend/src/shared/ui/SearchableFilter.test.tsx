import { useState } from "react";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import { SearchableFilter } from "./SearchableFilter";

const options = [
  { value: "amy", label: "Amy Rose" },
  { value: "sonic", label: "Sonic the Hedgehog" },
  { value: "classic", label: "Classic Sonic" },
];

afterEach(cleanup);

function openFilter(initial = "") {
  const changed = vi.fn();
  function Filter() {
    const [value, setValue] = useState(initial);
    return <SearchableFilter label="Racer" icon={null} options={options} value={value}
      allLabel="All racers" onChange={(next) => { changed(next); setValue(next); }} />;
  }
  render(<><Filter /><button type="button">Outside filter</button></>);
  return { changed, user: userEvent.setup(), input: screen.getByRole("combobox") };
}

it("filters labels and selects the highlighted match using the keyboard", async () => {
  const { changed, user, input } = openFilter();
  await user.clear(input);
  await user.type(input, "SONIC");
  expect(screen.queryByRole("option", { name: "Amy Rose" })).toBeNull();
  expect(screen.getAllByRole("option")).toHaveLength(2);
  await user.keyboard("{ArrowDown}{Enter}");

  expect(changed).toHaveBeenCalledTimes(1);
  expect(changed).toHaveBeenCalledWith("classic");
  expect((input as HTMLInputElement).value).toBe("Classic Sonic");
  expect(input.getAttribute("aria-expanded")).toBe("false");
});

it.each(["Escape", "blur"])("restores the selected label on %s without changing the filter", async (exit) => {
  const { changed, user, input } = openFilter("amy");
  await user.clear(input);
  await user.type(input, "Sonic");
  if (exit === "Escape") await user.keyboard("{Escape}");
  else await user.click(screen.getByRole("button", { name: "Outside filter" }));

  expect(changed).not.toHaveBeenCalled();
  expect((input as HTMLInputElement).value).toBe("Amy Rose");
  expect(input.getAttribute("aria-expanded")).toBe("false");
});

it("clears a selection through the All racers option", async () => {
  const { changed, user, input } = openFilter("amy");
  await user.click(input);
  await user.click(screen.getByRole("option", { name: "All racers" }));

  expect(changed).toHaveBeenCalledTimes(1);
  expect(changed).toHaveBeenCalledWith("");
  expect((input as HTMLInputElement).value).toBe("All racers");
});

it("does not invent a selection when the search has no matches", async () => {
  const { changed, user, input } = openFilter();
  await user.clear(input);
  await user.type(input, "missing racer");
  await user.keyboard("{ArrowDown}{Enter}");

  expect(screen.getByText("No matches")).toBeTruthy();
  expect(changed).not.toHaveBeenCalled();
  expect(input.hasAttribute("aria-activedescendant")).toBe(false);
});
