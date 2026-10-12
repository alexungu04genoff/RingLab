package dev.ringlab.domain.gamedata.registry;

/** Result of complete contract, checksum, metadata and fingerprint verification at the HTTP boundary. */
public record VerifiedRegistryPublication(Publication publication, String datasetFingerprint) {}
