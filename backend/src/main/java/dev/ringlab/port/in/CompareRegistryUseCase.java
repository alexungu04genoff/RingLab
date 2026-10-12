package dev.ringlab.port.in;

import dev.ringlab.domain.gamedata.registry.RegistryComparison;
import java.util.Optional;

public interface CompareRegistryUseCase {
  Optional<RegistryComparison> compareLatest();
}
