package dev.ringlab.port.out;

import dev.ringlab.domain.gamedata.GadgetRuleSnapshot;
import java.util.Optional;

public interface GadgetRuleRepository {
  Optional<GadgetRuleSnapshot> findByVersion(String version);
}
