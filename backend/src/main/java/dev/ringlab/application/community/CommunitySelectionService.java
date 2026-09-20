package dev.ringlab.application.community;

import dev.ringlab.application.gamedata.BaseStatsService;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.build.ranking.*;
import dev.ringlab.domain.gamedata.BaseStats;
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
  private final BuildRepository builds;
  private final VoteRepository votes;
  private final GameDataRepository game;
  private final UserRepository users;
  private final BaseStatsRepository stats;

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public CommunitySnapshot select() {
    var candidates = builds.searchCandidates(new BuildRepository.Filter(null, null, null, null, null));
    var summaries = votes.summaries(candidates.stream().map(BuildRanking.Candidate::id).toList());
    var ranked = candidates.stream().sorted(BuildRanking.comparator(BuildSort.BEST_RATED, summaries)).toList();
    var racers = index(game.listRacers(), r -> r.id());
    var machines = index(game.listMachines(), m -> m.id());
    var parts = index(game.listMachineParts(), p -> p.id());
    var gadgets = index(game.listGadgets(), g -> g.id());
    var versions = index(game.listGameVersions(), v -> v.id());
    var racerStats = new HashMap<UUID, Map<UUID, BaseStats>>();
    var partStats = new HashMap<UUID, Map<UUID, BaseStats>>();
    var names = new HashMap<UUID, String>();
    var selected = new ArrayList<CommunitySnapshot.Entry>();
    // Hydrate at most 50 candidates at a time, stopping as soon as three eligible builds are found.
    for (int offset = 0; offset < ranked.size() && selected.size() < 3; offset += 50) {
      var batch = ranked.subList(offset, Math.min(offset + 50, ranked.size()));
      var hydrated = index(builds.findAll(batch.stream().map(BuildRanking.Candidate::id).toList()), Build::id);
      for (var candidate : batch) {
        var b = hydrated.get(candidate.id());
        if (b == null || CommunityEligibility.controlledDemo(b.title())
            || !racers.containsKey(b.racerId())
            || b.gameVersionId() != null && !versions.containsKey(b.gameVersionId())
            || !CommunityEligibility.valid(b, parts, machines, gadgets)) continue;
        var name = names.computeIfAbsent(b.authorId(), id -> users.byId(id).orElseThrow().username());
        var rs = b.gameVersionId() == null ? Map.<UUID, BaseStats>of()
            : racerStats.computeIfAbsent(b.gameVersionId(), stats::racerStats);
        var ps = b.gameVersionId() == null ? Map.<UUID, BaseStats>of()
            : partStats.computeIfAbsent(b.gameVersionId(), stats::machinePartStats);
        Function<UUID, CommunitySnapshot.Part> part = id -> id == null ? null
            : new CommunitySnapshot.Part(parts.get(id), machines.get(parts.get(id).sourceMachineId()));
        selected.add(new CommunitySnapshot.Entry(b, name, racers.get(b.racerId()),
            part.apply(b.frontPartId()), part.apply(b.rearPartId()), part.apply(b.tirePartId()),
            b.gameVersionId() == null ? null : versions.get(b.gameVersionId()),
            b.gadgetIds().stream().map(gadgets::get).toList(),
            summaries.getOrDefault(b.id(), new VoteSummary(0, 0)),
            BaseStatsService.calculate(b.racerId(), b.frontPartId(), b.rearPartId(), b.tirePartId(), rs, ps),
            b.remixedFromBuildId() == null ? null : builds.find(b.remixedFromBuildId()).orElse(null)));
        if (selected.size() == 3) break;
      }
    }
    return new CommunitySnapshot(UUID.randomUUID(), Instant.now(), selected);
  }

  private static <T> Map<UUID, T> index(List<T> items, Function<T, UUID> id) {
    return items.stream().collect(Collectors.toMap(id, Function.identity()));
  }
}
