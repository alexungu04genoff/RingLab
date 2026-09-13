package dev.ringlab.port.out;

import dev.ringlab.domain.auth.VerifiedExternalIdentity;

public interface ExternalIdentityVerifier {
  /** Returns provider identity facts only after successfully verifying the supplied credential. */
  VerifiedExternalIdentity verify(String credential);
}
