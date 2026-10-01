package dev.ringlab.application.gamedata;

import dev.ringlab.port.in.ScenarioStatsUseCase;

import dev.ringlab.application.NotFoundException;
import dev.ringlab.application.ValidationException;
import dev.ringlab.domain.build.GadgetPlate;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.port.out.GameDataRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/** Read-only catalog resolution. No build, ownership or recommendation mutation dependencies. */
@ApplicationScoped
@RequiredArgsConstructor
public class ScenarioStatsService implements ScenarioStatsUseCase {
  private final BaseStatsService base;
  private final GameDataRepository game;

  public Rules rules() {
    return new Rules(ScenarioGadgetRules.VERSION, ScenarioGadgetRules.all());
  }

  public ScenarioStatsResult preview(UUID version, UUID racer, UUID front, UUID rear, UUID tire,
      List<UUID> ids, ScenarioContext context) {
    if (context == null) throw new ValidationException("Specify a scenario (empty conditions are allowed)");
    if (ids == null || ids.size() > 6 || ids.stream().anyMatch(Objects::isNull)
        || new HashSet<>(ids).size() != ids.size())
      throw new ValidationException("Select distinct gadget IDs (at most six)");
    // One gadget catalog read regardless of the selected count; reuse the passive domain pipeline.
    var catalog = index(game.listGadgets(), Gadget::id);
    var gadgets = ids.stream().map(id -> {
      var gadget = catalog.get(id);
      if (gadget == null) throw NotFoundException.missing("Gadget");
      return gadget;
    }).toList();
    if (!GadgetPlate.canFit(gadgets.stream().map(Gadget::slotCost).toList()))
      throw new ValidationException("Selected gadgets do not fit the Gadget Plate");
    var breakdown = base.draftBreakdown(version, racer, front, rear, tire);
    var parts = index(game.listMachineParts(), MachinePart::id);
    var machines = index(game.listMachines(), Machine::id);
    var passive = PassiveStatsService.resolved(breakdown,
        version == null ? null : game.findGameVersion(version).orElseThrow(() -> NotFoundException.missing("Game version")),
        racer == null ? null : game.findRacer(racer).orElseThrow(() -> NotFoundException.missing("Racer")),
        parts.get(front), parts.get(rear), parts.get(tire), machines, gadgets, true);
    return ScenarioStatsCalculator.calculate(passive, context);
  }

  private static <T> Map<UUID,T> index(List<T> values, Function<T,UUID> key) {
    return values.stream().collect(Collectors.toMap(key, Function.identity()));
  }
}
