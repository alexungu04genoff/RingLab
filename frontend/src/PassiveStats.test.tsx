import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import { DraftStats } from "./BaseStats";
import { CardStats } from "./CardStats";
import { GadgetRuleDetails, PassiveStatsPanel } from "./PassiveStats";
import { passiveStatsPath, persistedStatsPath } from "./stats";
import { shareStats } from "./buildSharing";
import type { Build, BuildStatsResult, PassiveStatsResult } from "./types";

const base: BuildStatsResult = { speed:85.25, acceleration:30.75, handling:42.125,power:12,boost:54.5,
  character:{speed:20,acceleration:20,handling:20,power:20,boost:20},
  machine:{speed:65.25,acceleration:10.75,handling:22.125,power:-8,boost:34.5} };
const result: PassiveStatsResult = { base, adjustments:{speed:40,acceleration:-6,handling:0,power:0,boost:-2},
  adjusted:{speed:125.25,acceleration:24.75,handling:42.125,power:12,boost:52.5},
  coverage:"CALCULATED",ruleset:"reviewed-test",supportedVersion:"1.4.1",note:"Known arithmetic; race events excluded.",
  effects:[{gadgetId:"g1",gadgetName:"Speed Tuner 1",effectId:"stats",label:"Machine tuner",status:"APPLIED",
    adjustment:{speed:20,acceleration:-4,handling:0,power:0,boost:0},explanation:"Matches Speed machine.",sources:["https://example.test/source"]}] };
const draft={gameVersionId:"v",racerId:"r",frontPartId:"f",rearPartId:"b",tirePartId:"t",machineType:"SPEED" as const,gadgetIds:["g1"]};
const version={id:"v",version:"1.4.1",releasedAt:"2026-06-24"};
const part=(type:"FRONT"|"REAR"|"TIRE")=>({id:type,type,sourceMachineId:"m",sourceMachineName:"Machine",sourceMachineImagePath:null,racingType:"SPEED" as const});
const build: Build = {id:"saved",gameVersion:version,racer:{id:"r",name:"Racer",racingType:"SPEED",imagePath:null},
  frontPart:part("FRONT"),rearPart:part("REAR"),tirePart:part("TIRE"),
  gadgets:[{id:"g1",name:"Speed Tuner 1",slotCost:1,description:null,imagePath:null}],mapRecommendations:{mode:"ALL",maps:[]},
  title:"Saved setup",description:"",author:{id:"author",username:"Author"},remixedFrom:null,
  createdAt:"2026-09-27T00:00:00Z",updatedAt:"2026-09-27T00:00:00Z",score:0,upvotes:0,downvotes:0};
afterEach(()=>{cleanup();vi.unstubAllGlobals();});

it("shows the base, signed penalties and unclamped decimal result",()=>{
  render(<PassiveStatsPanel value={result} />);
  expect(screen.getByLabelText("Base speed").textContent).toBe("85.25");
  expect(screen.getByLabelText("Gadget acceleration adjustment").textContent).toBe("−6");
  expect(screen.getByLabelText("speed result").textContent).toBe("= 125.25");
  expect(screen.getByText("Applied adjustments")).toBeTruthy();
  expect(screen.queryByText("Other effects")).toBeNull();
  expect(shareStats({...base,passive:result}).join("\n")).toContain("speed 85.25 +40 = 125.25");
});

it("distinguishes partial, unsupported, conditional and non-stat effects",()=>{
  const effects=result.effects.map(effect=>({...effect,status:"CONDITIONAL" as const}));
  const {rerender}=render(<PassiveStatsPanel value={{...result,coverage:"PARTIAL",effects}} />);
  expect(screen.getByText("Conditional effects — not included")).toBeTruthy();
  expect(screen.getByText(/Known subtotal only/)).toBeTruthy();
  rerender(<PassiveStatsPanel value={{...result,coverage:"UNSUPPORTED_VERSION"}} />);
  expect(screen.getByText("Gadget rules unavailable for this patch")).toBeTruthy();
  expect(screen.getByLabelText("speed result").textContent).toBe("= —");
  expect(screen.getByLabelText("Gadget speed adjustment").textContent).toBe("Unknown");
});

it("uses page enrichment without issuing a per-card request and stops control bubbling",async()=>{
  const fetch=vi.fn();vi.stubGlobal("fetch",fetch);const outer=vi.fn();
  render(<div onClick={outer}><CardStats build={build} pageStats={{status:"ready",value:{...base,passive:result}}} /></div>);
  expect(screen.queryByRole("button",{name:"Include verified passive gadget adjustments"})).toBeNull();
  expect(screen.getByLabelText("Passive stats").textContent).toContain("125.25");
  await userEvent.click(screen.getByText(/Reviewed passive effects calculated · Details/));
  expect(screen.getByLabelText("speed result").textContent).toBe("= 125.25");
  expect(fetch).not.toHaveBeenCalled();expect(outer).not.toHaveBeenCalled();
  await userEvent.click(screen.getByText("Gadget effect details"));expect(outer).not.toHaveBeenCalled();
});

it("marks the exact stat changed by a gadget without treating charge timing as stat points",async()=>{
  const zero={speed:0,acceleration:0,handling:0,power:0,boost:0};
  const drift: PassiveStatsResult={...result,adjustments:{...zero,handling:3},adjusted:{...base,handling:45.125},effects:[
    {...result.effects[0],gadgetName:"Drift Charge Kit",adjustment:{...zero,handling:3}},
    {...result.effects[0],gadgetName:"Drift Charge Kit",effectId:"timing",status:"NON_STAT",adjustment:zero}
  ]};
  const outer=vi.fn();
  const {rerender}=render(<div onClick={outer}><CardStats build={build} stats={{...base,passive:drift}} /></div>);
  expect(screen.getByLabelText("Passive stats").textContent).toContain("45.125");
  expect(screen.getByRole("img",{name:"Handling: base 42.125; gadget +3; adjusted 45.125. Outlined extension shows the gadget adjustment."})).toBeTruthy();
  const marker = screen.getByRole("button",{name:"Handling gadget adjustment +3"});
  expect(screen.queryByRole("tooltip")).toBeNull();
  await userEvent.hover(marker);
  expect(screen.getByRole("tooltip").textContent).toContain("Drift Charge Kit");
  await userEvent.unhover(marker);
  expect(screen.queryByRole("tooltip")).toBeNull();
  await userEvent.click(marker);
  expect(screen.getByText("Drift Charge Kit")).toBeTruthy();
  expect(screen.getByText(/Base 42.125 \+3 = 45.125/)).toBeTruthy();
  expect(screen.queryByLabelText(/Speed gadget adjustment/)).toBeNull();
  expect(outer).not.toHaveBeenCalled();
  await userEvent.keyboard("{Escape}");
  expect(screen.queryByRole("tooltip")).toBeNull();
  rerender(<CardStats build={build} stats={{...base,passive:{...drift,coverage:"UNSUPPORTED_VERSION"}}} />);
  expect(screen.queryByLabelText("Handling gadget adjustment +3")).toBeNull();
  expect(screen.getByLabelText("Base stats").textContent).toContain("42.125");
  expect(screen.getByText(/Base stats · Gadget rules unavailable for this patch/)).toBeTruthy();
  rerender(<CardStats build={build} stats={{...base,passive:{...drift,effects:drift.effects.map(effect=>({...effect,status:"CONDITIONAL"}))}}} />);
  expect(screen.queryByLabelText("Handling gadget adjustment +3")).toBeNull();
});

it("shows signed penalties alongside gadget bonuses",()=>{
  render(<CardStats build={build} stats={{...base,passive:result}} />);
  expect(screen.getByLabelText("Speed gadget adjustment +40")).toBeTruthy();
  expect(screen.getByLabelText("Acceleration gadget adjustment −6")).toBeTruthy();
  expect(screen.getByRole("img",{name:/Acceleration: base 30.75; gadget −6; adjusted 24.75. Hatched reduction/})).toBeTruthy();
});

it("shows gadget adjustments automatically without mode buttons",async()=>{
  const fetch=vi.fn((url:string)=>Promise.resolve(new Response(JSON.stringify(
    url.includes("passive-build") ? {...base,passive:result} : base))));
  vi.stubGlobal("fetch",fetch);
  render(<DraftStats draft={draft} version={version} />);
  expect(screen.queryByRole("button",{name:"With gadgets"})).toBeNull();
  expect(screen.queryByRole("button",{name:"Base"})).toBeNull();
  await waitFor(()=>expect(screen.getByLabelText("speed result").textContent).toBe("= 125.25"));
  expect(fetch.mock.calls[0][0]).toContain("gadgetId=g1");
  expect(screen.getByRole("img",{name:/Speed: base 85.25; gadget \+40; result 125.25/})).toBeTruthy();
});

it("recalculates after changing gadgets and never displays an earlier response under the new draft",async()=>{
  let resolveOld!:(value:Response)=>void;
  const old=new Promise<Response>(resolve=>{resolveOld=resolve;});
  const fetch=vi.fn((url:string)=>Promise.resolve(new Response(JSON.stringify(base))));
  fetch.mockImplementation((url:string)=>url.includes("gadgetId=g1") ? old
    : Promise.resolve(new Response(JSON.stringify(url.includes("passive-build") ? {...base,passive:{...result,adjusted:{...result.adjusted,speed:88.25}}} : base))));
  vi.stubGlobal("fetch",fetch);
  const {rerender}=render(<DraftStats draft={draft} version={version} />);
  expect(screen.getByText("Loading passive gadget stats…")).toBeTruthy();
  rerender(<DraftStats draft={{...draft,gadgetIds:["g2"]}} version={version} />);
  await waitFor(()=>expect(screen.getByLabelText("speed result").textContent).toBe("= 88.25"));
  await act(async()=>resolveOld(new Response(JSON.stringify({...base,passive:result}))));
  expect(screen.getByLabelText("speed result").textContent).toBe("= 88.25");
  expect(fetch.mock.calls.some(([url])=>url.includes("gadgetId=g2"))).toBe(true);
});

it("keys stats by all loadout inputs and ruleset, independently of maps and gadget display order",()=>{
  const path=passiveStatsPath({...draft,gadgetIds:["b","a"]});
  expect(path).toBe(passiveStatsPath({...draft,gadgetIds:["a","b"]}));
  expect(path).toContain("ruleset=");
  for(const change of [{racerId:"another"},{frontPartId:"another"},{rearPartId:"another"},{tirePartId:null},{gameVersionId:"older"},{gadgetIds:["c"]}])
    expect(passiveStatsPath({...draft,...change})).not.toBe(passiveStatsPath(draft));
  expect(persistedStatsPath({...build,mapRecommendations:{mode:"SELECTED",maps:[]}})).toBe(persistedStatsPath(build));
});

it("renders collection rules from the shared API metadata",()=>{
  render(<GadgetRuleDetails id="g1" catalog={{ruleset:"test",supportedVersion:"1.4.1",note:"Known arithmetic",gadgets:[{gadgetId:"g1",effects:[{
    effectId:"stats",label:"Matching racer bonus",kind:"PASSIVE",subject:"RACER",requiredType:"BOOST",
    matching:{speed:0,acceleration:0,handling:0,power:-5,boost:7},nonMatching:{speed:0,acceleration:0,handling:0,power:0,boost:0},
    explanation:"Uses racer type",sources:["https://example.test/source"],stackingGroup:null}]}]}} />);
  expect(screen.getByText("Racer type BOOST: Power −5, Boost +7")).toBeTruthy();
  expect(screen.getByText("Other known types: No stat-point adjustment")).toBeTruthy();
});
