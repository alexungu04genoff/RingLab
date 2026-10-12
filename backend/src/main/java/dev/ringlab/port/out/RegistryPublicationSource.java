package dev.ringlab.port.out;

import dev.ringlab.domain.gamedata.registry.VerifiedRegistryPublication;
import java.util.Optional;

/** Fetches and verifies a complete immutable publication, including its advertised metadata. */
public interface RegistryPublicationSource {
  Optional<VerifiedRegistryPublication> latest();
}
