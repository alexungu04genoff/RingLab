package dev.ringlab.application.community;

import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.domain.vote.VoteSummary;
import dev.ringlab.port.out.BaseStatsRepository;
import dev.ringlab.port.out.BuildRepository;
import dev.ringlab.port.out.GameDataRepository;
import dev.ringlab.port.out.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Resolves eligible snapshot entries with lookups reused only within one selection. */
final class CommunitySnapshotAssembler {
  private final BuildRepository builds;
  private final UserRepository users;
  private final BaseStatsRepository stats;
  private final Map<UUID, GameVersion> versions;
  private final Map<UUID, Racer> racers;
  private final Map<UUID, Machine> machines;
  private final Map<UUID, MachinePart> parts;
  private final Map<UUID, Gadget> gadgets;
  private final Map<UUID, Map<UUID, BaseStats>> racerStats = new HashMap<>();
  private final Map<UUID, Map<UUID, BaseStats>> partStats = new HashMap<>();
  private final Map<UUID, String> authorNames = new HashMap<>();

  CommunitySnapshotAssembler(BuildRepository builds, UserRepository users, BaseStatsRepository stats,
      GameDataRepository game, Map<UUID, GameVersion> versions) {
    this.builds = builds;
    this.users = users;
    this.stats = stats;
    this.versions = versions;
    racers = index(game.listRacers(), Racer::id);
    machines = index(game.listMachines(), Machine::id);
    parts = index(game.listMachineParts(), MachinePart::id);
    gadgets = index(game.listGadgets(), Gadget::id);
  }

  boolean eligible(Build build) {
    return build != null && !CommunityEligibility.controlledDemo(build.title())
        && racers.containsKey(build.racerId())
        && (build.gameVersionId() == null || versions.containsKey(build.gameVersionId()))
        && CommunityEligibility.valid(build, parts, machines, gadgets);
  }

  CommunitySnapshot.Entry assemble(Build build, VoteSummary summary) {
    var authorName = authorNames.computeIfAbsent(build.authorId(), id -> users.byId(id).orElseThrow().username());
    var versionRacers = build.gameVersionId() == null ? Map.<UUID, BaseStats>of()
        : racerStats.computeIfAbsent(build.gameVersionId(), stats::racerStats);
    var versionParts = build.gameVersionId() == null ? Map.<UUID, BaseStats>of()
        : partStats.computeIfAbsent(build.gameVersionId(), stats::machinePartStats);
    return new CommunitySnapshot.Entry(build, authorName, racers.get(build.racerId()),
        part(build.frontPartId()), part(build.rearPartId()), part(build.tirePartId()),
        build.gameVersionId() == null ? null : versions.get(build.gameVersionId()),
        build.gadgetIds().stream().map(gadgets::get).toList(), summary,
        BaseStatsBreakdown.calculate(build.racerId(), build.frontPartId(), build.rearPartId(),
            build.tirePartId(), versionRacers, versionParts),
        build.remixedFromBuildId() == null ? null : builds.find(build.remixedFromBuildId()).orElse(null));
  }

  private CommunitySnapshot.Part part(UUID id) {
    if (id == null) return null;
    var part = parts.get(id);
    return new CommunitySnapshot.Part(part, machines.get(part.sourceMachineId()));
  }

  private static <T> Map<UUID, T> index(List<T> items, Function<T, UUID> id) {
    return items.stream().collect(Collectors.toMap(id, Function.identity()));
  }
}
