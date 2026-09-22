package dev.ringlab.application.community;

import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.build.ranking.*;
import dev.ringlab.domain.vote.VoteSummary;
import dev.ringlab.port.out.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class CommunitySelectionService {
  private static final int SELECTION_SIZE = 3;
  private static final int HYDRATION_BATCH_SIZE = 50;
  private final BuildRepository builds;
  private final VoteRepository votes;
  private final GameDataRepository game;
  private final UserRepository users;
  private final BaseStatsRepository stats;

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public CommunitySnapshot select() {
    var candidates = builds.searchCandidates(new BuildRepository.Filter(null, null, null, null, null));
    var summaries = votes.summaries(candidates.stream().map(BuildRanking.Candidate::id).toList());
    var versions = index(game.listGameVersions(), v -> v.id());
    var releaseDates = versions.values().stream().collect(Collectors.toMap(
        v -> v.id(), v -> v.releasedAt()));
    var ranked = candidates.stream().sorted(
        BuildRanking.comparator(BuildSort.BEST_RATED, summaries, releaseDates)).toList();
    var entries = new CommunitySnapshotAssembler(builds, users, stats, game, versions);
    var selected = new ArrayList<CommunitySnapshot.Entry>();
    // Hydrate at most 50 candidates at a time, stopping as soon as three eligible builds are found.
    for (int offset = 0; offset < ranked.size() && selected.size() < SELECTION_SIZE; offset += HYDRATION_BATCH_SIZE) {
      var batch = ranked.subList(offset, Math.min(offset + HYDRATION_BATCH_SIZE, ranked.size()));
      var hydrated = index(builds.findAll(batch.stream().map(BuildRanking.Candidate::id).toList()), Build::id);
      for (var candidate : batch) {
        var build = hydrated.get(candidate.id());
        if (!entries.eligible(build)) continue;
        selected.add(entries.assemble(build, summaries.getOrDefault(build.id(), new VoteSummary(0, 0))));
        if (selected.size() == SELECTION_SIZE) break;
      }
    }
    return new CommunitySnapshot(UUID.randomUUID(), Instant.now(), selected);
  }

  private static <T> Map<UUID, T> index(List<T> items, Function<T, UUID> id) {
    return items.stream().collect(Collectors.toMap(id, Function.identity()));
  }
}
