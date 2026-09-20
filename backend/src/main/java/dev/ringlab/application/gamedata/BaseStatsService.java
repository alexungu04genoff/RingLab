package dev.ringlab.application.gamedata;

import dev.ringlab.application.NotFoundException;
import dev.ringlab.application.ValidationException;
import dev.ringlab.domain.gamedata.BaseStats;
import dev.ringlab.domain.gamedata.MachinePartType;
import dev.ringlab.domain.gamedata.MachineFamily;
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
      int expectedParts = machine.family() == MachineFamily.BOARD ? 2 : 3;
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
    if (version == null) return new BuildStats(BaseStats.UNKNOWN, BaseStats.UNKNOWN, BaseStats.UNKNOWN);
    requireVersion(version);
    if (racer != null && game.findRacer(racer).isEmpty()) throw NotFoundException.missing("Racer");
    var frontPart = requirePart(front, MachinePartType.FRONT);
    var rearPart = requirePart(rear, MachinePartType.REAR);
    var tirePart = requirePart(tire, MachinePartType.TIRE);
    var family = requireCompatibleFamilies(frontPart, rearPart, tirePart);
    if (family == MachineFamily.BOARD && tirePart != null) {
      throw new ValidationException("Board builds do not use a tire part");
    }
    var racers = stats.racerStats(version);
    var parts = stats.machinePartStats(version);
    var character = value(racers, racer);
    var contributions = new java.util.ArrayList<BaseStats>();
    contributions.add(value(parts, front));
    contributions.add(value(parts, rear));
    if (family != MachineFamily.BOARD) contributions.add(value(parts, tire));
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

  private MachineFamily requireCompatibleFamilies(MachinePart... parts) {
    MachineFamily family = null;
    for (var part : parts) {
      if (part == null) continue;
      var partFamily = game.findMachine(part.sourceMachineId())
          .orElseThrow(() -> NotFoundException.missing("Source machine")).family();
      if (family != null && family != partFamily) {
        throw new ValidationException("All machine parts must belong to the same machine family");
      }
      family = partFamily;
    }
    return family;
  }
}
