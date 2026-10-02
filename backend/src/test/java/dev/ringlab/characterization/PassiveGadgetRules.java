package dev.ringlab.characterization;

import dev.ringlab.domain.gamedata.*;

import static dev.ringlab.domain.gamedata.GadgetEffectRule.Kind.*;
import static dev.ringlab.domain.gamedata.GadgetEffectRule.Subject.*;
import static dev.ringlab.domain.gamedata.RacingType.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Small, version-specific reviewed table. See docs/passive-gadget-sources.md. */
public final class PassiveGadgetRules {
  public static final String VERSION = "1.4.1";
  public static final String RULESET = "crossworlds-1.4.1-passive-2026-09-28.5";
  public static final String ORIGINAL = "https://docs.google.com/spreadsheets/u/0/d/1_B2mHZUP6J6jeZfcwbM2HkLvjkwMYuACE4l6Ktt-UkI/htmlview/sheet?headers=true&gid=0";
  public static final String SEGA = "https://asia.sega.com/SonicRacingCrossWorlds/en/update/v-1-4-1.html";
  public static final BaseStats ZERO = points(0, 0, 0, 0, 0);
  private static final Map<UUID, List<GadgetEffectRule>> RULES = reviewed().stream()
      .collect(Collectors.groupingBy(GadgetEffectRule::gadgetId, Collectors.toUnmodifiableList()));

  private PassiveGadgetRules() {}
  public static UUID id(int suffix) {
    return UUID.fromString("70000000-0000-4000-8000-" + String.format("%012d", suffix));
  }
  public static List<GadgetEffectRule> forGadget(UUID id) { return RULES.getOrDefault(id, List.of()); }
  /** Pairwise evidence only: never implies that other kits or effects share a stacking group. */
  public static boolean verifiedStack(GadgetEffectRule first, GadgetEffectRule second) {
    if (first.gadgetId().equals(second.gadgetId())) return false;
    if (first.stackingGroup() != null && first.stackingGroup().equals(second.stackingGroup())) return true;
    if (!first.effectId().equals("stats") || !second.effectId().equals("stats")) return false;
    // Executed reference calculator with its actual gadget_041/gadget_080 records in both orders.
    // See docs/passive-gadget-sources.md; recovery remains a separate, excluded effect.
    return first.gadgetId().equals(id(55)) && second.gadgetId().equals(id(34))
        || first.gadgetId().equals(id(34)) && second.gadgetId().equals(id(55));
  }
  public static BaseStats points(int speed, int acceleration, int handling, int power, int boost) {
    return new BaseStats(BigDecimal.valueOf(speed), BigDecimal.valueOf(acceleration),
        BigDecimal.valueOf(handling), BigDecimal.valueOf(power), BigDecimal.valueOf(boost));
  }

  private static List<GadgetEffectRule> reviewed() {
    var rules = new ArrayList<GadgetEffectRule>();
    tuner(rules, 52, SPEED, points(20,-4,0,0,0), points(8,0,0,0,0));
    tuner(rules, 53, SPEED, points(20,-2,0,0,-2), points(8,0,0,0,0));
    tuner(rules, 54, ACCELERATION, points(0,20,-4,0,0), points(0,8,0,0,0));
    tuner(rules, 55, ACCELERATION, points(0,20,-2,0,-2), points(0,8,0,0,0));
    tuner(rules, 56, HANDLING, points(0,0,20,-4,0), points(0,0,8,0,0));
    tuner(rules, 57, HANDLING, points(-2,0,20,-2,0), points(0,0,8,0,0));
    tuner(rules, 58, POWER, points(0,-4,0,20,0), points(0,0,0,8,0));
    tuner(rules, 59, POWER, points(0,-2,-2,20,0), points(0,0,0,8,0));
    tuner(rules, 60, BOOST, points(-4,0,0,0,20), points(0,0,0,0,8));
    tuner(rules, 61, BOOST, points(-2,0,0,-2,20), points(0,0,0,0,8));

    machineKit(rules,49,SPEED,points(20,0,0,0,0),"Boosts from collected rings; no collection event is assumed.");
    extra(rules,id(49),NON_STAT,"Ring collection range","Collection radius is separate from stat points.","37");
    machineKit(rules,34,ACCELERATION,points(0,20,0,0,0),"Recovery duration after damage; no conversion to stat points.");
    machineKit(rules,37,HANDLING,points(0,0,20,0,0),"Level 4 drift charge and invincibility; requires a drift event.");
    machineKit(rules,38,POWER,points(0,0,0,20,0),"Boost on collision; no collision is assumed.");
    machineKit(rules,62,BOOST,points(0,0,0,0,20),"Rings from drift boosts; no drift event is assumed.");

    characterKit(rules,20,SPEED,points(7,-5,0,0,0),"Invincibility at the race start; no race phase is assumed.");
    extra(rules,id(20),CONDITIONAL,"Starting Boost rings","Requires a successful Starting Boost.","56");
    characterKit(rules,16,ACCELERATION,points(0,7,0,0,-5),"Ring rewards require an active slipstream.");
    extra(rules,id(16),CONDITIONAL,"Ring-collection boost","Requires collecting rings; no collection boost is assumed.","56");
    characterKit(rules,13,HANDLING,points(-5,0,7,0,0),"Ring theft on collision. Exact bundled thief variant remains disputed.");
    characterKit(rules,12,POWER,points(0,0,-5,7,0),"Collision boosts require colliding with another machine.");
    extra(rules,id(12),NON_STAT,"Slipstream occurrence","Slipstream behavior is separate from Power points.","56");
    characterKit(rules,11,BOOST,points(0,0,0,-5,7),"Ring rewards require air tricks; no trick event is assumed.");
    extra(rules,id(11),NON_STAT,"Air-trick timing","Air-trick duration is separate from Boost points.","56");

    passive(rules,id(50),ANY,null,points(0,0,3,0,0),ZERO,"Handling bonus","49",null);
    extra(rules,id(50),NON_STAT,"Drift charge timing","Faster Level 1/2 charge is duration, not extra Handling points.","49");
    passive(rules,id(23),ANY,null,points(0,0,8,8,0),ZERO,"Handling and Power bonuses","88",null);
    extra(rules,id(23),CONDITIONAL,"Panel ring combo","Ring rewards require consecutive panels; no panel event is assumed.","88");
    var doubleDown = UUID.fromString("f6f75d95-16dd-538d-89b5-49c71cc8a346");
    passive(rules,doubleDown,ANY,null,points(-10,-10,-10,-10,-10),ZERO,"Five-stat penalty",null,null);
    extra(rules,doubleDown,NON_STAT,"Double items","Item quantity does not change the five stat-point values.",null);

    for (int n : new int[] {45,46,47,48})
      extra(rules,id(n),CONDITIONAL,"Lap-dependent stats","Only active on the gadget's specified lap. No current lap is assumed.","45");
    for (int n : new int[] {5,6,7,8})
      extra(rules,id(n),CONDITIONAL,"Evolution stats","Requires rings, collisions, damage or item hits. No events are assumed.","45");
    for (int n : new int[] {15,17})
      extra(rules,id(n),CONDITIONAL,"Transformation stats","Requires water or flight form. Recommended maps do not establish a current form.",null);
    extra(rules,id(15),NON_STAT,"Air-trick timing","Air-trick duration is separate from five-stat points.",null);
    extra(rules,id(17),CONDITIONAL,"Panel and gate rings","Requires crossing a panel or gate.",null);
    for (String id : List.of("5a000a58-7d7a-581c-80e3-6ae8661215b7","182bdfa8-44d3-5de8-941b-d525381dd0a3"))
      extra(rules,UUID.fromString(id),CONDITIONAL,"Ring-dependent engine","Stat and physical-speed effects require held rings; no ring count is assumed.",null);
    extra(rules,UUID.fromString("174ea0a3-43bb-5001-bbd4-8b598482fe59"),NON_STAT,"Item order","Changes held-item order, not five-stat values.",null);
    rules.add(new GadgetEffectRule(id(22), "drift-spinner", "Drift charge timing and knockback", NON_STAT,
        ANY, null, ZERO, ZERO,
        "Faster Level 1 drift charging and knockback while drifting are race-time effects, not five-stat point adjustments.",
        List.of("https://sonic.fandom.com/wiki/Gadget_(Sonic_Racing%3A_CrossWorlds)"), null));

    // Reviewed utility effects contribute zero passive points. A race-time speed boost,
    // charge rate or ring reward is not an always-active Speed/Boost stat adjustment.
    extra(rules,id(1),CONDITIONAL,"Air-trick invincibility","Requires air tricks; no trick or landing event is assumed.",null);
    extra(rules,id(2),CONDITIONAL,"Landing boost","Requires accelerating on landing; no landing boost is assumed.",null);
    extra(rules,id(3),CONDITIONAL,"Homing-item protection","Consumes held items on a homing hit; no hit is assumed.",null);
    extra(rules,id(4),CONDITIONAL,"Finish-line invincibility","Requires approaching the finish line; no race phase is assumed.",null);
    extra(rules,id(9),NON_STAT,"Item summoning","Exchanges rings for an item, not passive stat points.",null);
    extra(rules,id(10),NON_STAT,"Omochao items","Changes item availability and ring distribution, not passive stat points.",null);
    extra(rules,id(14),NON_STAT,"Comeback items and rings","Ring capacity and lap-dependent items are separate from stat points.",null);
    extra(rules,id(18),CONDITIONAL,"Terrain and transformation effects","Requires particular terrain or water/flight form; no race state is assumed.",null);
    extra(rules,id(19),NON_STAT,"Wisp inventory","Changes item capacity and availability, not passive stat points.",null);
    extra(rules,id(21),NON_STAT,"Attack items and rewards","Changes item availability and ring rewards, not passive stat points.",null);
    extra(rules,id(27),CONDITIONAL,"Starting Boost rings","Requires a successful Starting Boost; no start event is assumed.",null);
    extra(rules,id(28),NON_STAT,"Ring capacity","Ring capacity does not change the five passive stat values.",null);
    extra(rules,id(29),CONDITIONAL,"Collision boost","Requires a collision; no collision boost is assumed.",null);
    extra(rules,id(30),NON_STAT,"Slipstream occurrence","Slipstream behavior is separate from passive stat points.",null);
    extra(rules,id(31),CONDITIONAL,"Ring theft","Requires a collision; no stolen rings are assumed.",null);
    extra(rules,id(32),CONDITIONAL,"Mini ring theft","Requires a collision; no stolen rings are assumed.",null);
    extra(rules,id(33),NON_STAT,"Recovery duration","Recovery timing is separate from passive stat points.",null);
    extra(rules,id(35),NON_STAT,"Wisp item chance","Item probabilities do not change passive stat points.",null);
    extra(rules,id(36),CONDITIONAL,"Starting invincibility","Requires the race start; no race phase is assumed.",null);
    extra(rules,id(39),NON_STAT,"Drift knockback","Drift collision behavior is separate from passive stat points.",null);
    extra(rules,id(40),NON_STAT,"Ring loss protection","Changes rings lost to damage, not passive stat points.",null);
    extra(rules,id(41),NON_STAT,"Warp Ring items","Changes starting items and item availability, not passive stat points.",null);
    extra(rules,id(42),NON_STAT,"Item retention","Keeping held items does not change passive stat points.",null);
    extra(rules,id(43),CONDITIONAL,"Timed drift boost","Requires releasing a charged drift at the right time; no boost is assumed.",null);
    extra(rules,id(44),NON_STAT,"Drift friction and charge timing","Drift slowdown and charge timing are separate from five-stat points.",null);
    extra(rules,id(51),NON_STAT,"Spin Dash controls and timing","Changes drift controls, slowdown and boost duration, not passive stat points.",null);
    extra(rules,UUID.fromString("82005d0f-575b-5350-934f-fb108d306a21"),NON_STAT,"Attack item chance","Item probabilities do not change passive stat points.",null);
    extra(rules,UUID.fromString("966f8792-a6af-576f-885c-697a33064497"),NON_STAT,"Boost item chance","Item probabilities do not change passive stat points.",null);
    extra(rules,UUID.fromString("83284989-3174-5cf8-a5ef-3d443b559b55"),NON_STAT,"Defense item chance","Item probabilities do not change passive stat points.",null);
    extra(rules,UUID.fromString("ae3d98e8-321c-5e19-b442-a2da09463467"),NON_STAT,"Hazard item chance","Item probabilities do not change passive stat points.",null);
    extra(rules,UUID.fromString("b3002fe1-32a2-5a06-8c07-64d0511811ad"),CONDITIONAL,"Leading-position rings","Requires a leading race position; no ring reward is assumed.",null);
    extra(rules,UUID.fromString("97147799-cf1d-5f50-b133-3ff6091574d3"),CONDITIONAL,"Damage invincibility","Requires taking damage; no hit is assumed.",null);
    extra(rules,UUID.fromString("c857021b-d746-5d73-b54d-ccd594064c80"),NON_STAT,"Rocket Punch items","Changes item size and probability, not passive stat points.",null);
    extra(rules,UUID.fromString("f3b3dd31-9912-5a84-a8db-a76d1f93dcbe"),CONDITIONAL,"Damage item reward","Requires taking damage; no item reward is assumed.",null);
    extra(rules,UUID.fromString("238359e7-ba2a-582d-bc82-6ab1c14b0284"),NON_STAT,"Item capacity","Item capacity does not change passive stat points.",null);
    extra(rules,UUID.fromString("180a09fc-0034-5f56-be95-2938ce9ed61d"),CONDITIONAL,"Low-ring top speed","Depends on held rings; physical top speed is not passive Speed points.",null);
    extra(rules,UUID.fromString("84c606c3-0540-5a3e-bb92-27185533a89b"),NON_STAT,"Double-item chance","Item quantity does not change passive stat points.",null);
    extra(rules,UUID.fromString("3130026a-75cc-50c9-94ba-4748263acbfd"),NON_STAT,"Ring rewards","Ring quantity is separate from passive stat points.",null);
    extra(rules,UUID.fromString("b5f42975-23b3-5a1d-9173-c18d2e584244"),CONDITIONAL,"Damage ring reward","Requires taking damage; no ring reward is assumed.",null);
    extra(rules,UUID.fromString("175c2797-0be5-571a-99ac-af4bfdbf58a4"),CONDITIONAL,"Travel Ring rewards","Requires choosing a Travel Ring; no ring reward or capacity change is assumed.",null);
    extra(rules,UUID.fromString("9735d3ab-d53b-52a1-a80d-ed0cb89429c2"),CONDITIONAL,"Final-lap invincibility","Requires the final lap; no race phase is assumed.",null);
    extra(rules,UUID.fromString("f8a1e5bd-b342-5a88-87e1-908adb2e165f"),NON_STAT,"Extra drift charge level","Drift charge levels and invincibility are separate from passive stat points.",null);
    return List.copyOf(rules);
  }

  private static void tuner(List<GadgetEffectRule> rules, int id, RacingType type, BaseStats yes, BaseStats no) {
    passive(rules,id(id),MACHINE,type,yes,no,"Machine tuner","45","machine-tuners");
  }
  private static void machineKit(List<GadgetEffectRule> rules, int id, RacingType type, BaseStats yes, String other) {
    passive(rules,id(id),MACHINE,type,yes,ZERO,"Matching machine bonus","37",null);
    extra(rules,id(id),CONDITIONAL,"Kit capabilities",other,"37");
  }
  private static void characterKit(List<GadgetEffectRule> rules, int id, RacingType type, BaseStats yes, String other) {
    passive(rules,id(id),RACER,type,yes,ZERO,"Matching racer bonus and penalty","56",null);
    extra(rules,id(id),CONDITIONAL,"Kit capabilities",other,"56");
  }
  private static void passive(List<GadgetEffectRule> rules, UUID id, GadgetEffectRule.Subject subject,
      RacingType type, BaseStats yes, BaseStats no, String label, String wiki, String stacking) {
    rules.add(new GadgetEffectRule(id,"stats",label,PASSIVE,subject,type,yes,no,
        subject == ANY ? "Always active stat-point adjustment."
            : "Checks " + subject.name().toLowerCase() + " type " + type + "; uses the separately documented non-matching values otherwise.",
        sources(wiki),stacking));
  }
  private static void extra(List<GadgetEffectRule> rules, UUID id, GadgetEffectRule.Kind kind,
      String label, String explanation, String wiki) {
    rules.add(new GadgetEffectRule(id,"other-" + rules.stream().filter(r -> r.gadgetId().equals(id)).count(),
        label,kind,ANY,null,ZERO,ZERO,explanation,sources(wiki),null));
  }
  private static List<String> sources(String wiki) {
    return wiki == null ? List.of(ORIGINAL,SEGA)
        : List.of(ORIGINAL,SEGA,"https://w.atwiki.jp/sonicracingcw/pages/" + wiki + ".html");
  }
}
