package dev.ringlab.application.gamedata;

import dev.ringlab.domain.gamedata.importing.ImportValidationException;
import dev.ringlab.domain.gamedata.registry.RegistryComparison;
import dev.ringlab.port.in.CompareRegistryUseCase;
import dev.ringlab.port.out.*;
import java.util.*;

/** No startup hook and no mutation capability. PostgreSQL is read only after successful remote verification. */
public final class RegistryComparisonService implements CompareRegistryUseCase {
  private final RegistryPublicationSource registry;
  private final GameDataSnapshotReader database;
  public RegistryComparisonService(RegistryPublicationSource registry, GameDataSnapshotReader database) {
    this.registry = registry; this.database = database;
  }
  @Override public Optional<RegistryComparison> compareLatest() {
    return registry.latest().map(verified -> {
      var projection = new RegistryProjection().project(verified.publication());
      var incompatible = new ArrayList<>(projection.incompatible());
      try { new GameDataSetValidator().validate(projection.data()); }
      catch (ImportValidationException e) { incompatible.addAll(e.problems()); }
      var current = database.read();
      // Shape is already valid. Retain planner differences even when a stricter RingLab mapping policy rejects a rule.
      var plan = new GameDataImportPlanner().plan(current, projection.data());
      return new RegistryComparison(verified, CatalogFingerprint.snapshot(current), CatalogFingerprint.snapshot(projection.data()),
          current.versions().stream().map(v -> v.value().version()).sorted().toList(), plan.changes(),
          plan.unsafeChanges(), incompatible, projection.observations(), plan.inserts(), plan.updateCount());
    });
  }
}
