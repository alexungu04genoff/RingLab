package dev.ringlab.port.in;

import dev.ringlab.domain.build.Build;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface SavedBuildUseCase {
  record Item(Build build, Instant savedAt) {}
  record Page(List<Item> items, long total) {
    public Page { items = List.copyOf(items); }
  }

  Instant save(UUID actor, UUID buildId);
  void remove(UUID actor, UUID buildId);
  Set<UUID> status(UUID actor, Set<UUID> ids);
  Page list(UUID actor, String search, UUID version, UUID mapId, boolean includeAllMaps, int page, int size);
}
