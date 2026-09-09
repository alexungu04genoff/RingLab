import { describe, expect, it } from "vitest";
import { moveGadget, toggleGadget } from "./buildForm";
describe("ordered gadget selection", () => {
  it("preserves selection order when adding and removing gadgets", () => {
    expect(toggleGadget(["b", "a"], "c")).toEqual(["b", "a", "c"]);
    expect(toggleGadget(["b", "a", "c"], "a")).toEqual(["b", "c"]);
  });
  it("moves a gadget without mutating the saved draft or crossing boundaries", () => {
    const ids = ["b", "a", "c"];
    expect(moveGadget(ids, 1, -1)).toEqual(["a", "b", "c"]);
    expect(moveGadget(ids, 1, 1)).toEqual(["b", "c", "a"]);
    expect(moveGadget(ids, 0, -1)).toEqual(ids);
    expect(moveGadget(ids, 2, 1)).toEqual(ids);
    expect(ids).toEqual(["b", "a", "c"]);
  });
});
