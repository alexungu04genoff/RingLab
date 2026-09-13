package dev.ringlab.adapter.in.rest.ratelimit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.eclipse.microprofile.config.Config;

@ApplicationScoped
class RateLimitSettings {
  private final Map<RequestRateLimitPolicy, RateLimitRule> rules;
  private final Duration bucketIdleTimeout;
  private final int maxBuckets;
  private final boolean trustCloudflareClientIp;

  @Inject
  RateLimitSettings(Config config) {
    var configuredRules =
        new EnumMap<RequestRateLimitPolicy, RateLimitRule>(RequestRateLimitPolicy.class);
    configuredRules.put(RequestRateLimitPolicy.GENERAL_READ, rule(config, "general"));
    configuredRules.put(RequestRateLimitPolicy.BUILD_LIST, rule(config, "build-list"));
    configuredRules.put(RequestRateLimitPolicy.NEWS, rule(config, "news"));
    configuredRules.put(RequestRateLimitPolicy.LOGIN, rule(config, "login"));
    configuredRules.put(RequestRateLimitPolicy.GOOGLE_LOGIN, rule(config, "google-login"));
    configuredRules.put(RequestRateLimitPolicy.REGISTRATION, rule(config, "registration"));
    configuredRules.put(RequestRateLimitPolicy.BUILD_CREATION, rule(config, "build-creation"));
    configuredRules.put(RequestRateLimitPolicy.COMMENT_CREATION, rule(config, "comment-creation"));
    configuredRules.put(RequestRateLimitPolicy.VOTING, rule(config, "voting"));
    configuredRules.put(
        RequestRateLimitPolicy.AUTHENTICATED_MUTATION,
        rule(config, "authenticated-mutation"));
    rules = Map.copyOf(configuredRules);
    bucketIdleTimeout = config.getValue("ringlab.rate-limit.bucket-idle-timeout", Duration.class);
    maxBuckets = config.getValue("ringlab.rate-limit.max-buckets", Integer.class);
    trustCloudflareClientIp =
        config.getValue("ringlab.rate-limit.trust-cloudflare-client-ip", Boolean.class);
  }

  Map<RequestRateLimitPolicy, RateLimitRule> rules() {
    return rules;
  }

  Duration bucketIdleTimeout() {
    return bucketIdleTimeout;
  }

  int maxBuckets() {
    return maxBuckets;
  }

  boolean trustCloudflareClientIp() {
    return trustCloudflareClientIp;
  }

  private static RateLimitRule rule(Config config, String policyName) {
    return RateLimitRule.parse(config.getValue("ringlab.rate-limit." + policyName, String.class));
  }
}
