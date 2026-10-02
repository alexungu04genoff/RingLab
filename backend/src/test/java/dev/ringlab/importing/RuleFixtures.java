package dev.ringlab.importing;

import dev.ringlab.adapter.in.catalog.GameDataCsvReader;
import dev.ringlab.application.gamedata.ReviewedGadgetRules;
import dev.ringlab.domain.gamedata.GadgetRuleSnapshot;
import dev.ringlab.domain.gamedata.importing.GameDataSet;
import java.nio.file.Path;
import java.util.Optional;

/** Unit-test facts come from the canonical CSVs; the frozen oracle remains independent. */
public final class RuleFixtures {
  public static final GameDataSet DATA = new GameDataCsvReader().read(Path.of("../game-data"));
  private static final GadgetRuleSnapshot SNAPSHOT = DATA.ruleSets().getFirst().snapshot(DATA.versions().stream()
      .map(r -> r.value()).filter(v -> v.id().equals(DATA.ruleSets().getFirst().versionId())).findFirst().orElseThrow());
  private RuleFixtures() {}
  public static GadgetRuleSnapshot snapshot() { return SNAPSHOT; }
  public static ReviewedGadgetRules loader() {
    return new ReviewedGadgetRules(version -> version.equals(SNAPSHOT.version().version()) ? Optional.of(SNAPSHOT) : Optional.empty());
  }
}
