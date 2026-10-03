import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import { DraftStats } from "./BaseStats";
import { CardStats } from "./CardStats";
import { BuildGadgetIcon } from "../builds/BuildCard";
import { GadgetAdjustmentBadges, GadgetCatalogAdjustmentBadges, GadgetCatalogTypeLabels, GadgetRuleDetails, PassiveStatsPanel } from "./PassiveStats";
import { passiveStatsPath, persistedStatsPath } from "./stats";
import { shareStats } from "../builds/buildSharing";
import type { Build, BuildStatsResult, PassiveStatsResult } from "../../shared/types";

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
  expect(screen.getByText("Base stats shown — this gadget combination is not fully calculated.")).toBeTruthy();
  rerender(<PassiveStatsPanel value={{...result,coverage:"UNSUPPORTED_VERSION"}} />);
  expect(screen.getByText("Gadget rules unavailable for this patch")).toBeTruthy();
  expect(screen.getByLabelText("speed result").textContent).toBe("= —");
  expect(screen.getByLabelText("Gadget speed adjustment").textContent).toBe("Unknown");
});

it("makes unresolved stacking visible instead of presenting the catalog bonus as applied",()=>{
  const unresolved = {...result,coverage:"PARTIAL" as const,adjustments:{speed:0,acceleration:0,handling:0,power:0,boost:0},
    adjusted:base,effects:[{...result.effects[0],gadgetName:"Acceleration Machine Kit",status:"UNSUPPORTED" as const,
      adjustment:{speed:0,acceleration:0,handling:0,power:0,boost:0},
      explanation:"Individual modifier is verified, but stacking is unresolved."}]};
  render(<><GadgetAdjustmentBadges gadgetId="g1" value={unresolved} /><PassiveStatsPanel value={unresolved} /></>);
  expect(screen.getByLabelText("Gadget adjustment not included").textContent).toContain("effect unsupported");
  expect(screen.getByText("Base stats shown — this gadget combination is not fully calculated.")).toBeTruthy();
  expect(screen.getByLabelText("Gadget acceleration adjustment").textContent).toBe("Not calculated");
  expect(screen.getByText(/Individual modifier is verified/).closest("details")).toBeTruthy();
});

it("shows an unreviewed festival kit as unsupported even without a stats effect",()=>{
  const unknown = {...result, coverage: "PARTIAL" as const, effects: [{...result.effects[0],
    gadgetName: "Air Trick Action Kit", effectId: "other-0", status: "UNSUPPORTED" as const,
    adjustment: {speed:0,acceleration:0,handling:0,power:0,boost:0},
    explanation: "Identity and cost are reviewed; full kit effects remain unsupported."}]};
  render(<GadgetAdjustmentBadges gadgetId="g1" value={unknown} />);
  expect(screen.getByText("Not added · effect unsupported").title).toContain("full kit effects remain unsupported");
  expect(screen.queryByLabelText("Applied gadget adjustments")).toBeNull();
});

it("labels a partial result with surviving adjustments as a known subtotal",()=>{
  render(<PassiveStatsPanel value={{...result,coverage:"PARTIAL",effects:[...result.effects,
    {...result.effects[0],gadgetId:"unknown",status:"UNSUPPORTED",explanation:"Unknown interaction"}]}} />);
  expect(screen.getByText("Known subtotal — this setup is not fully calculated.")).toBeTruthy();
  expect(screen.getByLabelText("Gadget speed adjustment").textContent).toBe("Known +40");
  expect(screen.getByLabelText("Gadget handling adjustment").textContent).toBe("Not calculated");
});

it("keeps reviewed values visible when a selected gadget is not active for the current setup",()=>{
  const catalog={ruleset:"test",supportedVersion:"1.4.1",note:"Known arithmetic",gadgets:[{gadgetId:"g1",effects:[{
    effectId:"stats",label:"Matching racer bonus",kind:"PASSIVE" as const,subject:"RACER" as const,requiredType:"ACCELERATION" as const,
    matching:{speed:0,acceleration:7,handling:0,power:0,boost:-5},nonMatching:{speed:0,acceleration:0,handling:0,power:0,boost:0},
    explanation:"Uses racer type",sources:[],stackingGroup:null}]}]};
  const inactive={...result,adjustments:{speed:0,acceleration:0,handling:0,power:0,boost:0},adjusted:base,
    effects:[{...result.effects[0],status:"NOT_MATCHED" as const,adjustment:{speed:0,acceleration:0,handling:0,power:0,boost:0}}]};
  const selection = { racerType: "HANDLING" as const, machineType: "ACCELERATION" as const };
  const {rerender} = render(<><GadgetCatalogTypeLabels gadgetId="g1" catalog={catalog} selection={selection} />
    <GadgetAdjustmentBadges gadgetId="g1" value={inactive} catalog={catalog} selection={selection} /></>);
  expect(screen.getByLabelText("Reviewed gadget stat adjustments").textContent).toContain("Acceleration +7");
  expect(screen.getByLabelText("Reviewed gadget stat adjustments").textContent).toContain("Boost −5");
  expect(screen.getByText("Stat adjustment inactive · type does not match")).toBeTruthy();
  expect(screen.getByTitle("Not met: requires Acceleration racer").classList.contains("gadget-condition-unmet")).toBe(true);
  expect(screen.getByText("Acceleration +7").closest(".gadget-condition-unmet")).toBeTruthy();
  rerender(<><GadgetCatalogTypeLabels gadgetId="g1" catalog={catalog} selection={{racerType:"ACCELERATION",machineType:"HANDLING"}} />
    <GadgetCatalogAdjustmentBadges gadgetId="g1" catalog={catalog} selection={{racerType:"ACCELERATION",machineType:"HANDLING"}} /></>);
  expect(screen.getByTitle("Met: requires Acceleration racer").classList.contains("gadget-condition-unmet")).toBe(false);
  expect(screen.getByText("Acceleration +7").closest(".gadget-condition-unmet")).toBeNull();
  const machineCatalog = {...catalog,gadgets:[{...catalog.gadgets[0],effects:[{...catalog.gadgets[0].effects[0],subject:"MACHINE" as const}]}]};
  rerender(<GadgetCatalogTypeLabels gadgetId="g1" catalog={machineCatalog} selection={{racerType:"ACCELERATION",machineType:"HANDLING"}} />);
  expect(screen.getByTitle("Not met: requires Acceleration machine")).toBeTruthy();
  rerender(<GadgetCatalogTypeLabels gadgetId="g1" catalog={machineCatalog} selection={selection} />);
  expect(screen.getByTitle("Met: requires Acceleration machine")).toBeTruthy();
  rerender(<GadgetCatalogTypeLabels gadgetId="g1" catalog={machineCatalog} />);
  expect(screen.getByText("Acceleration machine").classList.contains("gadget-condition-unmet")).toBe(false);
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

it("shows only the hovered gadget's applied bonuses and penalties for this setup",()=>{
  const gadget = {...build.gadgets[0],name:"Handling Tuner 1",description:"Adjust Handling"};
  const adjustment = {speed:0,acceleration:0,handling:20,power:-4,boost:0};
  const passive = {...result,effects:[{...result.effects[0],gadgetName:gadget.name,adjustment},
    {...result.effects[0],gadgetId:"other",adjustment:{...adjustment,handling:8}}]};
  const {rerender}=render(<BuildGadgetIcon gadget={gadget} passive={passive} />);
  expect(screen.getByLabelText("Handling Tuner 1: Handling +20, Power −4")).toBeTruthy();
  expect(screen.getByText("Handling +20")).toBeTruthy();
  expect(screen.getByText("Power −4")).toBeTruthy();
  expect(screen.queryByText("Handling +28")).toBeNull();
  rerender(<BuildGadgetIcon gadget={gadget} passive={{...passive,coverage:"UNSUPPORTED_VERSION"}} />);
  expect(screen.queryByLabelText("Applied gadget adjustments")).toBeNull();
  expect(screen.getByText("Gadget rules unavailable for this patch")).toBeTruthy();
});

it("explains every applied gadget on editor bars and removes highlights for unsupported effects",async()=>{
  const effects = [...result.effects, {...result.effects[0],gadgetId:"g2",gadgetName:"Speed Tuner 2"}];
  const {container,rerender}=render(<PassiveStatsPanel value={{...result,effects}} />);
  const marker=screen.getByRole("button",{name:"Speed gadget adjustment +40"});
  expect(container.querySelector(".stat-speed .card-stat-gadget-segment.bonus")).toBeTruthy();
  expect(container.querySelector(".stat-acceleration .card-stat-gadget-segment.penalty")).toBeTruthy();
  await userEvent.hover(marker);
  expect(screen.getByRole("tooltip").textContent).toContain("Speed Tuner 1");
  expect(screen.getByRole("tooltip").textContent).toContain("Speed Tuner 2");
  await userEvent.unhover(marker);
  expect(screen.queryByRole("tooltip")).toBeNull();
  await userEvent.click(marker);
  expect(screen.getByRole("tooltip").textContent).toContain("Base 85.25 +40 = 125.25");
  await userEvent.keyboard("{Escape}");
  expect(screen.queryByRole("tooltip")).toBeNull();
  rerender(<PassiveStatsPanel value={{...result,coverage:"UNSUPPORTED_VERSION",effects}} />);
  expect(screen.queryByRole("button",{name:/gadget adjustment/})).toBeNull();
  expect(container.querySelector(".card-stat-gadget-segment")).toBeNull();
  rerender(<PassiveStatsPanel value={{...result,effects:effects.map(effect=>({...effect,status:"CONDITIONAL"}))}} />);
  expect(screen.queryByRole("button",{name:/gadget adjustment/})).toBeNull();
  expect(container.querySelector(".card-stat-gadget-segment")).toBeNull();
});

it("shows gadget adjustments automatically without mode buttons",async()=>{
  const fetch=vi.fn((url:string)=>Promise.resolve(new Response(JSON.stringify(
    url.includes("passive-build") ? {...base,passive:result} : base))));
  vi.stubGlobal("fetch",fetch);
  render(<DraftStats draft={draft} version={version} />);
  expect(screen.queryByRole("group",{name:"Statistics mode"})).toBeNull();
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

it("shows nonmatching tuner values instead of matching bonuses and penalties in previews and unresolved fallbacks",()=>{
  const catalog={ruleset:"test",supportedVersion:"1.4.1",note:"Known arithmetic",gadgets:[{gadgetId:"g1",effects:[{
    effectId:"stats",label:"Machine tuner",kind:"PASSIVE" as const,subject:"MACHINE" as const,requiredType:"ACCELERATION" as const,
    matching:{speed:0,acceleration:20,handling:-2,power:0,boost:-2},nonMatching:{speed:0,acceleration:8,handling:0,power:0,boost:0},
    explanation:"Uses machine type",sources:[],stackingGroup:"machine-tuners"}]}]};
  const selection={racerType:"POWER" as const,machineType:"SPEED" as const};
  const {rerender}=render(<GadgetCatalogAdjustmentBadges gadgetId="g1" catalog={catalog} selection={selection} />);
  expect(screen.getByLabelText("Reviewed gadget stat adjustments").textContent).toBe("Acceleration +8");
  expect(screen.getByText("Acceleration +8").closest(".gadget-condition-unmet")).toBeNull();
  const unresolved={...result,effects:[{...result.effects[0],status:"UNSUPPORTED" as const}]};
  rerender(<GadgetAdjustmentBadges gadgetId="g1" catalog={catalog} selection={selection} value={unresolved} />);
  expect(screen.getByLabelText("Reviewed gadget stat adjustments").textContent).toBe("Acceleration +8");
    expect(screen.getByText("Not added · effect unsupported")).toBeTruthy();
  expect(screen.queryByLabelText("Applied gadget adjustments")).toBeNull();
  rerender(<GadgetCatalogAdjustmentBadges gadgetId="g1" catalog={catalog} selection={{...selection,machineType:"ACCELERATION"}} />);
  expect(screen.getByText("Acceleration +20")).toBeTruthy();
  expect(screen.getByText("Handling −2")).toBeTruthy();
  expect(screen.getByText("Boost −2")).toBeTruthy();
});

it("keeps a gadget type condition separate from its stat badges",()=>{
  const catalog={ruleset:"test",supportedVersion:"1.4.1",note:"Known arithmetic",gadgets:[{gadgetId:"g1",effects:[{
    effectId:"stats",label:"Matching machine bonus",kind:"PASSIVE" as const,subject:"MACHINE" as const,requiredType:"ACCELERATION" as const,
    matching:{speed:0,acceleration:20,handling:0,power:0,boost:0},nonMatching:{speed:0,acceleration:0,handling:0,power:0,boost:0},
    explanation:"Uses machine type",sources:[],stackingGroup:null}]}]};
  render(<><span className="gadget-option-meta"><small>3 slots</small><GadgetCatalogTypeLabels gadgetId="g1" catalog={catalog} /></span>
    <GadgetCatalogAdjustmentBadges gadgetId="g1" catalog={catalog} /></>);
  const typeBadge=screen.getByLabelText("Gadget type conditions").firstElementChild!;
  expect(typeBadge.textContent).toBe("Acceleration machine");
  expect(typeBadge.classList).toContain("racing-type-acceleration");
  expect(screen.getByLabelText("Reviewed gadget stat adjustments").textContent).toBe("Acceleration +20");
});
