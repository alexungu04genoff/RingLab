package dev.ringlab.adapter.in.rest.ratelimit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
class InMemoryRateLimiter {
  private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(1);

  private final Map<RequestRateLimitPolicy, RateLimitRule> rules;
  private final Duration idleTimeout;
  private final int maxBuckets;
  private final Clock clock;
  private final LinkedHashMap<BucketKey, Bucket> buckets = new LinkedHashMap<>(16, 0.75f, true);
  private Instant nextCleanup;

  @Inject
  InMemoryRateLimiter(RateLimitSettings settings) {
    this(
        settings.rules(),
        settings.bucketIdleTimeout(),
        settings.maxBuckets(),
        Clock.systemUTC());
  }

  InMemoryRateLimiter(
      Map<RequestRateLimitPolicy, RateLimitRule> rules,
      Duration idleTimeout,
      int maxBuckets,
      Clock clock) {
    if (idleTimeout == null || idleTimeout.isZero() || idleTimeout.isNegative()) {
      throw new IllegalArgumentException("Bucket idle timeout must be positive");
    }
    if (maxBuckets <= 0) {
      throw new IllegalArgumentException("Maximum bucket count must be positive");
    }
    this.rules = Map.copyOf(rules);
    this.idleTimeout = idleTimeout;
    this.maxBuckets = maxBuckets;
    this.clock = clock;
    this.nextCleanup = clock.instant().plus(CLEANUP_INTERVAL);
  }

  synchronized Decision tryConsume(RequestRateLimitPolicy policy, String identity) {
    Instant now = clock.instant();
    removeExpiredBucketsIfDue(now);

    BucketKey key = new BucketKey(policy, identity);
    Bucket bucket = buckets.get(key);
    if (bucket == null) {
      evictOldestBucketAtCapacity();
      RateLimitRule rule = ruleFor(policy);
      bucket = new Bucket(rule.capacity(), now);
      buckets.put(key, bucket);
    }

    RateLimitRule rule = ruleFor(policy);
    refill(bucket, rule, now);
    bucket.lastSeen = now;
    if (bucket.availableTokens >= 1) {
      bucket.availableTokens -= 1;
      return Decision.permitted();
    }

    double missingTokens = 1 - bucket.availableTokens;
    double nanosPerToken = (double) rule.refillPeriod().toNanos() / rule.capacity();
    long retryAfterSeconds = Math.max(1, (long) Math.ceil(missingTokens * nanosPerToken / 1_000_000_000d));
    return Decision.rejected(retryAfterSeconds);
  }

  synchronized int bucketCount() {
    return buckets.size();
  }

  private void refill(Bucket bucket, RateLimitRule rule, Instant now) {
    Duration elapsed = Duration.between(bucket.lastRefill, now);
    if (elapsed.isNegative() || elapsed.isZero()) {
      return;
    }
    double refill = (double) elapsed.toNanos() * rule.capacity() / rule.refillPeriod().toNanos();
    bucket.availableTokens = Math.min(rule.capacity(), bucket.availableTokens + refill);
    bucket.lastRefill = now;
  }

  private void removeExpiredBucketsIfDue(Instant now) {
    if (now.isBefore(nextCleanup)) {
      return;
    }
    Iterator<Bucket> iterator = buckets.values().iterator();
    while (iterator.hasNext()) {
      Bucket bucket = iterator.next();
      if (!bucket.lastSeen.plus(idleTimeout).isAfter(now)) {
        iterator.remove();
      }
    }
    nextCleanup = now.plus(CLEANUP_INTERVAL);
  }

  private void evictOldestBucketAtCapacity() {
    if (buckets.size() < maxBuckets) {
      return;
    }
    Iterator<BucketKey> iterator = buckets.keySet().iterator();
    if (iterator.hasNext()) {
      iterator.next();
      iterator.remove();
    }
  }

  private RateLimitRule ruleFor(RequestRateLimitPolicy policy) {
    RateLimitRule rule = rules.get(policy);
    if (rule == null) {
      throw new IllegalStateException("No rate-limit rule configured for " + policy);
    }
    return rule;
  }

  record Decision(boolean allowed, long retryAfterSeconds) {
    static Decision permitted() {
      return new Decision(true, 0);
    }

    static Decision rejected(long retryAfterSeconds) {
      return new Decision(false, retryAfterSeconds);
    }
  }

  private record BucketKey(RequestRateLimitPolicy policy, String identity) {}

  private static final class Bucket {
    private double availableTokens;
    private Instant lastRefill;
    private Instant lastSeen;

    private Bucket(int capacity, Instant now) {
      this.availableTokens = capacity;
      this.lastRefill = now;
      this.lastSeen = now;
    }
  }
}
