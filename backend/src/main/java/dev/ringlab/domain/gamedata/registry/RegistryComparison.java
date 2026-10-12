package dev.ringlab.domain.gamedata.registry;

import java.util.List;

/** Review artifact, not an activation plan. The complete publication retains every unmapped distinction. */
public record RegistryComparison(VerifiedRegistryPublication registry, String databaseFingerprint,
    String projectionFingerprint, List<String> databaseVersions, List<String> changes,
    List<String> protectedChanges, List<String> incompatibleMappings, List<Observation> observations,
    int proposedAdditions, int proposedPresentationUpdates) {
  public record Observation(String path, String kind, String detail) {}
  public RegistryComparison {
    databaseVersions = List.copyOf(databaseVersions); changes = List.copyOf(changes);
    protectedChanges = List.copyOf(protectedChanges); incompatibleMappings = List.copyOf(incompatibleMappings);
    observations = List.copyOf(observations);
  }
  public boolean projectionCompatible() { return protectedChanges.isEmpty() && incompatibleMappings.isEmpty(); }
  public String assessment() {
    return projectionCompatible()
        ? "PROJECTION_COMPATIBLE_REQUIRES_RETENTION_DESIGN: represented facts pass current importer policy; provenance and gap retention must be designed before activation."
        : "BLOCKED: protected differences or incompatible mappings prevent future activation under current policy.";
  }
}
