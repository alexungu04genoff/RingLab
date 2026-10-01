package dev.ringlab.port.in;

import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.gamedata.GadgetEffectRule;
import dev.ringlab.domain.gamedata.PassiveStatsResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PassiveStatsUseCase {
  record GadgetRules(UUID gadgetId, List<GadgetEffectRule> effects) {
    public GadgetRules { effects = List.copyOf(effects); }
  }
  record Rules(String ruleset, String supportedVersion, String note, List<GadgetRules> gadgets) {
    public Rules { gadgets = List.copyOf(gadgets); }
  }
  PassiveStatsResult draft(UUID version, UUID racer, UUID front, UUID rear, UUID tire, List<UUID> gadgets);
  Map<UUID, PassiveStatsResult> buildPage(List<Build> builds);
  Rules rules();
}
