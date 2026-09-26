package dev.ringlab.port.out;

import java.time.Instant;
import java.util.*;

public interface SavedBuildRepository {
  record Bookmark(UUID buildId, Instant savedAt) {}
  record Page(List<Bookmark> items, long total) {
    public Page { items = List.copyOf(items); }
  }
  Instant save(UUID userId, UUID buildId);
  void remove(UUID userId, UUID buildId);
  Set<UUID> status(UUID userId, Set<UUID> buildIds);
  Page list(UUID userId, String search, UUID versionId, int page, int size);
}
