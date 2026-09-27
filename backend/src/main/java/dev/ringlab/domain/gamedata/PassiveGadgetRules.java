package dev.ringlab.domain.gamedata;

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
  public static final String RULESET = "crossworlds-1.4.1-passive-2026-09-27.1";
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
    extra(rules,id(16),NON_STAT,"Slipstream occurrence","Slipstream behavior is not an Acceleration-point adjustment.","56");
    characterKit(rules,13,HANDLING,points(-5,0,7,0,0),"Ring theft on collision. Exact bundled thief variant remains disputed.");
    characterKit(rules,12,POWER,points(0,0,-5,7,0),"Collision boosts require colliding with another machine.");
    extra(rules,id(12),NON_STAT,"Slipstream occurrence","Slipstream behavior is separate from Power points.","56");
    characterKit(rules,11,BOOST,points(0,0,0,-5,7),"Invincibility requires the specified Air Trick sequence.");
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
      extra(rules,UUID.fromString(id),CONDITIONAL,"Ring-dependent engine","Requires held rings. Physical top-speed changes in km/h are not Speed points.",null);
    extra(rules,UUID.fromString("174ea0a3-43bb-5001-bbd4-8b598482fe59"),NON_STAT,"Item order","Changes held-item order, not five-stat values.",null);
    return List.copyOf(rules);
  }

  private static void tuner(List<GadgetEffectRule> rules, int id, RacingType type, BaseStats yes, BaseStats no) {
    passive(rules,id(id),MACHINE,type,yes,no,"Machine tuner","45","tuner-" + type);
  }
  private static void machineKit(List<GadgetEffectRule> rules, int id, RacingType type, BaseStats yes, String other) {
    passive(rules,id(id),MACHINE,type,yes,ZERO,"Matching machine bonus","37",null);
    extra(rules,id(id),CONDITIONAL,"Kit capabilities",other,"37");
  }
  private static void characterKit(List<GadgetEffectRule> rules, int id, RacingType type, BaseStats yes, String other) {
    passive(rules,id(id),RACER,type,yes,ZERO,"Matching racer bonus and penalty","56",null);
    extra(rules,id(id),id == 13 ? UNSUPPORTED : CONDITIONAL,"Kit capabilities",other,"56");
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
