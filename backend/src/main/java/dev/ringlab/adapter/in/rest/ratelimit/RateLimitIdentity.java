package dev.ringlab.adapter.in.rest.ratelimit;

import java.util.Optional;

final class RateLimitIdentity {
  private RateLimitIdentity() {}

  static String resolve(
      RequestRateLimitPolicy policy, Optional<String> authenticatedUserId, String clientIp) {
    if (policy.identityScope() == RequestRateLimitPolicy.IdentityScope.AUTHENTICATED_USER
        && authenticatedUserId.isPresent()) {
      return "user:" + authenticatedUserId.get();
    }
    return "ip:" + clientIp;
  }
}
