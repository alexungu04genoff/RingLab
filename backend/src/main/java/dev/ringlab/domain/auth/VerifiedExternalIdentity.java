package dev.ringlab.domain.auth;

/** Provider facts returned only after successful cryptographic verification. */
public record VerifiedExternalIdentity(String provider, String subject, String email, String displayName) {}
