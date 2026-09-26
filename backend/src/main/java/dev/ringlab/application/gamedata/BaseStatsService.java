package dev.ringlab.application.gamedata;

import dev.ringlab.application.NotFoundException;
import dev.ringlab.application.ValidationException;
import dev.ringlab.domain.gamedata.BaseStats;
import dev.ringlab.domain.gamedata.BaseStatsBreakdown;
import dev.ringlab.domain.gamedata.Machine;
import dev.ringlab.domain.gamedata.MachinePart;
import dev.ringlab.domain.gamedata.MachinePartType;
import dev.ringlab.domain.gamedata.MachineComposition;
import dev.ringlab.domain.build.Build;
import dev.ringlab.port.out.BaseStatsRepository;
import dev.ringlab.port.out.GameDataRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class BaseStatsService {
  private final BaseStatsRepository stats;
  private final GameDataRepository game;

  public record Catalog(UUID gameVersionId, Map<UUID, BaseStats> racers,
                        Map<UUID, BaseStats> machineParts, Map<UUID, BaseStats> machines) {}

  public Catalog catalog(UUID version) {
    requireVersion(version);
    Map<UUID, BaseStats> racers = stats.racerStats(version);
    Map<UUID, BaseStats> parts = stats.machinePartStats(version);
    Map<UUID, BaseStats> machines = new HashMap<>();
    var catalogParts = game.listMachineParts();
    for (var machine : game.listMachines()) {
      machines.put(machine.id(), stockMachineStats(machine, catalogParts, parts));
    }
    return new Catalog(version, Map.copyOf(racers), Map.copyOf(parts), Map.copyOf(machines));
  }

  private BaseStats stockMachineStats(
      Machine machine, List<MachinePart> catalogParts, Map<UUID, BaseStats> parts) {
    var contributions = catalogParts.stream().filter(part -> part.sourceMachineId().equals(machine.id()))
        .map(part -> parts.getOrDefault(part.id(), BaseStats.UNKNOWN)).toList();
    int expectedParts = machine.racingType() == null
        ? 0 : MachineComposition.requiredSlots(machine.racingType()).size();
    return contributions.size() == expectedParts ? BaseStats.sum(contributions) : BaseStats.UNKNOWN;
  }

  /** No selected version means no calculation and no fallback to the latest patch. */
  public BaseStats build(UUID version, UUID racer, UUID front, UUID rear, UUID tire) {
    return buildBreakdown(version, racer, front, rear, tire).total();
  }

  public BaseStatsBreakdown buildBreakdown(UUID version, UUID racer, UUID front, UUID rear, UUID tire) {
    if (version != null) requireVersion(version);
    if (racer != null && game.findRacer(racer).isEmpty()) throw NotFoundException.missing("Racer");
    requirePart(front, MachinePartType.FRONT);
    requirePart(rear, MachinePartType.REAR);
    requirePart(tire, MachinePartType.TIRE);
    if (version == null) return BaseStatsBreakdown.UNKNOWN;
    var racers = stats.racerStats(version);
    var parts = stats.machinePartStats(version);
    return BaseStatsBreakdown.calculate(racer, front, rear, tire, racers, parts);
  }

  /** Enrich only an already selected page of persisted builds. Its references are held
   * by the builds and constrained by foreign keys; do not look them up again per card. */
  public Map<UUID, BaseStatsBreakdown> buildPage(List<Build> builds) {
    Map<UUID, Map<UUID, BaseStats>> racersByVersion = new HashMap<>();
    Map<UUID, Map<UUID, BaseStats>> partsByVersion = new HashMap<>();
    Map<UUID, BaseStatsBreakdown> result = new HashMap<>();
    for (var build : builds) {
      UUID version = build.gameVersionId();
      if (version == null) {
        result.put(build.id(), BaseStatsBreakdown.UNKNOWN);
        continue;
      }
      var racers = racersByVersion.computeIfAbsent(version, stats::racerStats);
      var parts = partsByVersion.computeIfAbsent(version, stats::machinePartStats);
      result.put(build.id(), BaseStatsBreakdown.calculate(build.racerId(), build.frontPartId(),
          build.rearPartId(), build.tirePartId(), racers, parts));
    }
    return Map.copyOf(result);
  }

  private void requireVersion(UUID version) {
    if (version == null) throw new ValidationException("Select a game version for stats");
    if (game.findGameVersion(version).isEmpty()) throw NotFoundException.missing("Game version");
  }

  private void requirePart(UUID id, MachinePartType type) {
    if (id == null) return;
    var part = game.findMachinePart(id).orElseThrow(() -> NotFoundException.missing("Machine part"));
    if (part.type() != type) throw new ValidationException("Incorrect machine part type");
  }

}
