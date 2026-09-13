package dev.ringlab.port.out;

import dev.ringlab.domain.auth.VerifiedExternalIdentity;

public interface ExternalIdentityVerifier {
  VerifiedExternalIdentity verify(String credential);
}
