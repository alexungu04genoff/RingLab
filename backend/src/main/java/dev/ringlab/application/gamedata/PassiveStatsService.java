package dev.ringlab.application.gamedata;

import dev.ringlab.application.NotFoundException;
import dev.ringlab.application.ValidationException;
import dev.ringlab.domain.build.Build;
import dev.ringlab.domain.build.GadgetPlate;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.port.out.GameDataRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class PassiveStatsService {
  private final BaseStatsService base;
  private final GameDataRepository game;

  public PassiveStatsResult draft(UUID version, UUID racer, UUID front, UUID rear, UUID tire, List<UUID> ids) {
    if (ids == null || ids.size() > 6 || ids.stream().anyMatch(Objects::isNull)
        || new HashSet<>(ids).size() != ids.size()) throw new ValidationException("Select distinct gadget IDs (at most six)");
    var gadgets = ids.stream().map(id -> game.findGadget(id).orElseThrow(() -> NotFoundException.missing("Gadget"))).toList();
    if (!GadgetPlate.canFit(gadgets.stream().map(Gadget::slotCost).toList()))
      throw new ValidationException("Selected gadgets do not fit the Gadget Plate");
    var breakdown = base.buildBreakdown(version,racer,front,rear,tire);
    var parts = index(game.listMachineParts(),MachinePart::id);
    var machines = index(game.listMachines(),Machine::id);
    return resolved(breakdown,version == null ? null : game.findGameVersion(version).orElseThrow(),
        racer == null ? null : game.findRacer(racer).orElseThrow(),
        parts.get(front),parts.get(rear),parts.get(tire),machines,gadgets,true);
  }

  /** One catalog load per page; base contributions remain batched once per version. */
  public Map<UUID, PassiveStatsResult> buildPage(List<Build> builds) {
    if (builds.isEmpty()) return Map.of();
    var bases = base.buildPage(builds);
    var racers = index(game.listRacers(),Racer::id);
    var machines = index(game.listMachines(),Machine::id);
    var parts = index(game.listMachineParts(),MachinePart::id);
    var gadgets = index(game.listGadgets(),Gadget::id);
    var versions = index(game.listGameVersions(),GameVersion::id);
    var result = new HashMap<UUID, PassiveStatsResult>();
    for (var build : builds) {
      var selected = build.gadgetIds().stream().map(gadgets::get).filter(Objects::nonNull).toList();
      boolean valid = selected.size() == build.gadgetIds().size()
          && new HashSet<>(build.gadgetIds()).size() == build.gadgetIds().size()
          && GadgetPlate.canFit(selected.stream().map(Gadget::slotCost).toList());
      result.put(build.id(),resolved(bases.get(build.id()),versions.get(build.gameVersionId()),racers.get(build.racerId()),
          parts.get(build.frontPartId()),parts.get(build.rearPartId()),parts.get(build.tirePartId()),machines,selected,valid));
    }
    return Map.copyOf(result);
  }

  /** Reused by snapshot exports with already resolved catalog metadata. */
  public static PassiveStatsResult resolved(BaseStatsBreakdown base, GameVersion patch, Racer racer,
      MachinePart front, MachinePart rear, MachinePart tire, Map<UUID, Machine> machines,
      List<Gadget> gadgets, boolean validGadgets) {
    boolean coherent = true;
    RacingType machineType = null;
    for (var part : Arrays.asList(front,rear,tire)) {
      if (part == null) continue;
      var source = machines.get(part.sourceMachineId());
      var type = source == null ? null : source.racingType();
      if (type == null || (machineType != null && machineType != type)) coherent = false;
      if (type != null) machineType = type;
    }
    if ((front != null && front.type() != MachinePartType.FRONT)
        || (rear != null && rear.type() != MachinePartType.REAR)
        || (tire != null && tire.type() != MachinePartType.TIRE)
        || (machineType == RacingType.BOOST && tire != null)) coherent = false;
    boolean complete = front != null && rear != null && machineType != null
        && (machineType == RacingType.BOOST || tire != null);
    return PassiveStatsCalculator.calculate(base,patch == null ? null : patch.version(),
        racer == null ? null : racer.racingType(),coherent && complete ? machineType : null,
        gadgets,validGadgets && coherent);
  }

  private static <T> Map<UUID,T> index(List<T> values, Function<T,UUID> key) {
    return values.stream().collect(Collectors.toMap(key,Function.identity()));
  }
}
