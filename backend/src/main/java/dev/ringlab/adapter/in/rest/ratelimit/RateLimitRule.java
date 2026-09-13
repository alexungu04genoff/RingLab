package dev.ringlab.adapter.in.rest.ratelimit;

import java.time.Duration;

record RateLimitRule(int capacity, Duration refillPeriod) {
  RateLimitRule {
    if (capacity <= 0) {
      throw new IllegalArgumentException("Rate-limit capacity must be positive");
    }
    if (refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()) {
      throw new IllegalArgumentException("Rate-limit refill period must be positive");
    }
  }

  static RateLimitRule parse(String configuredValue) {
    if (configuredValue == null) {
      throw new IllegalArgumentException("Rate-limit value is required");
    }
    String[] parts = configuredValue.trim().split("/", 2);
    if (parts.length != 2) {
      throw new IllegalArgumentException(
          "Rate-limit value must use the form <capacity>/<ISO-8601 duration>");
    }
    try {
      return new RateLimitRule(Integer.parseInt(parts[0]), Duration.parse(parts[1]));
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Rate-limit capacity must be an integer", exception);
    }
  }
}
