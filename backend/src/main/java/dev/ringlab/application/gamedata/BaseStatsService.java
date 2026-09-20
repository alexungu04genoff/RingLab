package dev.ringlab.application.gamedata;

import dev.ringlab.application.NotFoundException;
import dev.ringlab.application.ValidationException;
import dev.ringlab.domain.gamedata.BaseStats;
import dev.ringlab.domain.gamedata.MachinePartType;
import dev.ringlab.domain.gamedata.MachineComposition;
import dev.ringlab.domain.gamedata.MachinePart;
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
  public record BuildStats(BaseStats total, BaseStats character, BaseStats machine) {}

  public Catalog catalog(UUID version) {
    requireVersion(version);
    Map<UUID, BaseStats> racers = stats.racerStats(version);
    Map<UUID, BaseStats> parts = stats.machinePartStats(version);
    Map<UUID, BaseStats> machines = new HashMap<>();
    var catalogParts = game.listMachineParts();
    for (var machine : game.listMachines()) {
      var contributions = catalogParts.stream().filter(p -> p.sourceMachineId().equals(machine.id()))
          .map(p -> parts.getOrDefault(p.id(), BaseStats.UNKNOWN)).toList();
      int expectedParts = machine.racingType() == null ? 0 : MachineComposition.requiredSlots(machine.racingType()).size();
      machines.put(machine.id(), contributions.size() == expectedParts
          ? BaseStats.sum(contributions) : BaseStats.UNKNOWN);
    }
    return new Catalog(version, Map.copyOf(racers), Map.copyOf(parts), Map.copyOf(machines));
  }

  /** No selected version means no calculation and no fallback to the latest patch. */
  public BaseStats build(UUID version, UUID racer, UUID front, UUID rear, UUID tire) {
    return buildBreakdown(version, racer, front, rear, tire).total();
  }

  public BuildStats buildBreakdown(UUID version, UUID racer, UUID front, UUID rear, UUID tire) {
    if (version != null) requireVersion(version);
    if (racer != null && game.findRacer(racer).isEmpty()) throw NotFoundException.missing("Racer");
    var frontPart = requirePart(front, MachinePartType.FRONT);
    var rearPart = requirePart(rear, MachinePartType.REAR);
    var tirePart = requirePart(tire, MachinePartType.TIRE);
    var machineType = MachineCompatibility.requireCompatible(game, frontPart, rearPart, tirePart);
    if (version == null) return new BuildStats(BaseStats.UNKNOWN, BaseStats.UNKNOWN, BaseStats.UNKNOWN);
    var racers = stats.racerStats(version);
    var parts = stats.machinePartStats(version);
    var character = value(racers, racer);
    var contributions = new java.util.ArrayList<BaseStats>();
    contributions.add(value(parts, front));
    contributions.add(value(parts, rear));
    if (machineType == null || MachineComposition.requiredSlots(machineType).contains(MachinePartType.TIRE)) contributions.add(value(parts, tire));
    var machine = BaseStats.sum(contributions);
    return new BuildStats(BaseStats.sum(List.of(character, machine)), character, machine);
  }

  private BaseStats value(Map<UUID, BaseStats> values, UUID id) {
    return id == null ? BaseStats.UNKNOWN : values.getOrDefault(id, BaseStats.UNKNOWN);
  }

  private void requireVersion(UUID version) {
    if (version == null) throw new ValidationException("Select a game version for stats");
    if (game.findGameVersion(version).isEmpty()) throw NotFoundException.missing("Game version");
  }

  private MachinePart requirePart(UUID id, MachinePartType type) {
    if (id == null) return null;
    var part = game.findMachinePart(id).orElseThrow(() -> NotFoundException.missing("Machine part"));
    if (part.type() != type) throw new ValidationException("Incorrect machine part type");
    return part;
  }

}
