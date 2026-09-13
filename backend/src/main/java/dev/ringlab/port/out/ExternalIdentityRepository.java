package dev.ringlab.port.out;

import dev.ringlab.domain.auth.ExternalIdentity;
import java.util.Optional;

public interface ExternalIdentityRepository {
  /** Finds the identity uniquely identified by its provider and provider-issued subject. */
  Optional<ExternalIdentity> find(String provider, String subject);

  /** Creates an identity whose provider and subject pair must remain unique. */
  void create(ExternalIdentity identity);
}
