package dev.ringlab.application.gamedata;

import dev.ringlab.application.NotFoundException;
import dev.ringlab.application.ValidationException;
import dev.ringlab.domain.gamedata.BaseStats;
import dev.ringlab.domain.gamedata.MachinePartType;
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
      var contributions = catalogParts.stream().filter(p -> p.sourceMachineId().equals(machine.id()))
          .map(p -> parts.getOrDefault(p.id(), BaseStats.UNKNOWN)).toList();
      machines.put(machine.id(), contributions.size() == 3 ? BaseStats.sum(contributions) : BaseStats.UNKNOWN);
    }
    return new Catalog(version, Map.copyOf(racers), Map.copyOf(parts), Map.copyOf(machines));
  }

  /** No selected version means no calculation and no fallback to the latest patch. */
  public BaseStats build(UUID version, UUID racer, UUID front, UUID rear, UUID tire) {
    if (version == null) return BaseStats.UNKNOWN;
    requireVersion(version);
    if (racer != null && game.findRacer(racer).isEmpty()) throw NotFoundException.missing("Racer");
    requirePart(front, MachinePartType.FRONT);
    requirePart(rear, MachinePartType.REAR);
    requirePart(tire, MachinePartType.TIRE);
    var racers = stats.racerStats(version);
    var parts = stats.machinePartStats(version);
    return BaseStats.sum(List.of(value(racers, racer), value(parts, front), value(parts, rear), value(parts, tire)));
  }

  private BaseStats value(Map<UUID, BaseStats> values, UUID id) {
    return id == null ? BaseStats.UNKNOWN : values.getOrDefault(id, BaseStats.UNKNOWN);
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
