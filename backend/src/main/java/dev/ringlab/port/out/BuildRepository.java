package dev.ringlab.port.out;

import dev.ringlab.domain.build.Build;
import java.util.*;

public interface BuildRepository {
  /** Optional predicates combine with AND. Null predicates and blank search are ignored.
   * Search is a case-insensitive literal substring of title (including literal % and _).
   * machineId identifies a source machine: ANY selected front/rear/tire part may match.
   * Racer, author and game version match their IDs exactly.
   */
  record Filter(
      String search,
      UUID racerId,
      UUID machineId,
      UUID authorId,
      UUID gameVersionId) {}

  Optional<Build> find(UUID id);

  /** All matching builds, each once, with no ordering or pagination guarantee.
   * Ranking and pagination belong to the application, not this adapter.
   */
  List<Build> search(Filter filter);

  void save(Build build);

  /** Deletes an existing build within the caller's transaction. On successful commit,
   * its votes, comments and ordered gadget relations are gone. Surviving remixes retain
   * their contents and have remixedFromBuildId cleared. These effects must be atomic.
   * PostgreSQL implements this contract with cascades and ON DELETE SET NULL;
   * other adapters must preserve the same postconditions.
   */
  void delete(UUID id);
}
