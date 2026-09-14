package dev.ringlab.adapter.in.rest.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ringlab.adapter.in.rest.ErrorRestExceptionMapper;
import dev.ringlab.adapter.in.rest.ErrorRestExceptionMapper.ErrorResponse;
import dev.ringlab.application.AuthenticationException;
import jakarta.ws.rs.core.Response;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RateLimitTest {
  @Test
  void allowsBurstThenReturnsRetryAfterAndRefillsWithoutSleeping() {
    MutableClock clock = new MutableClock();
    InMemoryRateLimiter limiter = limiter(3, Duration.ofMinutes(1), 100, clock);

    assertTrue(limiter.tryConsume(RequestRateLimitPolicy.LOGIN, "ip:192.0.2.1").allowed());
    assertTrue(limiter.tryConsume(RequestRateLimitPolicy.LOGIN, "ip:192.0.2.1").allowed());
    assertTrue(limiter.tryConsume(RequestRateLimitPolicy.LOGIN, "ip:192.0.2.1").allowed());

    var rejected = limiter.tryConsume(RequestRateLimitPolicy.LOGIN, "ip:192.0.2.1");
    assertFalse(rejected.allowed());
    assertEquals(20, rejected.retryAfterSeconds());

    clock.advance(Duration.ofSeconds(20));
    assertTrue(limiter.tryConsume(RequestRateLimitPolicy.LOGIN, "ip:192.0.2.1").allowed());
  }

  @Test
  void separateUnauthenticatedIpsHaveIndependentBuckets() {
    InMemoryRateLimiter limiter = limiter(1, Duration.ofMinutes(1), 100, new MutableClock());

    assertTrue(limiter.tryConsume(RequestRateLimitPolicy.REGISTRATION, "ip:192.0.2.1").allowed());
    assertFalse(limiter.tryConsume(RequestRateLimitPolicy.REGISTRATION, "ip:192.0.2.1").allowed());
    assertTrue(limiter.tryConsume(RequestRateLimitPolicy.REGISTRATION, "ip:192.0.2.2").allowed());
  }

  @Test
  void authenticatedMutationUsesIndependentUserIdentities() {
    InMemoryRateLimiter limiter = limiter(1, Duration.ofMinutes(1), 100, new MutableClock());
    String first = RateLimitIdentity.resolve(
        RequestRateLimitPolicy.BUILD_CREATION, Optional.of("user-one"), "192.0.2.1");
    String second = RateLimitIdentity.resolve(
        RequestRateLimitPolicy.BUILD_CREATION, Optional.of("user-two"), "192.0.2.1");

    assertEquals("user:user-one", first);
    assertTrue(limiter.tryConsume(RequestRateLimitPolicy.BUILD_CREATION, first).allowed());
    assertFalse(limiter.tryConsume(RequestRateLimitPolicy.BUILD_CREATION, first).allowed());
    assertTrue(limiter.tryConsume(RequestRateLimitPolicy.BUILD_CREATION, second).allowed());
  }

  @Test
  void unauthenticatedMutationFallsBackToClientIp() {
    assertEquals(
        "ip:192.0.2.10",
        RateLimitIdentity.resolve(
            RequestRateLimitPolicy.COMMENT_CREATION, Optional.empty(), "192.0.2.10"));
  }

  @Test
  void classifiesAuthenticationAndMutationEndpointsBeforeGeneralTraffic() {
    assertEquals(RequestRateLimitPolicy.LOGIN, policy("POST", "/api/auth/login"));
    assertEquals(RequestRateLimitPolicy.GOOGLE_LOGIN, policy("POST", "/api/auth/google"));
    assertEquals(RequestRateLimitPolicy.REGISTRATION, policy("POST", "/api/auth/register"));
    assertEquals(RequestRateLimitPolicy.BUILD_LIST, policy("GET", "/api/builds/"));
    assertEquals(RequestRateLimitPolicy.NEWS, policy("GET", "/api/news"));
    assertEquals(RequestRateLimitPolicy.BUILD_CREATION, policy("POST", "/api/builds"));
    assertEquals(
        RequestRateLimitPolicy.COMMENT_CREATION,
        policy("POST", "/api/builds/a-build/comments"));
    assertEquals(RequestRateLimitPolicy.VOTING, policy("PUT", "/api/builds/a-build/vote"));
    assertEquals(RequestRateLimitPolicy.VOTING, policy("DELETE", "/api/builds/a-build/vote"));
    assertEquals(
        RequestRateLimitPolicy.AUTHENTICATED_MUTATION,
        policy("DELETE", "/api/comments/a-comment"));
    assertEquals(RequestRateLimitPolicy.GENERAL_READ, policy("GET", "/api/racers"));
    assertTrue(RequestRateLimitPolicy.forRequest("OPTIONS", "/api/builds").isEmpty());
  }

  @Test
  void cloudflareAddressRequiresExplicitTrustAndLoopbackPeer() {
    assertEquals(
        "127.0.0.1", ClientIpResolver.resolve("127.0.0.1", "203.0.113.8", false));
    assertEquals(
        "203.0.113.8", ClientIpResolver.resolve("127.0.0.1", "203.0.113.8", true));
    assertEquals(
        "198.51.100.2", ClientIpResolver.resolve("198.51.100.2", "203.0.113.8", true));
    assertEquals(
        "127.0.0.1", ClientIpResolver.resolve("127.0.0.1", "not-an-ip", true));
  }

  @Test
  void onlyTheExplicitDockerProxyCanSupplyVisitorIdentity() {
    assertEquals("203.0.113.8",
        ClientIpResolver.resolve("172.30.50.2", "203.0.113.8", true, "172.30.50.2"));
    assertEquals("172.30.50.3",
        ClientIpResolver.resolve("172.30.50.3", "203.0.113.8", true, "172.30.50.2"));
    assertEquals("172.30.50.2",
        ClientIpResolver.resolve("172.30.50.2", "203.0.113.8", false, "172.30.50.2"));
    assertEquals("172.30.50.2",
        ClientIpResolver.resolve("172.30.50.2", "203.0.113.8", true, "caddy"));
    assertEquals("172.30.50.2",
        ClientIpResolver.resolve("172.30.50.2", "203.0.113.8", true, "172.30.50.0/24"));
    assertEquals("unknown",
        ClientIpResolver.resolve(null, "203.0.113.8", true, ""));
    assertEquals("172.30.50.2",
        ClientIpResolver.resolve("172.30.50.2", "203.0.113.8, 198.51.100.1", true, "172.30.50.2"));
    assertEquals("2001:db8:0:0:0:0:0:8",
        ClientIpResolver.resolve("172.30.50.2", "2001:db8::8", true, "172.30.50.2"));
  }

  @Test
  void rejectionResponseUsesSafeJsonAndValidRetryAfter() {
    try (Response response = RateLimitFilter.rejectionResponse(
        InMemoryRateLimiter.Decision.rejected(12))) {
      assertEquals(429, response.getStatus());
      assertEquals("12", response.getHeaderString("Retry-After"));
      assertEquals("application/json", response.getMediaType().toString());
      assertEquals(new ErrorResponse("Too many requests"), response.getEntity());
    }
  }

  @Test
  void admittedAuthenticationFailuresKeepTheirNormalErrorResponseUntilBucketIsExhausted() {
    InMemoryRateLimiter limiter = limiter(1, Duration.ofMinutes(1), 100, new MutableClock());
    var admitted = limiter.tryConsume(RequestRateLimitPolicy.LOGIN, "ip:192.0.2.1");
    assertTrue(admitted.allowed());

    try (Response response = new ErrorRestExceptionMapper()
        .toResponse(new AuthenticationException("Invalid credentials"))) {
      assertEquals(401, response.getStatus());
      assertEquals(new ErrorResponse("Invalid credentials"), response.getEntity());
    }

    var rejected = limiter.tryConsume(RequestRateLimitPolicy.LOGIN, "ip:192.0.2.1");
    assertFalse(rejected.allowed());
    try (Response response = RateLimitFilter.rejectionResponse(rejected)) {
      assertEquals(429, response.getStatus());
    }
  }

  @Test
  void bucketStorageExpiresIdleEntriesAndNeverExceedsConfiguredMaximum() {
    MutableClock clock = new MutableClock();
    InMemoryRateLimiter limiter = limiter(1, Duration.ofMinutes(1), 3, clock);

    for (int i = 0; i < 10; i++) {
      limiter.tryConsume(RequestRateLimitPolicy.GENERAL_READ, "ip:192.0.2." + i);
    }
    assertEquals(3, limiter.bucketCount());

    clock.advance(Duration.ofMinutes(2));
    limiter.tryConsume(RequestRateLimitPolicy.GENERAL_READ, "ip:198.51.100.1");
    assertEquals(1, limiter.bucketCount());
  }

  @Test
  void rateConfigurationUsesCapacityAndIsoDuration() {
    RateLimitRule rule = RateLimitRule.parse("5/PT1H");
    assertEquals(5, rule.capacity());
    assertEquals(Duration.ofHours(1), rule.refillPeriod());
  }

  @Test
  void invalidRateConfigurationFailsAtStartupWithActionableMessages() {
    assertEquals("Rate-limit value is required",
        assertThrows(IllegalArgumentException.class, () -> RateLimitRule.parse(null)).getMessage());
    assertEquals("Rate-limit value must use the form <capacity>/<ISO-8601 duration>",
        assertThrows(IllegalArgumentException.class, () -> RateLimitRule.parse("5")).getMessage());
    assertEquals("Rate-limit capacity must be an integer",
        assertThrows(IllegalArgumentException.class, () -> RateLimitRule.parse("many/PT1M")).getMessage());
    assertEquals("Rate-limit capacity must be positive",
        assertThrows(IllegalArgumentException.class, () -> RateLimitRule.parse("0/PT1M")).getMessage());
    for (String duration : new String[] {"PT0S", "-PT1S"}) {
      assertEquals("Rate-limit refill period must be positive",
          assertThrows(IllegalArgumentException.class,
              () -> RateLimitRule.parse("1/" + duration)).getMessage());
    }
    assertEquals("Rate-limit refill period must be positive",
        assertThrows(IllegalArgumentException.class,
            () -> new RateLimitRule(1, null)).getMessage());
  }

  private static RequestRateLimitPolicy policy(String method, String path) {
    return RequestRateLimitPolicy.forRequest(method, path).orElseThrow();
  }

  private static InMemoryRateLimiter limiter(
      int capacity, Duration refillPeriod, int maxBuckets, Clock clock) {
    Map<RequestRateLimitPolicy, RateLimitRule> rules =
        new EnumMap<>(RequestRateLimitPolicy.class);
    for (RequestRateLimitPolicy policy : RequestRateLimitPolicy.values()) {
      rules.put(policy, new RateLimitRule(capacity, refillPeriod));
    }
    return new InMemoryRateLimiter(rules, Duration.ofMinutes(1), maxBuckets, clock);
  }

  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-01-01T00:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
