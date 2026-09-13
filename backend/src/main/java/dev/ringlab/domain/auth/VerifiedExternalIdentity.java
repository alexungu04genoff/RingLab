package dev.ringlab.domain.auth;

public record VerifiedExternalIdentity(String provider, String subject, String email, String displayName) {}
