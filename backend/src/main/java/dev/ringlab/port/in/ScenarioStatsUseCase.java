package dev.ringlab.port.in;

import dev.ringlab.domain.gamedata.ScenarioContext;
import dev.ringlab.domain.gamedata.ScenarioEffectRule;
import dev.ringlab.domain.gamedata.ScenarioStatsResult;
import java.util.List;
import java.util.UUID;

public interface ScenarioStatsUseCase {
  record Rules(String supportedVersion, List<ScenarioEffectRule> effects) {
    public Rules { effects = List.copyOf(effects); }
  }
  ScenarioStatsResult preview(UUID version, UUID racer, UUID front, UUID rear, UUID tire,
                             List<UUID> gadgets, ScenarioContext context);
  Rules rules();
}
