package dev.ringlab.port.out;

import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.build.ranking.BuildRanking;
import java.util.*;

public interface BuildRepository {
  /**
   * Optional candidate filters. Null IDs and blank search are ignored; non-blank search is a
   * literal, case-insensitive substring across the title, racer name, selected machine source names,
   * and selected gadget names. Racer, author, and game version IDs match exactly; machine ID matches
   * any selected front, rear, or tire part sourced from that machine.
   */
  record Filter(
      String search,
      UUID racerId,
      UUID machineId,
      UUID authorId,
      UUID gameVersionId,
      Set<UUID> excludedIds) {
    public Filter(String search, UUID racerId, UUID machineId, UUID authorId, UUID gameVersionId) {
      this(search, racerId, machineId, authorId, gameVersionId, Set.of());
    }

    public Filter {
      excludedIds = excludedIds == null ? Set.of() : Set.copyOf(excludedIds);
    }
  }

  Optional<Build> find(UUID id);

  /**
   * Returns every matching candidate with lightweight ranking facts only. It provides no ranking
   * or pagination guarantee.
   */
  List<BuildRanking.Candidate> searchCandidates(Filter filter);

  /** Returns fully hydrated builds for the requested IDs; returned order is unspecified. */
  List<Build> findAll(Collection<UUID> ids);

  void save(Build build);

  /**
   * Atomically deletes the build, its votes, comments, and gadget relations. Remixes survive with
   * null provenance.
   */
  void delete(UUID id);
}
