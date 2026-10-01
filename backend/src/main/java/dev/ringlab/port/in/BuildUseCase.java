package dev.ringlab.port.in;

import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.build.ranking.BuildSort;
import dev.ringlab.domain.vote.VoteSummary;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface BuildUseCase {
  record Filter(String search, UUID racerId, UUID machineId, UUID authorId, UUID gameVersionId,
                Set<UUID> excludedIds, UUID mapId, boolean includeAllMaps) {
    public Filter(String search, UUID racerId, UUID machineId, UUID authorId, UUID gameVersionId) {
      this(search, racerId, machineId, authorId, gameVersionId, Set.of(), null, true);
    }
    public Filter(String search, UUID racerId, UUID machineId, UUID authorId, UUID gameVersionId,
        Set<UUID> excludedIds) {
      this(search, racerId, machineId, authorId, gameVersionId, excludedIds, null, true);
    }
    public Filter { excludedIds = excludedIds == null ? Set.of() : Set.copyOf(excludedIds); }
  }

  record Query(Filter filter, BuildSort sort, int page, int size) {}

  record Page(List<Build> items, long total, Map<UUID, VoteSummary> summaries) {
    public Page { items = List.copyOf(items); summaries = Map.copyOf(summaries); }
    public Page(List<Build> items, long total) { this(items, total, Map.of()); }
    public VoteSummary summary(UUID buildId) { return summaries.getOrDefault(buildId, new VoteSummary(0, 0)); }
  }

  record Draft(String title, String description, UUID racerId, UUID frontPartId,
               UUID rearPartId, UUID tirePartId, UUID gameVersionId, UUID remixedFromBuildId,
               List<UUID> gadgetIds, List<UUID> recommendedMapIds) {
    /** Null map selection means unspecified: default on create, preserve on edit. */
    public Draft(String title, String description, UUID racerId, UUID frontPartId,
        UUID rearPartId, UUID tirePartId, UUID gameVersionId, UUID remixedFromBuildId,
        List<UUID> gadgetIds) {
      this(title, description, racerId, frontPartId, rearPartId, tirePartId, gameVersionId,
          remixedFromBuildId, gadgetIds, null);
    }
  }

  Build get(UUID id);
  Optional<Build> remixSource(Build build);
  Page list(Query query);
  Build create(UUID author, Draft draft);
  Build edit(UUID id, UUID actor, Draft draft);
  void delete(UUID id, UUID actor);
}
