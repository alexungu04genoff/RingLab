package dev.ringlab.port.out;

import dev.ringlab.domain.auth.ExternalIdentity;
import java.util.Optional;

public interface ExternalIdentityRepository {
  Optional<ExternalIdentity> find(String provider, String subject);

  void create(ExternalIdentity identity);
}
