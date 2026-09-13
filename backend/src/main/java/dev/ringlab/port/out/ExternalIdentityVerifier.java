package dev.ringlab.port.out;

import dev.ringlab.domain.auth.VerifiedExternalIdentity;

public interface ExternalIdentityVerifier {
  /** Verifies authenticity, intended audience and validity before returning any identity facts. */
  VerifiedExternalIdentity verify(String credential);
}
