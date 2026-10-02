package dev.ringlab.domain.gamedata;

import static dev.ringlab.domain.gamedata.PassiveGadgetRules.id;
import java.util.UUID;

/** Safety/interpretation policy; CSV facts cannot introduce a new stacking permission. */
public final class ScenarioGadgetRules {
  public static final String VERSION = "1.4.1";
  public static final UUID RING_ENGINE = UUID.fromString("5a000a58-7d7a-581c-80e3-6ae8661215b7");
  public static final UUID HYPER_RING_ENGINE = UUID.fromString("182bdfa8-44d3-5de8-941b-d525381dd0a3");
  private ScenarioGadgetRules() {}

  /** User-approved modeling assumption, not verified game behavior. Only this exact effect pair. */
  public static boolean assumedAdditivePair(UUID first, String firstEffect, UUID second, String secondEffect) {
    if (!"other-0".equals(firstEffect) || !"other-0".equals(secondEffect)) return false;
    return (first.equals(id(45)) && second.equals(id(15)))
        || (first.equals(id(15)) && second.equals(id(45)));
  }
}
