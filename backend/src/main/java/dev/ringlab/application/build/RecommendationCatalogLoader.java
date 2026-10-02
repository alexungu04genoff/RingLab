package dev.ringlab.application.build;

import dev.ringlab.application.ValidationException;
import dev.ringlab.domain.build.recommendation.RecommendationCatalog;
import dev.ringlab.domain.gamedata.*;
import dev.ringlab.port.out.BaseStatsRepository;
import dev.ringlab.port.out.GameDataRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/** The runtime catalog is read-only. All facts are detached before this short transaction ends. */
@ApplicationScoped
@RequiredArgsConstructor
public class RecommendationCatalogLoader {
  private final GameDataRepository game;
  private final BaseStatsRepository stats;
  private final dev.ringlab.application.gamedata.ReviewedGadgetRules reviewed;

  @Transactional(Transactional.TxType.REQUIRES_NEW)
  public RecommendationCatalog load(UUID versionId) {
    var version = game.findGameVersion(versionId)
        .orElseThrow(() -> new ValidationException("Unknown game version / patch ID"));
    return new RecommendationCatalog(version, index(game.listRacers(), Racer::id),
        index(game.listMachines(), Machine::id), index(game.listMachineParts(), MachinePart::id),
        index(game.listGadgets(), Gadget::id), stats.racerStats(versionId), stats.machinePartStats(versionId), reviewed.snapshot());
  }

  private static <T> Map<UUID, T> index(List<T> values, Function<T, UUID> key) {
    return values.stream().collect(Collectors.toMap(key, Function.identity()));
  }
}
